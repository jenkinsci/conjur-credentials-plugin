package org.conjur.jenkins.disco.manual;

import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import org.conjur.jenkins.disco.discovery.AnnotationMapper;
import org.conjur.jenkins.disco.export.DiscoExportClient;
import org.conjur.jenkins.disco.model.*;
import org.conjur.jenkins.disco.security.CyberArkIdentityClient;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

/**
 * Manual integration tests that build a Jenkins credential+folder+job structure
 * entirely in-memory (no Jenkins instance), serialize it as a DiscoverySnapshot,
 * then upload the JSON payload to the live FIS endpoint via DiscoExportClient.
 *
 * This mirrors what DiscoExportClient.send() does at runtime but lets you
 * craft arbitrary structures without running a real Jenkins server.
 *
 * ── How to run ────────────────────────────────────────────────────────────────
 *
 *   mvn test -pl . -Dtest=FisJenkinsStructureExportIT \
 *             -DARK_LIVE_TEST=true \
 *             [-DARK_IDENTITY_URL=https://...] \
 *             [-DARK_ENV_FILE=/path/to/.env] \
 *             [-DARK_ENV_DETAILS=/path/to/env_details_config.json] \
 *             [-DARK_ENV=integration] [-DARK_REGION=us-east-1]
 *
 * See FisJenkinsSnapshotIT for full env-var documentation.
 * These tests share the same ARK_* configuration properties.
 */
public class FisJenkinsStructureExportIT {

    private static final Logger LOG = Logger.getLogger(FisJenkinsStructureExportIT.class.getName());

    private static final String AGENT_VERSION        = "v2.0.0";
    private static final String JENKINS_API_SUFFIX   = "/api/ingestions/jenkins/snapshot-links";
    private static final String DATA_SOURCE_TYPE     = "JenkinsDiscoveryPlugin";
    private static final String PLUGIN_VERSION       = "3.1.0";

    private static String identityUrl;
    private static String subdomain;
    private static String tenantId;
    private static String username;
    private static String secret;
    private static String snapshotLinksUrl;

    private OkHttpClient httpClient;
    private final Gson gson = new Gson();

    // =========================================================================
    // Setup — reuses the same config-loading logic as FisJenkinsSnapshotIT
    // =========================================================================

    @BeforeClass
    public static void loadConfiguration() throws Exception {
        Assume.assumeTrue(
                "Set ARK_LIVE_TEST=true to run FIS structure-export integration tests",
                "true".equalsIgnoreCase(FisJenkinsSnapshotIT.env("ARK_LIVE_TEST")));

        if (!FisJenkinsSnapshotIT.env("ARK_IDENTITY_URL").isBlank()
                && !FisJenkinsSnapshotIT.env("ARK_USERNAME").isBlank()) {
            identityUrl      = FisJenkinsSnapshotIT.requireEnv("ARK_IDENTITY_URL");
            subdomain        = FisJenkinsSnapshotIT.requireEnv("ARK_SUBDOMAIN");
            tenantId         = FisJenkinsSnapshotIT.requireEnv("ARK_TENANT_ID");
            username         = FisJenkinsSnapshotIT.requireEnv("ARK_USERNAME");
            secret           = FisJenkinsSnapshotIT.requireEnv("ARK_SECRET");
            String lambdaUrl = FisJenkinsSnapshotIT.env("ARK_LAMBDA_URL");
            if (!lambdaUrl.isBlank()) {
                snapshotLinksUrl = lambdaUrl;
            } else {
                snapshotLinksUrl = FisJenkinsSnapshotIT.requireEnv("ARK_FIS_BASE_URL") + JENKINS_API_SUFFIX;
            }
        } else {
            // Fall back to config files — reuse FisJenkinsSnapshotIT.loadFromConfigFiles() indirectly
            // by reading .env + env_details_config.json ourselves
            String envFilePath = FisJenkinsSnapshotIT.env("ARK_ENV_FILE");
            if (envFilePath.isBlank()) {
                envFilePath = java.nio.file.Paths.get(System.getProperty("user.dir"),
                        "integration-tests", ".env").toString();
            }
            Map<String, String> dotEnv = FisJenkinsSnapshotIT.parseDotEnv(envFilePath);
            String credentials = dotEnv.getOrDefault("CREDENTIALS", "");
            if (credentials.isBlank()) {
                throw new IllegalStateException("No CREDENTIALS= in .env: " + envFilePath);
            }
            String[] parts = credentials.split(":", 3);
            if (parts.length < 3) {
                throw new IllegalStateException("CREDENTIALS must be Tina:<user>:<pass>");
            }
            username = parts[1];
            secret   = parts[2];

            String envDetailsPath = FisJenkinsSnapshotIT.env("ARK_ENV_DETAILS");
            if (envDetailsPath.isBlank()) {
                envDetailsPath = java.nio.file.Paths.get(envFilePath).getParent()
                        .resolve("env_details_config.json").toString();
            }
            String envKey    = FisJenkinsSnapshotIT.env("ARK_ENV", "integration");
            String regionKey = FisJenkinsSnapshotIT.env("ARK_REGION", "us-east-1");

            com.google.gson.JsonObject envDetails =
                    FisJenkinsSnapshotIT.parseEnvDetails(envDetailsPath, envKey, regionKey);
            tenantId         = envDetails.get("tenant_id").getAsString();
            subdomain        = envDetails.get("tenant_name").getAsString();
            snapshotLinksUrl = envDetails.get("fis_api_endpoint").getAsString() + JENKINS_API_SUFFIX;

            identityUrl = FisJenkinsSnapshotIT.env("ARK_IDENTITY_URL");
            if (identityUrl.isBlank()) {
                throw new IllegalStateException(
                        "ARK_IDENTITY_URL is required when using config files.");
            }
        }

        LOG.info("FisJenkinsStructureExportIT config: subdomain=" + subdomain
                + "  snapshotLinksUrl=" + snapshotLinksUrl);
    }

    @Before
    public void buildHttpClient() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    // =========================================================================
    // Scenario 1 — minimal snapshot: one credential, no folders, no jobs
    // =========================================================================

    /**
     * Upload a snapshot containing a single global StringCredential with no jobs or folders.
     * Verifies the pipeline accepts the minimal valid payload shape.
     */
    @Test
    public void scenario_minimalSnapshot_singleCredential() throws Exception {
        String controllerId = generateControllerId();
        LOG.info("scenario_minimalSnapshot: controllerId=" + controllerId);

        List<CredentialRecord> creds = Collections.singletonList(
                buildStringCredRecord("api-token", "Global",
                        "SystemCredentialsProvider", "global",
                        Collections.emptyList()));

        DiscoverySnapshot snapshot = buildSnapshot(controllerId, creds,
                Collections.emptyList(), Collections.emptyList());

        uploadSnapshot(snapshot, controllerId);
        LOG.info("scenario_minimalSnapshot: PASSED");
    }

    // =========================================================================
    // Scenario 2 — standard hierarchy: folders + jobs + mixed credentials
    // =========================================================================

    /**
     * Upload a full hierarchy that mirrors what DiscoveryOrchestrator produces:
     *   root → team → team/finance (folders)
     *   team/finance/deploy (Pipeline), team/build (FreeStyle), team/finance/report (Pipeline)
     *   global-string-secret, finance-db-cred (UsernamePassword), unused-cred
     */
    @Test
    public void scenario_fullHierarchySnapshot() throws Exception {
        String controllerId = generateControllerId();
        LOG.info("scenario_fullHierarchySnapshot: controllerId=" + controllerId);

        DiscoverySnapshot snapshot = buildSnapshot(
                controllerId,
                buildMixedCredentials(),
                buildFolders(),
                buildJobs());

        uploadSnapshot(snapshot, controllerId);
        LOG.info("scenario_fullHierarchySnapshot: PASSED");
    }

    // =========================================================================
    // Scenario 3 — large credential list (50 credentials across 5 folders)
    // =========================================================================

    /**
     * Upload a snapshot with 50 generated credentials spread across 5 folders
     * and 10 pipeline jobs. Validates that the pipeline handles a realistic
     * medium-sized payload without truncation or rejection.
     */
    @Test
    public void scenario_largeCredentialList() throws Exception {
        String controllerId = generateControllerId();
        LOG.info("scenario_largeCredentialList: controllerId=" + controllerId);

        List<String> folderPaths = Arrays.asList(
                "team-a", "team-b", "team-c", "team-d", "team-e");

        List<JenkinsObject> folders = new ArrayList<>();
        folders.add(new JenkinsObject("", null, "", "GlobalConfiguration", "GlobalConfiguration", ""));
        for (String f : folderPaths) {
            folders.add(new JenkinsObject(f, null, "",
                    "com.cloudbees.hudson.plugins.folder.Folder", "Folder", ""));
        }

        List<JenkinsObject> jobs = new ArrayList<>();
        for (String f : folderPaths) {
            for (int j = 1; j <= 2; j++) {
                jobs.add(new JenkinsObject(f + "/pipeline-" + j,
                        "Auto-generated pipeline " + j,
                        "hudson.plugins.git.GitSCM",
                        "org.jenkinsci.plugins.workflow.job.WorkflowJob",
                        "Pipeline",
                        "2026-05-01T10:00:00Z"));
            }
        }

        List<CredentialRecord> creds = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String folder = folderPaths.get(i % folderPaths.size());
            String credId = "generated-cred-" + String.format("%02d", i);
            List<String> usedBy = Arrays.asList(folder + "/pipeline-1");
            if (i % 3 == 0) {
                creds.add(buildStringCredRecord(credId, folder,
                        "FolderCredentialsProvider", "folder", usedBy));
            } else {
                creds.add(buildUserPassCredRecord(credId, folder,
                        "FolderCredentialsProvider", "folder", "svc-user-" + i, usedBy));
            }
        }

        DiscoverySnapshot snapshot = buildSnapshot(controllerId, creds, folders, jobs);
        uploadSnapshot(snapshot, controllerId);
        LOG.info("scenario_largeCredentialList: PASSED (50 creds, 10 jobs, 5 folders)");
    }

    // =========================================================================
    // Scenario 4 — cleanup snapshot (same controller, empty lists)
    // =========================================================================

    /**
     * Upload a full snapshot then immediately upload an empty cleanup snapshot
     * for the same controller ID — mirrors the Python FIS cleanup step.
     */
    @Test
    public void scenario_uploadThenCleanup() throws Exception {
        String controllerId = generateControllerId();
        LOG.info("scenario_uploadThenCleanup: controllerId=" + controllerId);

        DiscoverySnapshot full = buildSnapshot(
                controllerId,
                buildMixedCredentials(),
                buildFolders(),
                buildJobs());
        uploadSnapshot(full, controllerId);
        LOG.info("scenario_uploadThenCleanup: full snapshot uploaded");

        // Re-login for a fresh token before the cleanup upload
        byte[] freshToken = login();
        DiscoverySnapshot empty = buildSnapshot(controllerId,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        byte[] payload = toJsonBytes(empty);
        new DiscoExportClient(httpClient)
                .uploadViaSnapshotLinks(payload, snapshotLinksUrl, freshToken,
                        AGENT_VERSION, tenantId, username, controllerId);
        LOG.info("scenario_uploadThenCleanup: cleanup snapshot uploaded — PASSED");
    }

    // =========================================================================
    // Scenario 6 — short-name hierarchy: 4 folders × 4 sub-folders, max 3-char names
    // =========================================================================

    /**
     * Structure: 4 top-level folders (aaa, bbb, ccc, ddd), each containing
     * 4 sub-folders (p1…p4), 1 pipeline job per sub-folder, and 1 credential
     * per sub-folder scoped to that sub-folder.
     *
     * All names are ≤ 3 chars so they stand out clearly in logs.
     * Log prefix [SHN] (Short-Name Hierarchy) makes every line grep-able.
     */
    @Test
    public void scenario_shortNameHierarchy_4x4_foldersAndPipelines() throws Exception {
        String controllerId = generateControllerId();
        LOG.info("[SHN] START controllerId=" + controllerId);

        String[] roots = {"aaa", "bbb", "ccc", "ddd"};
        String[] subs  = {"p1",  "p2",  "p3",  "p4"};

        List<JenkinsObject> folders = new ArrayList<>();
        List<JenkinsObject> jobs    = new ArrayList<>();
        List<CredentialRecord> creds = new ArrayList<>();

        // global root
        folders.add(new JenkinsObject("", null, "", "GlobalConfiguration", "GlobalConfiguration", ""));

        for (String root : roots) {
            folders.add(new JenkinsObject(root, null, "",
                    "com.cloudbees.hudson.plugins.folder.Folder", "Folder", ""));
            LOG.info("[SHN] folder=" + root);

            for (String sub : subs) {
                String subPath = root + "/" + sub;
                folders.add(new JenkinsObject(subPath, null, "",
                        "com.cloudbees.hudson.plugins.folder.Folder", "Folder", ""));
                LOG.info("[SHN]   sub=" + subPath);

                String jobPath = subPath + "/run";
                jobs.add(new JenkinsObject(
                        jobPath,
                        "Pipeline for " + subPath,
                        "hudson.plugins.git.GitSCM",
                        "org.jenkinsci.plugins.workflow.job.WorkflowJob",
                        "Pipeline",
                        "2026-05-25T08:00:00Z"));
                LOG.info("[SHN]   job=" + jobPath);

                String credId = root + "-" + sub;
                creds.add(buildStringCredRecord(
                        credId, subPath,
                        "FolderCredentialsProvider", "folder",
                        Collections.singletonList(jobPath)));
                LOG.info("[SHN]   cred=" + credId + "  location=" + subPath);
            }
        }

        LOG.info("[SHN] totals: folders=" + folders.size()
                + "  jobs=" + jobs.size()
                + "  creds=" + creds.size());

        DiscoverySnapshot snapshot = buildSnapshot(controllerId, creds, folders, jobs);
        uploadSnapshot(snapshot, controllerId);
        LOG.info("[SHN] PASSED  folders=" + folders.size()
                + "  jobs=" + jobs.size()
                + "  creds=" + creds.size());
    }

    // =========================================================================
    // Scenario 5 — snapshot JSON logged to console for inspection
    // =========================================================================

    /**
     * Build the standard hierarchy, serialize to JSON, log the full payload,
     * then upload. Useful for visually inspecting what the plugin sends.
     */
    @Test
    public void scenario_logAndUploadSnapshot() throws Exception {
        String controllerId = generateControllerId();
        DiscoverySnapshot snapshot = buildSnapshot(
                controllerId,
                buildMixedCredentials(),
                buildFolders(),
                buildJobs());

        String json = gson.toJson(snapshot);
        LOG.info("scenario_logAndUploadSnapshot: payload=\n" + json);

        uploadSnapshot(snapshot, controllerId);
        LOG.info("scenario_logAndUploadSnapshot: PASSED");
    }

    // =========================================================================
    // Helpers — upload orchestration
    // =========================================================================

    private void uploadSnapshot(DiscoverySnapshot snapshot, String controllerId) throws Exception {
        byte[] token   = login();
        byte[] payload = toJsonBytes(snapshot);
        LOG.info("Uploading snapshot: controllerId=" + controllerId
                + "  payloadSize=" + payload.length + " bytes");
        new DiscoExportClient(httpClient)
                .uploadViaSnapshotLinks(payload, snapshotLinksUrl, token,
                        AGENT_VERSION, tenantId, username, controllerId);
    }

    private byte[] login() throws Exception {
        CyberArkIdentityClient client = new CyberArkIdentityClient(httpClient);
        byte[] token = client.login(identityUrl, subdomain, username,
                secret.getBytes(StandardCharsets.UTF_8));
        LOG.info("Login OK  token.length=" + token.length);
        return token;
    }

    private byte[] toJsonBytes(DiscoverySnapshot snapshot) {
        return gson.toJson(snapshot).getBytes(StandardCharsets.UTF_8);
    }

    // =========================================================================
    // Helpers — snapshot assembly
    // =========================================================================

    private DiscoverySnapshot buildSnapshot(String controllerId,
                                             List<CredentialRecord> credentials,
                                             List<JenkinsObject> folders,
                                             List<JenkinsObject> jobs) {
        DiscoverySnapshot s = new DiscoverySnapshot();
        s.setJenkinsId(controllerId);
        s.setOriginStoreId(controllerId);
        s.setDataSourceType(DATA_SOURCE_TYPE);
        s.setVersion(PLUGIN_VERSION);
        s.setSnapshotId(UUID.randomUUID().toString());
        s.setTimestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        s.setKid("test-kid");
        OpenIdConfiguration oidc = new OpenIdConfiguration();
        oidc.setIssuer("https://jenkins.example.com");
        oidc.setJwksUri("https://jenkins.example.com/jwtauth/conjur-jwk-set");
        oidc.setJwksData(null);
        s.setOpenIdConfiguration(oidc);
        s.setDisCoConfig(new DiscoExporterConfigurationSnapshot());
        s.setConjurConfig(new GlobalConjurConfigurationSnapshot());
        s.setCredentials(credentials);
        s.setFolders(folders);
        s.setJobs(jobs);
        return s;
    }

    // =========================================================================
    // Helpers — credential record builders (mirrors DiscoverySnapshotPayloadTest)
    // =========================================================================

    private CredentialRecord buildStringCredRecord(
            String id, String scopePath,
            String storeProvider, String scope,
            List<String> usedBy) {

        StringCredentials mock = Mockito.mock(StringCredentials.class);
        Mockito.when(mock.getId()).thenReturn(id);

        CredentialRecord rec = new CredentialRecord();
        rec.setCredentialId(id);
        rec.setName(id);
        rec.setOriginId(scopePath + ":" + id);
        rec.setType(StringCredentials.class.getName());
        rec.setLocation(scopePath);
        rec.setConjurization(AnnotationMapper.map(mock));
        rec.setAdditionalData(additionalData(storeProvider, scope, scopePath));
        rec.setFields(Collections.singletonMap("secret", "hudson.util.Secret"));
        rec.setValues(null);
        rec.setValuesWithError(Collections.emptyList());
        rec.setWhereUsed(new ArrayList<>(usedBy));
        rec.setLevelUpdatedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        return rec;
    }

    private CredentialRecord buildUserPassCredRecord(
            String id, String scopePath,
            String storeProvider, String scope,
            String uname, List<String> usedBy) {

        UsernamePasswordCredentialsImpl mock = Mockito.mock(UsernamePasswordCredentialsImpl.class);
        Mockito.when(mock.getId()).thenReturn(id);
        Mockito.when(mock.getUsername()).thenReturn(uname);

        CredentialRecord rec = new CredentialRecord();
        rec.setCredentialId(id);
        rec.setName(id);
        rec.setOriginId(scopePath + ":" + id);
        rec.setType(UsernamePasswordCredentialsImpl.class.getName());
        rec.setLocation(scopePath);
        rec.setDescription("Auto-generated UsernamePassword credential");
        rec.setConjurization(AnnotationMapper.map(mock));
        rec.setAdditionalData(additionalData(storeProvider, scope, scopePath));
        rec.setFields(Map.of("username", "java.lang.String", "password", "hudson.util.Secret"));
        rec.setValues(null);
        rec.setValuesWithError(Collections.emptyList());
        rec.setWhereUsed(new ArrayList<>(usedBy));
        rec.setLevelUpdatedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        return rec;
    }

    // =========================================================================
    // Helpers — standard structure (mirrors DiscoverySnapshotPayloadTest)
    // =========================================================================

    private List<CredentialRecord> buildMixedCredentials() {
        List<CredentialRecord> list = new ArrayList<>();
        list.add(buildStringCredRecord(
                "global-string-secret", "Global",
                "SystemCredentialsProvider", "global",
                Arrays.asList("team/finance/deploy")));
        list.add(buildUserPassCredRecord(
                "finance-db-cred", "team/finance",
                "FolderCredentialsProvider", "folder", "dbuser",
                Arrays.asList("team/finance/deploy", "team/finance/report", "team/finance")));
        list.add(buildStringCredRecord(
                "unused-cred", "Global",
                "SystemCredentialsProvider", "global",
                Collections.emptyList()));
        return list;
    }

    private List<JenkinsObject> buildFolders() {
        return Arrays.asList(
                new JenkinsObject("", null, "", "GlobalConfiguration", "GlobalConfiguration", ""),
                new JenkinsObject("team", null, "",
                        "com.cloudbees.hudson.plugins.folder.Folder", "Folder", ""),
                new JenkinsObject("team/finance", null, "",
                        "com.cloudbees.hudson.plugins.folder.Folder", "Folder", ""));
    }

    private List<JenkinsObject> buildJobs() {
        return Arrays.asList(
                new JenkinsObject(
                        "team/finance/deploy",
                        "Production deployment pipeline",
                        "hudson.plugins.git.GitSCM",
                        "org.jenkinsci.plugins.workflow.job.WorkflowJob",
                        "Pipeline",
                        "2026-03-25T10:00:00Z"),
                new JenkinsObject(
                        "team/build",
                        "Build job",
                        "",
                        "hudson.model.FreeStyleProject",
                        "Project",
                        ""),
                new JenkinsObject(
                        "team/finance/report",
                        "Finance reporting pipeline",
                        "hudson.plugins.git.GitSCM",
                        "org.jenkinsci.plugins.workflow.job.WorkflowJob",
                        "Pipeline",
                        "2026-03-24T08:30:00Z"));
    }

    private Map<String, String> additionalData(String storeProvider, String scope, String scopePath) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("storeProvider", storeProvider);
        data.put("storeProviderVersion", "unknown");
        data.put("scope", scope);
        data.put("scopePath", scopePath);
        return data;
    }

    /** Generate a controller identifier; reads ARK_CONTROLLER_ID env/system-property first. */
    private static String generateControllerId() {
        String fromEnv = System.getProperty("ARK_CONTROLLER_ID");
        if (fromEnv == null || fromEnv.isBlank()) fromEnv = System.getenv("ARK_CONTROLLER_ID");
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;
        var rng = RandomGenerator.getDefault();
        var sb  = new StringBuilder(32);
        String hex = "0123456789abcdef";
        for (int i = 0; i < 32; i++) sb.append(hex.charAt(rng.nextInt(16)));
        return sb.toString();
    }
}
