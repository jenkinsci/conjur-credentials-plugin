package org.conjur.jenkins.disco.discovery;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.google.gson.Gson;
import hudson.model.Job;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import okhttp3.OkHttpClient;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.conjur.jenkins.configuration.ConjurJITJobProperty;
import org.conjur.jenkins.configuration.FolderConjurConfiguration;
import org.conjur.jenkins.disco.config.DiscoExporterConfiguration;
import org.conjur.jenkins.disco.export.DiscoExportClient;
import org.conjur.jenkins.disco.model.*;
import org.conjur.jenkins.disco.security.CyberArkIdentityClient;
import org.conjur.jenkins.disco.security.EncryptionService;
import org.conjur.jenkins.jwtauth.JwtAuthenticationService;
import org.conjur.jenkins.jwtauth.impl.JwtToken;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;

import org.conjur.jenkins.api.ConjurAPIUtils;
import org.conjur.jenkins.configuration.TelemetryConfiguration;
import org.conjur.jenkins.disco.DiscoCode;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.conjur.jenkins.disco.DiscoCode.*;

/**
 * Singleton orchestrator that runs the full DisCo discovery pipeline.
 *
 * Thread-safety: a volatile isRunning flag acts as a concurrency guard.
 * Only one run can be active at a time regardless of trigger source.
 */
public class DiscoveryOrchestrator {

    public enum TriggerType { MANUAL, CRON }

    private static final Logger LOGGER = Logger.getLogger(DiscoveryOrchestrator.class.getName());

    private static final DiscoveryOrchestrator INSTANCE = new DiscoveryOrchestrator();

    private volatile boolean isRunning = false;
    private final DiscoveryRunResult currentResult = new DiscoveryRunResult();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "disco-orchestrator");
        t.setDaemon(true);
        return t;
    });

    private DiscoveryOrchestrator() {}

    /** Protected constructor for unit test subclassing only. */
    protected DiscoveryOrchestrator(boolean testMode) {}

    public static DiscoveryOrchestrator getInstance() {
        return INSTANCE;
    }

    public DiscoveryRunResult getCurrentResult() {
        return currentResult;
    }

    // -------------------------------------------------------------------------

    /** Schedules an async run and returns immediately. */
    public void runAsync(TriggerType triggerType) {
        executor.submit(() -> run(triggerType));
    }

    /** Executes the full discovery pipeline synchronously. */
    public void run(TriggerType triggerType) {
        if (isRunning) {
            LOGGER.warning(ABORTED_ALREADY_RUNNING.format());
            return;
        }

        DiscoExporterConfiguration config = DiscoExporterConfiguration.get();
        if (config == null) {
            LOGGER.severe(CONFIG_NOT_AVAILABLE.format());
            return;
        }

        String subdomain = config.getSubdomain();
        if (subdomain == null || subdomain.isBlank()) {
            LOGGER.severe(SUBDOMAIN_INVALID.format());
            currentResult.setStatus(DiscoveryRunResult.Status.ABORTED);
            currentResult.setMessage(SUBDOMAIN_NOT_CONFIGURED.format());
            return;
        }

        if (triggerType == TriggerType.MANUAL && config.isRateLimitActive()) {
            LOGGER.warning(ABORTED_RATE_LIMIT.format());
            currentResult.setStatus(DiscoveryRunResult.Status.ABORTED);
            currentResult.setMessage(RATE_LIMIT_ACTIVE.format());
            return;
        }

        isRunning = true;
        currentResult.setStartTime(System.currentTimeMillis());
        currentResult.setStatus(DiscoveryRunResult.Status.RUNNING);
        currentResult.setMessage(DISCOVERY_STARTED.format(triggerType));

        LOGGER.info(DISCOVERY_STARTED.format(triggerType));

        try {
            OkHttpClient httpClient = new OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            // Step 1: Platform Discovery — resolve tenantId, identityBaseUrl, snapshotLinksUrl
            DiscoveryServiceClient discoveryClient = new DiscoveryServiceClient(httpClient);
            DiscoveryServiceResult discovery = discoveryClient.resolve(
                    config.getPlatformDiscoveryUrl(), subdomain);

            // Step 2: CyberArk Identity login — obtain bearer token
            String username = config.resolveUsername();
            byte[] passwordBytes = config.resolvePasswordBytes();
            if (username == null || passwordBytes == null) {
                throw new DiscoExportClient.ExportException(CREDENTIALS_NOT_RESOLVED.format());
            }
            CyberArkIdentityClient identityClient = new CyberArkIdentityClient(httpClient);
            byte[] tokenBytes;
            try {
                tokenBytes = identityClient.login(
                        discovery.getIdentityBaseUrl(),
                        username,
                        passwordBytes);
            } finally {
                java.util.Arrays.fill(passwordBytes, (byte) 0);
            }

            try {
                // Step 3: Key handshake — fetch from discovery-context/jwks (not snapshot-links)
                EncryptionService encryptionService = new EncryptionService(httpClient);
                encryptionService.fetchLatestKeys(discovery.getDiscoveryContextBaseUrl(), tokenBytes);
                String kid = encryptionService.getSelectedKid();
                currentResult.setKid(kid);

                // Step 4: Scan credentials
                UsageTracker usageTracker = new UsageTracker();
                usageTracker.scan();

                CredentialsDictionaryMapper mapper = new CredentialsDictionaryMapper(
                        config, encryptionService, usageTracker);
                List<CredentialRecord> credentials = mapper.mapAll();

                // Step 5: Collect structural context
                List<JenkinsObject> folders = collectFolders();
                List<JenkinsObject> jobs = collectJobs();

                // Step 6: Assemble snapshot
                OpenIdConfiguration openIdConfig = buildOpenIdConfiguration(config);
                currentResult.setJwksUri(openIdConfig.getJwksUri());
                currentResult.setConjurUrl(config.getConjurUrl());

                DiscoverySnapshot snapshot = buildSnapshot(config, kid, openIdConfig,
                        credentials, folders, jobs);

                // Step 7: Export
                String instanceId = Jenkins.get().getLegacyInstanceId();
                if (instanceId == null) instanceId = "unknown";
                DiscoExportClient exportClient = new DiscoExportClient(httpClient);
                exportClient.send(snapshot, discovery.getResolvedUrl(), tokenBytes,
                        getPluginVersion(), discovery.getTenantId(), username, instanceId);
            } finally {
                java.util.Arrays.fill(tokenBytes, (byte) 0);
            }

            config.setLastExportTimestamp(System.currentTimeMillis());
            currentResult.setStatus(DiscoveryRunResult.Status.SUCCESS);
            currentResult.setMessage(EXPORT_SUCCESSFUL.format());
            LOGGER.info(EXPORT_SUCCESSFUL.format());

        } catch (DiscoExportClient.ExportException e) {
            LOGGER.severe(EXPORT_FAILED.format(e.getMessage()));
            currentResult.setStatus(DiscoveryRunResult.Status.ERROR);
            currentResult.setMessage(EXPORT_FAILED.format(e.getMessage()));
        } catch (java.io.IOException e) {
            // IOExceptions from pipeline steps already carry their own DISC_XXX code.
            LOGGER.severe(e.getMessage());
            currentResult.setStatus(DiscoveryRunResult.Status.ERROR);
            currentResult.setMessage(e.getMessage());
        } catch (Exception e) {
            LOGGER.severe(PIPELINE_FAILED.format(e.getMessage()));
            currentResult.setStatus(DiscoveryRunResult.Status.ERROR);
            currentResult.setMessage(PIPELINE_FAILED.format(e.getMessage()));
        } finally {
            isRunning = false;
        }
    }

    // -------------------------------------------------------------------------

    private DiscoverySnapshot buildSnapshot(DiscoExporterConfiguration config,
                                             String kid, OpenIdConfiguration openIdConfiguration,
                                             List<CredentialRecord> credentials,
                                             List<JenkinsObject> folders,
                                             List<JenkinsObject> jobs) {
        DiscoverySnapshot snapshot = new DiscoverySnapshot();
        snapshot.setJenkinsId(Jenkins.get().getLegacyInstanceId());
        snapshot.setOriginStoreId(Jenkins.get().getLegacyInstanceId());
        snapshot.setDataSourceType("JenkinsDiscoveryPlugin");
        snapshot.setVersion(getPluginVersion());
        snapshot.setSnapshotId(UUID.randomUUID().toString());
        snapshot.setTimestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        snapshot.setDisCoConfig(DiscoExporterConfigurationSnapshot.from(config));
        snapshot.setKid(kid);
        snapshot.setOpenIdConfiguration(openIdConfiguration);
        snapshot.setConjurConfig(
                GlobalConjurConfigurationSnapshot.from(
                        org.conjur.jenkins.configuration.GlobalConjurConfiguration.get()));
        snapshot.setCredentials(credentials);
        snapshot.setFolders(folders);
        snapshot.setJobs(jobs);
        return snapshot;
    }

    private OpenIdConfiguration buildOpenIdConfiguration(DiscoExporterConfiguration config) {
        OpenIdConfiguration oidc = new OpenIdConfiguration();
        oidc.setIssuer(ConjurAPIUtils.getJenkinsIssuer());
        oidc.setJwksUri(config.getJwksUri());
        oidc.setJwksData(fetchJwksData());
        return oidc;
    }

    private Object fetchJwksData() {
        try {
            JwtAuthenticationService svc = jenkins.model.Jenkins.get()
                    .getExtensionList(JwtAuthenticationService.class)
                    .stream().findFirst().orElse(null);
            if (svc == null) return null;
            String jwksJson = svc.getJwkSet();
            if (jwksJson == null) return null;
            return new Gson().fromJson(jwksJson, Object.class);
        } catch (Exception e) {
            LOGGER.warning(JWKS_DATA_FAILED.format(e.getMessage()));
            return null;
        }
    }

    /**
     * Collects folder-level Jenkins objects as {@link JenkinsObject} entries.
     * The root (GlobalConfiguration) entry is always first.
     */
    public List<JenkinsObject> collectFolders() {
        List<JenkinsObject> records = new ArrayList<>();
        JenkinsObject globalObj = new JenkinsObject("", null, "", "GlobalConfiguration", "GlobalConfiguration", "");
        globalObj.setSub("GlobalCredentials");
        records.add(globalObj);
        GlobalConjurConfiguration globalConjurConfig = GlobalConjurConfiguration.get();
        try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
            for (AbstractFolder<?> folder : Jenkins.get().getAllItems(AbstractFolder.class)) {
                JenkinsObject obj = new JenkinsObject(
                        folder.getFullName(),
                        folder.getDescription(),
                        "",
                        folder.getClass().getName(),
                        folder.getPronoun(),
                        ""
                );
                obj.setInheritancePath(CredentialsDictionaryMapper.buildInheritancePath(folder.getClass()));
                obj.setConjurConfiguration(resolveFolderConjurConfiguration(folder));
                obj.setSub(JwtToken.computeSubClaim(folder, globalConjurConfig));
                records.add(obj);
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not collect folder objects", e);
        }
        return records;
    }

    /**
     * Collects job-level Jenkins objects as {@link JenkinsObject} entries.
     */
    public List<JenkinsObject> collectJobs() {
        List<JenkinsObject> records = new ArrayList<>();
        GlobalConjurConfiguration globalConjurConfig = GlobalConjurConfiguration.get();
        try (ACLContext aclCtx = ACL.as2(ACL.SYSTEM2)) {
            for (Job<?, ?> job : Jenkins.get().getAllItems(Job.class)) {
                String lastBuildTs = "";
                try {
                    hudson.model.Run<?, ?> last = job.getLastBuild();
                    if (last != null) {
                        lastBuildTs = DateTimeFormatter.ISO_INSTANT.format(
                                last.getTime().toInstant());
                    }
                } catch (RuntimeException ignored) {}

                String scmUrl = "";
                try {
                    if (job instanceof hudson.model.AbstractProject<?, ?> ap && ap.getScm() != null) {
                        scmUrl = ap.getScm().getType();
                    }
                } catch (RuntimeException ignored) {}

                JenkinsObject obj = new JenkinsObject(
                        job.getFullName(),
                        job.getDescription(),
                        scmUrl,
                        job.getClass().getName(),
                        job.getPronoun(),
                        lastBuildTs
                );
                obj.setInheritancePath(CredentialsDictionaryMapper.buildInheritancePath(job.getClass()));
                obj.setConjurConfiguration(resolveJobConjurConfiguration(job));
                obj.setSub(JwtToken.computeSubClaim(job, globalConjurConfig));
                records.add(obj);
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not collect job objects", e);
        }
        return records;
    }

    private ConjurConfiguration resolveFolderConjurConfiguration(AbstractFolder<?> folder) {
        try {
            FolderConjurConfiguration prop = folder.getProperties().get(FolderConjurConfiguration.class);
            if (prop == null) return null;
            ConjurConfiguration cc = prop.getConjurConfiguration();
            if (cc == null) return null;
            if (Boolean.TRUE.equals(cc.getInheritFromParent())
                    && (cc.getApplianceURL() == null || cc.getApplianceURL().isBlank())
                    && (cc.getAccount() == null || cc.getAccount().isBlank())) return null;
            return cc;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private ConjurConfiguration resolveJobConjurConfiguration(Job<?, ?> job) {
        try {
            ConjurJITJobProperty prop = job.getProperty(ConjurJITJobProperty.class);
            if (prop == null) return null;
            ConjurConfiguration cc = prop.getConjurConfiguration();
            if (cc == null) return null;
            if (Boolean.TRUE.equals(cc.getInheritFromParent())
                    && (cc.getApplianceURL() == null || cc.getApplianceURL().isBlank())
                    && (cc.getAccount() == null || cc.getAccount().isBlank())) return null;
            return cc;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String getPluginVersion() {
        return TelemetryConfiguration.getPluginVersion();
    }
}
