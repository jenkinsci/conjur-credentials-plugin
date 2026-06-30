package org.conjur.jenkins.disco.manual;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import org.conjur.jenkins.conjursecrets.*;
import org.conjur.jenkins.disco.discovery.AnnotationMapper;
import org.conjur.jenkins.disco.export.DiscoExportClient;
import org.conjur.jenkins.disco.model.*;
import org.conjur.jenkins.disco.security.CyberArkIdentityClient;
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
 * Manual integration tests that export snapshots containing the plugin's own
 * Conjur-specific credential types (ConjurSecretCredentials*).
 *
 * Each scenario exercises one or more credential types from the conjursecrets
 * package and wires them to mock Pipeline/FreeStyle jobs so the where-used
 * graph is populated, then uploads the resulting snapshot to the live FIS
 * endpoint.
 *
 * ── Credential types covered ─────────────────────────────────────────────────
 *   ConjurSecretCredentialsImpl        — generic Conjur secret (StringCredentials)
 *   ConjurSecretStringCredentialsImpl  — Conjur string secret
 *   ConjurSecretUsernameCredentialsImpl — Conjur username+secret pair
 *   ConjurSecretUsernameSSHKeyCredentialsImpl — Conjur username+SSH key pair
 *   ConjurSecretFileCredentialsImpl    — Conjur file secret
 *   ConjurSecretDockerCertCredentialsImpl — Conjur Docker client cert (3-field)
 *
 * ── How to run ────────────────────────────────────────────────────────────────
 *
 *   mvn test -pl . -Dtest=FisConjurCredentialsExportIT \
 *             -DARK_LIVE_TEST=true \
 *             -DARK_IDENTITY_URL=https://<pod>.id.<domain> \
 *             -DARK_SUBDOMAIN=<tenant> \
 *             -DARK_TENANT_ID=<uuid> \
 *             -DARK_USERNAME=<user> \
 *             -DARK_SECRET=<pass> \
 *             -DARK_LAMBDA_URL=https://...jenkins/snapshot-links
 *
 * See integration-tests/INTEGRATION_TESTS.md for full documentation.
 */
public class FisConjurCredentialsExportIT {

    private static final Logger LOG = Logger.getLogger(FisConjurCredentialsExportIT.class.getName());

    private static final String AGENT_VERSION      = "v2.0.0";
    private static final String JENKINS_API_SUFFIX = "/api/ingestions/jenkins/snapshot-links";
    private static final String DATA_SOURCE_TYPE   = "JenkinsDiscoveryPlugin";
    private static final String PLUGIN_VERSION     = "3.1.0";

    private static String identityUrl;
    private static String subdomain;
    private static String tenantId;
    private static String username;
    private static String secret;
    private static String snapshotLinksUrl;

    private OkHttpClient httpClient;
    private final Gson gson = new Gson();

    // =========================================================================
    // Setup
    // =========================================================================

    @BeforeClass
    public static void loadConfiguration() throws Exception {
        Assume.assumeTrue(
                "Set ARK_LIVE_TEST=true to run Conjur credentials export integration tests",
                "true".equalsIgnoreCase(FisJenkinsSnapshotIT.env("ARK_LIVE_TEST")));

        if (!FisJenkinsSnapshotIT.env("ARK_IDENTITY_URL").isBlank()
                && !FisJenkinsSnapshotIT.env("ARK_USERNAME").isBlank()) {
            identityUrl      = FisJenkinsSnapshotIT.requireEnv("ARK_IDENTITY_URL");
            subdomain        = FisJenkinsSnapshotIT.requireEnv("ARK_SUBDOMAIN");
            tenantId         = FisJenkinsSnapshotIT.requireEnv("ARK_TENANT_ID");
            username         = FisJenkinsSnapshotIT.requireEnv("ARK_USERNAME");
            secret           = FisJenkinsSnapshotIT.requireEnv("ARK_SECRET");
            String lambdaUrl = FisJenkinsSnapshotIT.env("ARK_LAMBDA_URL");
            snapshotLinksUrl = lambdaUrl.isBlank()
                    ? FisJenkinsSnapshotIT.requireEnv("ARK_FIS_BASE_URL") + JENKINS_API_SUFFIX
                    : lambdaUrl;
        } else {
            String envFilePath = FisJenkinsSnapshotIT.env("ARK_ENV_FILE");
            if (envFilePath.isBlank()) {
                envFilePath = java.nio.file.Paths.get(System.getProperty("user.dir"),
                        "integration-tests", ".env").toString();
            }
            Map<String, String> dotEnv = FisJenkinsSnapshotIT.parseDotEnv(envFilePath);
            String[] parts = dotEnv.getOrDefault("CREDENTIALS", "").split(":", 3);
            if (parts.length < 3) {
                throw new IllegalStateException("CREDENTIALS must be Tina:<user>:<pass> in .env");
            }
            username = parts[1];
            secret   = parts[2];

            String envDetailsPath = FisJenkinsSnapshotIT.env("ARK_ENV_DETAILS");
            if (envDetailsPath.isBlank()) {
                envDetailsPath = java.nio.file.Paths.get(envFilePath).getParent()
                        .resolve("env_details_config.json").toString();
            }
            com.google.gson.JsonObject envDetails = FisJenkinsSnapshotIT.parseEnvDetails(
                    envDetailsPath,
                    FisJenkinsSnapshotIT.env("ARK_ENV", "integration"),
                    FisJenkinsSnapshotIT.env("ARK_REGION", "us-east-1"));
            tenantId         = envDetails.get("tenant_id").getAsString();
            subdomain        = envDetails.get("tenant_name").getAsString();
            snapshotLinksUrl = envDetails.get("fis_api_endpoint").getAsString() + JENKINS_API_SUFFIX;

            identityUrl = FisJenkinsSnapshotIT.requireEnv("ARK_IDENTITY_URL");
        }

        LOG.info("FisConjurCredentialsExportIT config: subdomain=" + subdomain
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
    // Scenario 1 — ConjurSecretCredentialsImpl (generic Conjur secret)
    // =========================================================================

    /**
     * Export a snapshot containing ConjurSecretCredentialsImpl (the generic
     * "Conjur Secret Credential" type). Maps to stringcredential in DisCo.
     * Wired to two Pipeline jobs to populate where-used.
     */
    @Test
    public void scenario_conjurSecretCredential() throws Exception {
        String controllerId = generateControllerId();
        LOG.info("scenario_conjurSecretCredential: controllerId=" + controllerId);

        ConjurSecretCredentialsImpl cred = new ConjurSecretCredentialsImpl(
                CredentialsScope.GLOBAL, "conjur-api-key", "secret/jenkins/api-key", "Jenkins API key");

        List<String> usedBy = Arrays.asList("ops/deploy-prod", "ops/smoke-test");
        CredentialRecord rec = buildConjurSecretRecord(cred, "Global",
                "SystemCredentialsProvider", "global", usedBy);

        List<JenkinsObject> folders = Arrays.asList(
                globalRoot(),
                folder("ops", "Operations team"));
        List<JenkinsObject> jobs = Arrays.asList(
                pipeline("ops/deploy-prod", "Production deployment", ""),
                pipeline("ops/smoke-test", "Smoke test suite", ""));

        uploadSnapshot(controllerId, Collections.singletonList(rec), folders, jobs);
        LOG.info("scenario_conjurSecretCredential: PASSED");
    }

    // =========================================================================
    // Scenario 2 — ConjurSecretStringCredentialsImpl
    // =========================================================================

    /**
     * Export a snapshot with ConjurSecretStringCredentialsImpl. Same DisCo
     * type (stringcredential) but different Jenkins class, used in a folder
     * credential store.
     */
    @Test
    public void scenario_conjurSecretStringCredential() throws Exception {
        String controllerId = generateControllerId();
        LOG.info("scenario_conjurSecretStringCredential: controllerId=" + controllerId);

        ConjurSecretStringCredentialsImpl cred = new ConjurSecretStringCredentialsImpl(
                CredentialsScope.GLOBAL, "conjur-db-password",
                "secret/db/password", "Database password from Conjur");

        CredentialRecord rec = buildConjurStringRecord(cred, "team/backend",
                "FolderCredentialsProvider", "folder",
                Arrays.asList("team/backend/migrate", "team/backend/deploy"));

        List<JenkinsObject> folders = Arrays.asList(
                globalRoot(),
                folder("team", ""),
                folder("team/backend", "Backend services"));
        List<JenkinsObject> jobs = Arrays.asList(
                pipeline("team/backend/migrate", "DB migration pipeline", ""),
                pipeline("team/backend/deploy", "Backend deployment", ""));

        uploadSnapshot(controllerId, Collections.singletonList(rec), folders, jobs);
        LOG.info("scenario_conjurSecretStringCredential: PASSED");
    }

    // =========================================================================
    // Scenario 3 — ConjurSecretUsernameCredentialsImpl
    // =========================================================================

    /**
     * Export a ConjurSecretUsernameCredentialsImpl. Maps to usernamecredential
     * in DisCo with username annotation. Pipeline job references it directly.
     */
    @Test
    public void scenario_conjurSecretUsernameCredential() throws Exception {
        String controllerId = generateControllerId();
        LOG.info("scenario_conjurSecretUsernameCredential: controllerId=" + controllerId);

        ConjurSecretUsernameCredentialsImpl cred = new ConjurSecretUsernameCredentialsImpl(
                CredentialsScope.GLOBAL, "conjur-svc-account",
                "svc-deploy", "secret/svc/password", "Service account for deployments");

        CredentialRecord rec = buildGenericRecord(
                "conjur-svc-account", "Global",
                "SystemCredentialsProvider", "global",
                ConjurSecretUsernameCredentialsImpl.class.getName(),
                buildUsernameConjurization("conjur-svc-account", "svc-deploy"),
                Arrays.asList("platform/release"));

        List<JenkinsObject> folders = Arrays.asList(globalRoot(), folder("platform", "Platform team"));
        List<JenkinsObject> jobs = Collections.singletonList(
                pipeline("platform/release", "Release pipeline", "hudson.plugins.git.GitSCM"));

        uploadSnapshot(controllerId, Collections.singletonList(rec), folders, jobs);
        LOG.info("scenario_conjurSecretUsernameCredential: PASSED");
    }

    // =========================================================================
    // Scenario 4 — ConjurSecretUsernameSSHKeyCredentialsImpl
    // =========================================================================

    /**
     * Export a ConjurSecretUsernameSSHKeyCredentialsImpl (SSH key backed by Conjur).
     * Maps to usernamesshkeycredential in DisCo. Used by a FreeStyle build job.
     */
    @Test
    public void scenario_conjurSecretSSHKeyCredential() throws Exception {
        String controllerId = generateControllerId();
        LOG.info("scenario_conjurSecretSSHKeyCredential: controllerId=" + controllerId);

        ConjurSecretUsernameSSHKeyCredentialsImpl cred =
                new ConjurSecretUsernameSSHKeyCredentialsImpl(
                        CredentialsScope.GLOBAL, "conjur-git-ssh",
                        "git", "secret/git/private-key",
                        hudson.util.Secret.fromString(""), "Git SSH key via Conjur");

        CredentialRecord rec = buildGenericRecord(
                "conjur-git-ssh", "Global",
                "SystemCredentialsProvider", "global",
                ConjurSecretUsernameSSHKeyCredentialsImpl.class.getName(),
                buildSshKeyConjurization("conjur-git-ssh", "git"),
                Arrays.asList("infra/checkout", "infra/tag-release"));

        List<JenkinsObject> folders = Arrays.asList(globalRoot(), folder("infra", "Infrastructure"));
        List<JenkinsObject> jobs = Arrays.asList(
                freeStyle("infra/checkout", "Repository checkout job"),
                pipeline("infra/tag-release", "Tagging pipeline", "hudson.plugins.git.GitSCM"));

        uploadSnapshot(controllerId, Collections.singletonList(rec), folders, jobs);
        LOG.info("scenario_conjurSecretSSHKeyCredential: PASSED");
    }

    // =========================================================================
    // Scenario 5 — ConjurSecretFileCredentialsImpl
    // =========================================================================

    /**
     * Export a ConjurSecretFileCredentialsImpl (a file secret backed by Conjur).
     * Maps to filecredential in DisCo with double-map as stringcredential.
     */
    @Test
    public void scenario_conjurSecretFileCredential() throws Exception {
        String controllerId = generateControllerId();
        LOG.info("scenario_conjurSecretFileCredential: controllerId=" + controllerId);

        ConjurSecretFileCredentialsImpl cred = new ConjurSecretFileCredentialsImpl(
                CredentialsScope.GLOBAL, "conjur-kubeconfig",
                "Kubernetes config file from Conjur", "secret/k8s/kubeconfig");

        CredentialRecord rec = buildGenericRecord(
                "conjur-kubeconfig", "Global",
                "SystemCredentialsProvider", "global",
                ConjurSecretFileCredentialsImpl.class.getName(),
                buildFileConjurization(),
                Arrays.asList("k8s/deploy", "k8s/rollback"));

        List<JenkinsObject> folders = Arrays.asList(globalRoot(), folder("k8s", "Kubernetes ops"));
        List<JenkinsObject> jobs = Arrays.asList(
                pipeline("k8s/deploy", "K8s deployment pipeline", ""),
                pipeline("k8s/rollback", "K8s rollback pipeline", ""));

        uploadSnapshot(controllerId, Collections.singletonList(rec), folders, jobs);
        LOG.info("scenario_conjurSecretFileCredential: PASSED");
    }

    // =========================================================================
    // Scenario 6 — ConjurSecretDockerCertCredentialsImpl
    // =========================================================================

    /**
     * Export a ConjurSecretDockerCertCredentialsImpl (3-field Docker TLS cert).
     * Maps to dockercertcredential in DisCo with key/cert/ca sub-values.
     */
    @Test
    public void scenario_conjurSecretDockerCertCredential() throws Exception {
        String controllerId = generateControllerId();
        LOG.info("scenario_conjurSecretDockerCertCredential: controllerId=" + controllerId);

        ConjurSecretDockerCertCredentialsImpl cred = new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "conjur-docker-tls",
                "Docker TLS certs from Conjur",
                "secret/docker/client-key",
                "secret/docker/client-cert",
                "secret/docker/ca-cert");

        CredentialRecord rec = buildGenericRecord(
                "conjur-docker-tls", "Global",
                "SystemCredentialsProvider", "global",
                ConjurSecretDockerCertCredentialsImpl.class.getName(),
                buildDockerCertConjurization("conjur-docker-tls"),
                Arrays.asList("docker/build-image", "docker/push-image"));

        List<JenkinsObject> folders = Arrays.asList(globalRoot(), folder("docker", "Docker build farm"));
        List<JenkinsObject> jobs = Arrays.asList(
                freeStyle("docker/build-image", "Docker image build"),
                freeStyle("docker/push-image", "Docker image push"));

        uploadSnapshot(controllerId, Collections.singletonList(rec), folders, jobs);
        LOG.info("scenario_conjurSecretDockerCertCredential: PASSED");
    }

    // =========================================================================
    // Scenario 7 — all Conjur credential types in one snapshot
    // =========================================================================

    /**
     * Export a single snapshot containing one of every Conjur credential type.
     * Verifies the pipeline accepts a mixed-type payload and that all
     * credential types produce valid DisCo conjurization entries.
     */
    @Test
    public void scenario_allConjurCredentialTypes() throws Exception {
        String controllerId = generateControllerId();
        LOG.info("scenario_allConjurCredentialTypes: controllerId=" + controllerId);

        List<CredentialRecord> creds = new ArrayList<>();

        // ConjurSecretCredentialsImpl
        creds.add(buildConjurSecretRecord(
                new ConjurSecretCredentialsImpl(CredentialsScope.GLOBAL,
                        "conjur-generic", "secret/generic", "Generic"),
                "Global", "SystemCredentialsProvider", "global",
                Arrays.asList("mixed/pipeline-a")));

        // ConjurSecretStringCredentialsImpl
        creds.add(buildConjurStringRecord(
                new ConjurSecretStringCredentialsImpl(CredentialsScope.GLOBAL,
                        "conjur-string", "secret/token", "Token"),
                "Global", "SystemCredentialsProvider", "global",
                Arrays.asList("mixed/pipeline-b")));

        // ConjurSecretUsernameCredentialsImpl
        creds.add(buildGenericRecord(
                "conjur-username", "Global",
                "SystemCredentialsProvider", "global",
                ConjurSecretUsernameCredentialsImpl.class.getName(),
                buildUsernameConjurization("conjur-username", "svcuser"),
                Arrays.asList("mixed/pipeline-a", "mixed/pipeline-b")));

        // ConjurSecretUsernameSSHKeyCredentialsImpl
        creds.add(buildGenericRecord(
                "conjur-ssh", "Global",
                "SystemCredentialsProvider", "global",
                ConjurSecretUsernameSSHKeyCredentialsImpl.class.getName(),
                buildSshKeyConjurization("conjur-ssh", "gituser"),
                Arrays.asList("mixed/pipeline-c")));

        // ConjurSecretFileCredentialsImpl
        creds.add(buildGenericRecord(
                "conjur-file", "Global",
                "SystemCredentialsProvider", "global",
                ConjurSecretFileCredentialsImpl.class.getName(),
                buildFileConjurization(),
                Collections.emptyList()));

        // ConjurSecretDockerCertCredentialsImpl
        creds.add(buildGenericRecord(
                "conjur-docker", "Global",
                "SystemCredentialsProvider", "global",
                ConjurSecretDockerCertCredentialsImpl.class.getName(),
                buildDockerCertConjurization("conjur-docker"),
                Arrays.asList("mixed/pipeline-c")));

        List<JenkinsObject> folders = Arrays.asList(globalRoot(), folder("mixed", "Mixed credential test folder"));
        List<JenkinsObject> jobs = Arrays.asList(
                pipeline("mixed/pipeline-a", "Pipeline A", ""),
                pipeline("mixed/pipeline-b", "Pipeline B", ""),
                pipeline("mixed/pipeline-c", "Pipeline C", "hudson.plugins.git.GitSCM"));

        String json = gson.toJson(buildSnapshot(controllerId, creds, folders, jobs));
        LOG.info("scenario_allConjurCredentialTypes: payloadSize=" + json.length() + " chars");

        uploadSnapshot(controllerId, creds, folders, jobs);
        LOG.info("scenario_allConjurCredentialTypes: PASSED (" + creds.size() + " cred types)");
    }

    // =========================================================================
    // Scenario 8 — large snapshot: 100 folders, 100 pipelines, 100 credentials
    // =========================================================================

    /**
     * Stress-test with 100 folders (10 teams × 10 sub-folders each), 100 pipeline
     * jobs (one per sub-folder), and 100 Conjur credentials distributed across
     * all credential types, plus standard Jenkins objects (global root,
     * FreeStyle build jobs, a multi-branch pipeline job).
     *
     * Every credential is wired to at least one job via where-used so the full
     * graph is populated.
     */
    @Test
    public void scenario_largeConjurSnapshot_100x100x100() throws Exception {
        String controllerId = generateControllerId();
        LOG.info("scenario_largeConjurSnapshot: controllerId=" + controllerId);

        int TEAMS    = 10;
        int SUBS     = 10;   // sub-folders per team → 100 total leaf folders
        int JOBS     = 100;  // one pipeline per leaf folder
        int CREDS    = 100;

        // ── Folders ─────────────────────────────────────────────────────────
        List<JenkinsObject> folders = new ArrayList<>();
        folders.add(globalRoot());

        List<String> leafFolders = new ArrayList<>();
        for (int t = 1; t <= TEAMS; t++) {
            String teamPath = "team-" + String.format("%02d", t);
            folders.add(folder(teamPath, "Team " + t));
            for (int s = 1; s <= SUBS; s++) {
                String subPath = teamPath + "/sub-" + String.format("%02d", s);
                folders.add(folder(subPath, "Sub-folder " + s + " of team " + t));
                leafFolders.add(subPath);
            }
        }

        // ── Jobs (100 pipelines, one per leaf folder + extras) ────────────
        List<JenkinsObject> jobs = new ArrayList<>();
        List<String> jobPaths = new ArrayList<>();
        for (String leaf : leafFolders) {
            String jobPath = leaf + "/pipeline";
            jobs.add(pipeline(jobPath, "Auto pipeline in " + leaf, ""));
            jobPaths.add(jobPath);
        }
        // Additional standard Jenkins objects: FreeStyle + placeholder for multi-branch
        jobs.add(freeStyle("team-01/build-all", "Global FreeStyle build"));
        jobs.add(freeStyle("team-02/build-all", "Team-02 FreeStyle build"));
        jobs.add(new JenkinsObject("team-01/multi-branch", "Multi-branch scan",
                "hudson.plugins.git.GitSCM",
                "org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject",
                "Multibranch Pipeline", "2026-05-01T06:00:00Z"));

        // ── Credentials (100 across all Conjur types) ─────────────────────
        List<CredentialRecord> creds = new ArrayList<>();
        String[] credTypes = {
                "conjur-secret", "conjur-string", "conjur-username", "conjur-ssh",
                "conjur-file",   "conjur-docker"
        };

        for (int i = 0; i < CREDS; i++) {
            String typeKey    = credTypes[i % credTypes.length];
            String credId     = typeKey + "-" + String.format("%03d", i);
            String folder     = leafFolders.get(i % leafFolders.size());
            String scope      = i < 20 ? "Global" : folder;
            String provider   = i < 20 ? "SystemCredentialsProvider" : "FolderCredentialsProvider";
            String scopeType  = i < 20 ? "global" : "folder";
            List<String> usedBy = Collections.singletonList(jobPaths.get(i % jobPaths.size()));

            CredentialRecord rec = switch (typeKey) {
                case "conjur-secret" -> buildConjurSecretRecord(
                        new ConjurSecretCredentialsImpl(CredentialsScope.GLOBAL, credId,
                                "secret/" + credId, "Auto-gen " + credId),
                        scope, provider, scopeType, usedBy);
                case "conjur-string" -> buildConjurStringRecord(
                        new ConjurSecretStringCredentialsImpl(CredentialsScope.GLOBAL, credId,
                                "secret/" + credId, "Auto-gen " + credId),
                        scope, provider, scopeType, usedBy);
                case "conjur-username" -> buildGenericRecord(credId, scope, provider, scopeType,
                        ConjurSecretUsernameCredentialsImpl.class.getName(),
                        buildUsernameConjurization(credId, "svc-" + i), usedBy);
                case "conjur-ssh" -> buildGenericRecord(credId, scope, provider, scopeType,
                        ConjurSecretUsernameSSHKeyCredentialsImpl.class.getName(),
                        buildSshKeyConjurization(credId, "git-" + i), usedBy);
                case "conjur-file" -> buildGenericRecord(credId, scope, provider, scopeType,
                        ConjurSecretFileCredentialsImpl.class.getName(),
                        buildFileConjurization(), usedBy);
                default -> buildGenericRecord(credId, scope, provider, scopeType,
                        ConjurSecretDockerCertCredentialsImpl.class.getName(),
                        buildDockerCertConjurization(credId), usedBy);
            };
            creds.add(rec);
        }

        LOG.info("scenario_largeConjurSnapshot: folders=" + folders.size()
                + "  jobs=" + jobs.size()
                + "  creds=" + creds.size());

        String json = gson.toJson(buildSnapshot(controllerId, creds, folders, jobs));
        LOG.info("scenario_largeConjurSnapshot: payloadSize=" + json.length() + " chars");

        uploadSnapshot(controllerId, creds, folders, jobs);
        LOG.info("scenario_largeConjurSnapshot_100x100x100: PASSED");
    }

    // =========================================================================
    // Upload helpers
    // =========================================================================

    private void uploadSnapshot(String controllerId,
                                 List<CredentialRecord> creds,
                                 List<JenkinsObject> folders,
                                 List<JenkinsObject> jobs) throws Exception {
        DiscoverySnapshot snapshot = buildSnapshot(controllerId, creds, folders, jobs);
        byte[] token   = login();
        byte[] payload = gson.toJson(snapshot).getBytes(StandardCharsets.UTF_8);
        LOG.info("Uploading: controllerId=" + controllerId + "  size=" + payload.length + " bytes");
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

    // =========================================================================
    // Snapshot assembly
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
    // Credential record builders — one per Conjur credential type
    // =========================================================================

    /**
     * Build a CredentialRecord for ConjurSecretCredentialsImpl.
     * AnnotationMapper treats it as a generic/unknown → stringcredential.
     */
    private CredentialRecord buildConjurSecretRecord(
            ConjurSecretCredentialsImpl cred, String scopePath,
            String storeProvider, String scope, List<String> usedBy) {

        // ConjurSecretCredentialsImpl is not a StringCredentials, SSHUser, etc.
        // AnnotationMapper falls into the generic else-branch → stringcredential.
        Map<String, String> conjurization = AnnotationMapper.map(cred);

        CredentialRecord rec = new CredentialRecord();
        rec.setCredentialId(cred.getId());
        rec.setName(cred.getId());
        rec.setOriginId(scopePath + ":" + cred.getId());
        rec.setType(ConjurSecretCredentialsImpl.class.getName());
        rec.setLocation(scopePath);
        rec.setDescription(cred.getDescription());
        rec.setConjurization(conjurization);
        rec.setAdditionalData(additionalData(storeProvider, scope, scopePath));
        rec.setFields(Collections.singletonMap("variableId", "java.lang.String"));
        rec.setValues(null);
        rec.setValuesWithError(Collections.emptyList());
        rec.setWhereUsed(new ArrayList<>(usedBy));
        rec.setLevelUpdatedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        return rec;
    }

    /**
     * Build a CredentialRecord for ConjurSecretStringCredentialsImpl.
     * Also falls into generic → stringcredential via AnnotationMapper.
     */
    private CredentialRecord buildConjurStringRecord(
            ConjurSecretStringCredentialsImpl cred, String scopePath,
            String storeProvider, String scope, List<String> usedBy) {

        Map<String, String> conjurization = AnnotationMapper.map(cred);

        CredentialRecord rec = new CredentialRecord();
        rec.setCredentialId(cred.getId());
        rec.setName(cred.getId());
        rec.setOriginId(scopePath + ":" + cred.getId());
        rec.setType(ConjurSecretStringCredentialsImpl.class.getName());
        rec.setLocation(scopePath);
        rec.setDescription(cred.getDescription());
        rec.setConjurization(conjurization);
        rec.setAdditionalData(additionalData(storeProvider, scope, scopePath));
        rec.setFields(Collections.singletonMap("variableId", "java.lang.String"));
        rec.setValues(null);
        rec.setValuesWithError(Collections.emptyList());
        rec.setWhereUsed(new ArrayList<>(usedBy));
        rec.setLevelUpdatedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        return rec;
    }

    /**
     * Generic record builder for credential types that need a pre-built
     * conjurization map (types not directly recognised by AnnotationMapper).
     */
    private CredentialRecord buildGenericRecord(
            String credId, String scopePath,
            String storeProvider, String scope,
            String type,
            Map<String, String> conjurization,
            List<String> usedBy) {

        CredentialRecord rec = new CredentialRecord();
        rec.setCredentialId(credId);
        rec.setName(credId);
        rec.setOriginId(scopePath + ":" + credId);
        rec.setType(type);
        rec.setLocation(scopePath);
        rec.setConjurization(conjurization);
        rec.setAdditionalData(additionalData(storeProvider, scope, scopePath));
        rec.setValues(null);
        rec.setValuesWithError(Collections.emptyList());
        rec.setWhereUsed(new ArrayList<>(usedBy));
        rec.setLevelUpdatedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        return rec;
    }

    // =========================================================================
    // Conjurization map builders — hand-craft what AnnotationMapper would produce
    // for Conjur-specific credential types that it doesn't directly recognise
    // =========================================================================

    private Map<String, String> buildUsernameConjurization(String credId, String uname) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("variable:annotation:jenkins_credential_type", "usernamecredential");
        m.put("variable:annotation:jenkins_credential_username", "{{username}}");
        m.put("variable:value", "{{password}}");
        m.put("variable:annotation:jenkins_credential_type_alt", "stringcredential");
        return m;
    }

    private Map<String, String> buildSshKeyConjurization(String credId, String uname) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("variable:annotation:jenkins_credential_type", "usernamesshkeycredential");
        m.put("variable:annotation:jenkins_credential_username", "{{username}}");
        m.put("variable:value", "{{passphrase}}");
        m.put("variable:annotation:jenkins_credential_type_alt", "stringcredential");
        return m;
    }

    private Map<String, String> buildFileConjurization() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("variable:annotation:jenkins_credential_type", "filecredential");
        m.put("variable:value", "{{content}}");
        m.put("variable:annotation:jenkins_credential_type_alt", "stringcredential");
        return m;
    }

    private Map<String, String> buildDockerCertConjurization(String credId) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("variable:annotation:jenkins_credential_type", "dockercertcredential");
        m.put("variable:value:key",  credId + "/key");
        m.put("variable:value:cert", credId + "/cert");
        m.put("variable:value:ca",   credId + "/ca");
        m.put("variable:annotation:jenkins_credential_type_alt", "stringcredential");
        return m;
    }

    // =========================================================================
    // JenkinsObject factory helpers
    // =========================================================================

    private JenkinsObject globalRoot() {
        return new JenkinsObject("", null, "", "GlobalConfiguration", "GlobalConfiguration", "");
    }

    private JenkinsObject folder(String path, String description) {
        return new JenkinsObject(path, description.isBlank() ? null : description, "",
                "com.cloudbees.hudson.plugins.folder.Folder", "Folder", "");
    }

    private JenkinsObject pipeline(String path, String description, String scmUrl) {
        return new JenkinsObject(path, description, scmUrl,
                "org.jenkinsci.plugins.workflow.job.WorkflowJob",
                "Pipeline",
                DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
    }

    private JenkinsObject freeStyle(String path, String description) {
        return new JenkinsObject(path, description, "",
                "hudson.model.FreeStyleProject", "Project", "");
    }

    // =========================================================================
    // Other helpers
    // =========================================================================

    private Map<String, String> additionalData(String storeProvider, String scope, String scopePath) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("storeProvider", storeProvider);
        data.put("storeProviderVersion", "unknown");
        data.put("scope", scope);
        data.put("scopePath", scopePath);
        return data;
    }

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
