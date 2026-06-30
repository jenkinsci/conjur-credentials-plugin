package org.conjur.jenkins.disco;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.util.Secret;
import org.conjur.jenkins.disco.config.DiscoExporterConfiguration;
import org.conjur.jenkins.disco.discovery.CredentialsDictionaryMapper;
import org.conjur.jenkins.disco.discovery.DiscoveryOrchestrator;
import org.conjur.jenkins.disco.discovery.UsageTracker;
import org.conjur.jenkins.disco.model.DiscoverySnapshot;
import org.conjur.jenkins.disco.model.JenkinsObject;
import org.conjur.jenkins.disco.model.OpenIdConfiguration;
import org.conjur.jenkins.disco.model.DiscoExporterConfigurationSnapshot;
import org.conjur.jenkins.disco.security.EncryptionService;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies that a credential stored in a folder and referenced by a pipeline
 * inside that folder via withCredentials(…) appears in the JSON snapshot's
 * whereUsed array with the correct pipeline path.
 *
 * Hierarchy:
 *
 *   myteam/                        (Folder)
 *     ├─ myteam/db-cred            (UsernamePasswordCredentials stored in folder)
 *     ├─ myteam/token-cred         (StringCredentials stored in folder)
 *     ├─ GlobalSecretFirst         (StringCredentials stored in folder)
 *     ├─ myteam/my-pipeline        (WorkflowJob)
 *     │     withCredentials([usernamePassword(credentialsId: 'db-cred', ...)])
 *     │     withCredentials([string(credentialsId: 'token-cred', ...)])
 *     └─ myteam/payments-pipeline  (WorkflowJob — uses GlobalSecretFirst via stage+withCredentials)
 *           stage('Use DisCo payments folder secrets') {
 *             withCredentials([string(credentialsId: 'GlobalSecretFirst', variable: 'DISCO_SECRET')])
 *
 * EncryptionService and DiscoExporterConfiguration are mocked — no live
 * network calls are made.
 */
public class FolderCredentialWhereUsedTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static final String FOLDER_NAME      = "myteam";
    private static final String PIPELINE_NAME   = "my-pipeline";
    private static final String PIPELINE_PATH   = FOLDER_NAME + "/" + PIPELINE_NAME;
    private static final String PAYMENTS_NAME   = "payments-pipeline";
    private static final String PAYMENTS_PATH   = FOLDER_NAME + "/" + PAYMENTS_NAME;
    private static final String DB_CRED_ID      = "db-cred";
    private static final String TOKEN_CRED_ID   = "token-cred";
    private static final String GLOBAL_FIRST_ID = "GlobalSecretFirst";

    // nested hierarchy: folderA/folderB
    private static final String OUTER_FOLDER_NAME = "folderA";
    private static final String INNER_FOLDER_NAME = "folderB";
    private static final String INNER_FOLDER_PATH = OUTER_FOLDER_NAME + "/" + INNER_FOLDER_NAME;
    private static final String NESTED_PIPE_NAME  = "nested-pipeline";
    private static final String NESTED_PIPE_PATH  = INNER_FOLDER_PATH + "/" + NESTED_PIPE_NAME;
    private static final String NESTED_CRED_ID    = "nested-cred";
    private static final String API_TOKEN_ID      = "api-token";
    private static final String ENV_CRED_ID       = "env-cred";

    private EncryptionService       encryptionService;
    private DiscoExporterConfiguration config;

    @Before
    public void setUp() throws Exception {
        // ── folder ───────────────────────────────────────────────────────────────
        Folder myteam = j.jenkins.createProject(Folder.class, FOLDER_NAME);

        // ── credentials stored inside the folder ─────────────────────────────────
        FolderCredentialsProvider.FolderCredentialsProperty prop =
                myteam.getProperties().get(FolderCredentialsProvider.FolderCredentialsProperty.class);
        if (prop == null) {
            prop = new FolderCredentialsProvider.FolderCredentialsProperty(new ArrayList<>());
            myteam.addProperty(prop);
        }
        CredentialsStore store = (CredentialsStore) prop.getStore();
        store.addCredentials(Domain.global(),
                new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, DB_CRED_ID, "DB credentials",
                        "admin", "p@ssw0rd"));
        store.addCredentials(Domain.global(),
                new StringCredentialsImpl(
                        CredentialsScope.GLOBAL, TOKEN_CRED_ID, "API token",
                        Secret.fromString("my-api-token")));
        store.addCredentials(Domain.global(),
                new StringCredentialsImpl(
                        CredentialsScope.GLOBAL, GLOBAL_FIRST_ID, "DisCo payments secret",
                        Secret.fromString("payments-secret-value")));

        // ── pipeline that uses both folder credentials via withCredentials ────────
        WorkflowJob pipeline = myteam.createProject(WorkflowJob.class, PIPELINE_NAME);
        pipeline.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  withCredentials([usernamePassword(credentialsId: '" + DB_CRED_ID + "', " +
                "      usernameVariable: 'U', passwordVariable: 'P')]) {\n" +
                "    echo 'using db'\n" +
                "  }\n" +
                "  withCredentials([string(credentialsId: '" + TOKEN_CRED_ID + "', variable: 'TOKEN')]) {\n" +
                "    echo 'using token'\n" +
                "  }\n" +
                "}", true));

        // ── payments pipeline — uses GlobalSecretFirst via stage+withCredentials ──
        WorkflowJob paymentsPipeline = myteam.createProject(WorkflowJob.class, PAYMENTS_NAME);
        paymentsPipeline.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "    stage('Use DisCo payments folder secrets') {\n" +
                "        withCredentials([\n" +
                "            string(credentialsId: '" + GLOBAL_FIRST_ID + "', variable: 'DISCO_SECRET')\n" +
                "        ]) {\n" +
                "            sh '''#!/bin/bash\n" +
                "            set -euo pipefail\n" +
                "            test -n \"$DISCO_SECRET\"\n" +
                "            echo \"DisCo payments folder credential binding succeeded\"\n" +
                "            '''\n" +
                "        }\n" +
                "    }\n" +
                "}", true));

        // ── folderA/folderB nested hierarchy ─────────────────────────────────────
        Folder folderA = j.jenkins.createProject(Folder.class, OUTER_FOLDER_NAME);
        Folder folderB = folderA.createProject(Folder.class, INNER_FOLDER_NAME);

        // credentials stored in the inner folder
        FolderCredentialsProvider.FolderCredentialsProperty innerProp =
                folderB.getProperties().get(FolderCredentialsProvider.FolderCredentialsProperty.class);
        if (innerProp == null) {
            innerProp = new FolderCredentialsProvider.FolderCredentialsProperty(new ArrayList<>());
            folderB.addProperty(innerProp);
        }
        CredentialsStore innerStore = (CredentialsStore) innerProp.getStore();

        // credential used via: credentialsId: 'nested-cred'  (colon + single-quote)
        innerStore.addCredentials(Domain.global(),
                new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, NESTED_CRED_ID, "Nested DB creds",
                        "svc-user", "s3cr3t!"));

        // credential used via: credentialsId: "api-token"  (colon + double-quote)
        innerStore.addCredentials(Domain.global(),
                new StringCredentialsImpl(
                        CredentialsScope.GLOBAL, API_TOKEN_ID, "API token",
                        Secret.fromString("tok-abcdef")));

        // credential used via: credentials('env-cred')  (function-call form)
        innerStore.addCredentials(Domain.global(),
                new StringCredentialsImpl(
                        CredentialsScope.GLOBAL, ENV_CRED_ID, "Env secret",
                        Secret.fromString("env-value-xyz")));

        // pipeline inside folderA/folderB using all three credentials, each in a
        // syntactically distinct form so the regex is exercised end-to-end
        WorkflowJob nestedPipeline = folderB.createProject(WorkflowJob.class, NESTED_PIPE_NAME);
        nestedPipeline.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                // form 1: colon + single-quote
                "  withCredentials([usernamePassword(credentialsId: '" + NESTED_CRED_ID + "', " +
                "      usernameVariable: 'U', passwordVariable: 'P')]) {\n" +
                "    echo 'nested-cred used'\n" +
                "  }\n" +
                // form 2: colon + double-quote
                "  withCredentials([string(credentialsId: \"" + API_TOKEN_ID + "\", variable: 'TOKEN')]) {\n" +
                "    echo 'api-token used'\n" +
                "  }\n" +
                // form 3: function-call credentials('id')
                "  withCredentials([string(credentials('" + ENV_CRED_ID + "'), variable: 'ENV')]) {\n" +
                "    echo 'env-cred used'\n" +
                "  }\n" +
                "}", true));

        // ── mocks ─────────────────────────────────────────────────────────────────
        encryptionService = mock(EncryptionService.class);
        when(encryptionService.encryptValue(anyString())).thenAnswer(inv -> "ENC[" + inv.getArgument(0) + "]");
        when(encryptionService.getSelectedKid()).thenReturn("mock-kid-001");

        config = mock(DiscoExporterConfiguration.class);
        when(config.isExportSecretValues()).thenReturn(false);
        when(config.getSubdomain()).thenReturn("test-tenant");
        when(config.getJwksUri()).thenReturn(j.getURL() + "jwtauth/conjur-jwk-set");
    }

    // ── whereUsed — Java model level ─────────────────────────────────────────────

    @Test
    public void whereUsed_dbCred_containsPipelinePath() throws Exception {
        UsageTracker tracker = new UsageTracker();
        tracker.scan();
        assertThat(tracker.getWhereUsed(DB_CRED_ID)).contains(PIPELINE_PATH);
    }

    @Test
    public void whereUsed_tokenCred_containsPipelinePath() throws Exception {
        UsageTracker tracker = new UsageTracker();
        tracker.scan();
        assertThat(tracker.getWhereUsed(TOKEN_CRED_ID)).contains(PIPELINE_PATH);
    }

    // ── whereUsed — JSON snapshot level ──────────────────────────────────────────

    @Test
    public void jsonPayload_whereUsed_dbCred_containsPipelinePath() throws Exception {
        JsonObject snapshot = buildSnapshotJson();
        JsonObject dbCredJson = findCred(snapshot.getAsJsonArray("credentials"), DB_CRED_ID);

        assertThat(dbCredJson)
                .as("db-cred must appear in the credentials array")
                .isNotNull();

        List<String> whereUsed = toStringList(dbCredJson.getAsJsonArray("whereUsed"));
        assertThat(whereUsed)
                .as("db-cred whereUsed must contain the pipeline path '%s'", PIPELINE_PATH)
                .contains(PIPELINE_PATH);
    }

    @Test
    public void jsonPayload_whereUsed_tokenCred_containsPipelinePath() throws Exception {
        JsonObject snapshot = buildSnapshotJson();
        JsonObject tokenCredJson = findCred(snapshot.getAsJsonArray("credentials"), TOKEN_CRED_ID);

        assertThat(tokenCredJson)
                .as("token-cred must appear in the credentials array")
                .isNotNull();

        List<String> whereUsed = toStringList(tokenCredJson.getAsJsonArray("whereUsed"));
        assertThat(whereUsed)
                .as("token-cred whereUsed must contain the pipeline path '%s'", PIPELINE_PATH)
                .contains(PIPELINE_PATH);
    }

    @Test
    public void jsonPayload_whereUsed_pipelinePathIsNotDuplicated() throws Exception {
        JsonObject snapshot = buildSnapshotJson();
        JsonObject dbCredJson = findCred(snapshot.getAsJsonArray("credentials"), DB_CRED_ID);
        assertThat(dbCredJson).isNotNull();

        List<String> whereUsed = toStringList(dbCredJson.getAsJsonArray("whereUsed"));
        long count = whereUsed.stream().filter(PIPELINE_PATH::equals).count();
        assertThat(count)
                .as("pipeline path must appear exactly once in whereUsed (no duplicates)")
                .isEqualTo(1);
    }

    // ── payments pipeline — GlobalSecretFirst via stage+withCredentials ──────────

    @Test
    public void whereUsed_globalSecretFirst_containsPaymentsPipelinePath() throws Exception {
        UsageTracker tracker = new UsageTracker();
        tracker.scan();
        assertThat(tracker.getWhereUsed(GLOBAL_FIRST_ID)).contains(PAYMENTS_PATH);
    }

    @Test
    public void whereUsed_globalSecretFirst_notInOtherPipeline() throws Exception {
        UsageTracker tracker = new UsageTracker();
        tracker.scan();
        assertThat(tracker.getWhereUsed(GLOBAL_FIRST_ID)).doesNotContain(PIPELINE_PATH);
    }

    @Test
    public void jsonPayload_whereUsed_globalSecretFirst_containsPaymentsPipelinePath() throws Exception {
        JsonObject snapshot = buildSnapshotJson();
        JsonObject credJson = findCred(snapshot.getAsJsonArray("credentials"), GLOBAL_FIRST_ID);

        assertThat(credJson)
                .as("GlobalSecretFirst must appear in the credentials array")
                .isNotNull();

        List<String> whereUsed = toStringList(credJson.getAsJsonArray("whereUsed"));
        assertThat(whereUsed)
                .as("GlobalSecretFirst whereUsed must contain the payments pipeline path '%s'", PAYMENTS_PATH)
                .contains(PAYMENTS_PATH);
    }

    @Test
    public void jsonPayload_whereUsed_globalSecretFirst_notDuplicated() throws Exception {
        JsonObject snapshot = buildSnapshotJson();
        JsonObject credJson = findCred(snapshot.getAsJsonArray("credentials"), GLOBAL_FIRST_ID);
        assertThat(credJson).isNotNull();

        long count = toStringList(credJson.getAsJsonArray("whereUsed")).stream()
                .filter(PAYMENTS_PATH::equals).count();
        assertThat(count)
                .as("payments pipeline path must appear exactly once in whereUsed")
                .isEqualTo(1);
    }

    @Test
    public void jsonPayload_paymentsPipeline_appearsInJobsList() throws Exception {
        JsonObject snapshot = buildSnapshotJson();
        List<String> jobPaths = new ArrayList<>();
        snapshot.getAsJsonArray("jobs").forEach(e ->
                jobPaths.add(e.getAsJsonObject().get("path").getAsString()));
        assertThat(jobPaths).contains(PAYMENTS_PATH);
    }

    // ── folderA/folderB nested hierarchy tests ────────────────────────────────────

    // -- UsageTracker level --

    @Test
    public void nested_whereUsed_nestedCred_containsNestedPipelinePath() throws Exception {
        UsageTracker tracker = new UsageTracker();
        tracker.scan();
        assertThat(tracker.getWhereUsed(NESTED_CRED_ID))
                .as("NESTED_CRED_ID (colon+single-quote form) must reference '%s'", NESTED_PIPE_PATH)
                .contains(NESTED_PIPE_PATH);
    }

    @Test
    public void nested_whereUsed_apiToken_containsNestedPipelinePath() throws Exception {
        UsageTracker tracker = new UsageTracker();
        tracker.scan();
        assertThat(tracker.getWhereUsed(API_TOKEN_ID))
                .as("API_TOKEN_ID (colon+double-quote form) must reference '%s'", NESTED_PIPE_PATH)
                .contains(NESTED_PIPE_PATH);
    }

    @Test
    public void nested_whereUsed_envCred_containsNestedPipelinePath() throws Exception {
        UsageTracker tracker = new UsageTracker();
        tracker.scan();
        assertThat(tracker.getWhereUsed(ENV_CRED_ID))
                .as("ENV_CRED_ID (function-call credentials('id') form) must reference '%s'", NESTED_PIPE_PATH)
                .contains(NESTED_PIPE_PATH);
    }

    @Test
    public void nested_whereUsed_nestedCredsNotLeakingToSiblingFolder() throws Exception {
        UsageTracker tracker = new UsageTracker();
        tracker.scan();
        // credentials in folderA/folderB must not appear in myteam pipelines
        assertThat(tracker.getWhereUsed(NESTED_CRED_ID)).doesNotContain(PIPELINE_PATH);
        assertThat(tracker.getWhereUsed(NESTED_CRED_ID)).doesNotContain(PAYMENTS_PATH);
    }

    // -- JSON snapshot level --

    @Test
    public void nested_jsonPayload_nestedCred_containsNestedPipelinePath() throws Exception {
        JsonObject snapshot = buildSnapshotJson();
        JsonObject credJson = findCred(snapshot.getAsJsonArray("credentials"), NESTED_CRED_ID);

        assertThat(credJson)
                .as("nested-cred must appear in the credentials array")
                .isNotNull();

        List<String> whereUsed = toStringList(credJson.getAsJsonArray("whereUsed"));
        assertThat(whereUsed)
                .as("nested-cred whereUsed must contain '%s'", NESTED_PIPE_PATH)
                .contains(NESTED_PIPE_PATH);
    }

    @Test
    public void nested_jsonPayload_apiToken_containsNestedPipelinePath() throws Exception {
        JsonObject snapshot = buildSnapshotJson();
        JsonObject credJson = findCred(snapshot.getAsJsonArray("credentials"), API_TOKEN_ID);

        assertThat(credJson)
                .as("api-token must appear in the credentials array")
                .isNotNull();

        List<String> whereUsed = toStringList(credJson.getAsJsonArray("whereUsed"));
        assertThat(whereUsed)
                .as("api-token whereUsed must contain '%s'", NESTED_PIPE_PATH)
                .contains(NESTED_PIPE_PATH);
    }

    @Test
    public void nested_jsonPayload_envCred_containsNestedPipelinePath() throws Exception {
        JsonObject snapshot = buildSnapshotJson();
        JsonObject credJson = findCred(snapshot.getAsJsonArray("credentials"), ENV_CRED_ID);

        assertThat(credJson)
                .as("env-cred must appear in the credentials array")
                .isNotNull();

        List<String> whereUsed = toStringList(credJson.getAsJsonArray("whereUsed"));
        assertThat(whereUsed)
                .as("env-cred whereUsed must contain '%s'", NESTED_PIPE_PATH)
                .contains(NESTED_PIPE_PATH);
    }

    @Test
    public void nested_jsonPayload_credLocation_isInnerFolderPath() throws Exception {
        JsonObject snapshot = buildSnapshotJson();
        JsonObject credJson = findCred(snapshot.getAsJsonArray("credentials"), NESTED_CRED_ID);

        assertThat(credJson).isNotNull();
        String location = credJson.get("location").getAsString();
        assertThat(location)
                .as("nested-cred location must be the inner folder path '%s'", INNER_FOLDER_PATH)
                .isEqualTo(INNER_FOLDER_PATH);
    }

    @Test
    public void nested_jsonPayload_innerFolderAppearsInFoldersList() throws Exception {
        JsonObject snapshot = buildSnapshotJson();
        List<String> folderPaths = new ArrayList<>();
        snapshot.getAsJsonArray("folders").forEach(e ->
                folderPaths.add(e.getAsJsonObject().get("path").getAsString()));
        assertThat(folderPaths)
                .as("folders list must contain the inner folder path '%s'", INNER_FOLDER_PATH)
                .contains(INNER_FOLDER_PATH);
    }

    @Test
    public void nested_jsonPayload_nestedPipelineAppearsInJobsList() throws Exception {
        JsonObject snapshot = buildSnapshotJson();
        List<String> jobPaths = new ArrayList<>();
        snapshot.getAsJsonArray("jobs").forEach(e ->
                jobPaths.add(e.getAsJsonObject().get("path").getAsString()));
        assertThat(jobPaths)
                .as("jobs list must contain the nested pipeline path '%s'", NESTED_PIPE_PATH)
                .contains(NESTED_PIPE_PATH);
    }

    @Test
    public void nested_jsonPayload_whereUsed_notDuplicated() throws Exception {
        JsonObject snapshot = buildSnapshotJson();
        JsonObject credJson = findCred(snapshot.getAsJsonArray("credentials"), NESTED_CRED_ID);
        assertThat(credJson).isNotNull();

        long count = toStringList(credJson.getAsJsonArray("whereUsed")).stream()
                .filter(NESTED_PIPE_PATH::equals).count();
        assertThat(count)
                .as("nested pipeline path must appear exactly once in whereUsed")
                .isEqualTo(1);
    }

    // ── anonymous-read-access disabled (regression: whereUsed was empty) ─────────
    //
    // Jenkins 2.470+ disables anonymous read access by default.
    // UsageTracker.scan() and DiscoveryOrchestrator.collectJobs/Folders() call
    // getAllItems() which respects the current security context — without
    // ACL.as2(SYSTEM2) they returned empty lists for the anonymous principal,
    // making whereUsed empty for every credential.

    @Test
    public void securityEnabled_whereUsed_dbCred_containsPipelinePath() throws Exception {
        enableSecurityDenyAnonymous();

        UsageTracker tracker = new UsageTracker();
        tracker.scan();
        assertThat(tracker.getWhereUsed(DB_CRED_ID))
                .as("whereUsed must not be empty when anonymous read access is disabled")
                .contains(PIPELINE_PATH);
    }

    @Test
    public void securityEnabled_whereUsed_globalSecretFirst_containsPaymentsPipelinePath() throws Exception {
        enableSecurityDenyAnonymous();

        UsageTracker tracker = new UsageTracker();
        tracker.scan();
        assertThat(tracker.getWhereUsed(GLOBAL_FIRST_ID))
                .as("whereUsed must not be empty when anonymous read access is disabled")
                .contains(PAYMENTS_PATH);
    }

    @Test
    public void securityEnabled_jsonPayload_whereUsed_dbCred_containsPipelinePath() throws Exception {
        enableSecurityDenyAnonymous();

        JsonObject snapshot = buildSnapshotJson();
        JsonObject credJson = findCred(snapshot.getAsJsonArray("credentials"), DB_CRED_ID);

        assertThat(credJson).isNotNull();
        List<String> whereUsed = toStringList(credJson.getAsJsonArray("whereUsed"));
        assertThat(whereUsed)
                .as("JSON whereUsed must contain pipeline path when anonymous read is disabled")
                .contains(PIPELINE_PATH);
    }

    @Test
    public void securityEnabled_jsonPayload_foldersListNotEmpty() throws Exception {
        enableSecurityDenyAnonymous();

        JsonObject snapshot = buildSnapshotJson();
        // folders array always has at least the GlobalConfiguration sentinel plus myteam
        List<String> folderPaths = new ArrayList<>();
        snapshot.getAsJsonArray("folders").forEach(e ->
                folderPaths.add(e.getAsJsonObject().get("path").getAsString()));
        assertThat(folderPaths)
                .as("folders list must not be empty when anonymous read access is disabled")
                .contains(FOLDER_NAME);
    }

    @Test
    public void securityEnabled_jsonPayload_jobsListNotEmpty() throws Exception {
        enableSecurityDenyAnonymous();

        JsonObject snapshot = buildSnapshotJson();
        List<String> jobPaths = new ArrayList<>();
        snapshot.getAsJsonArray("jobs").forEach(e ->
                jobPaths.add(e.getAsJsonObject().get("path").getAsString()));
        assertThat(jobPaths)
                .as("jobs list must not be empty when anonymous read access is disabled")
                .contains(PIPELINE_PATH);
    }

    /**
     * Activates a security realm with "deny anonymous read access" — the exact
     * configuration that caused empty whereUsed on newer Jenkins versions.
     */
    private void enableSecurityDenyAnonymous() throws Exception {
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(realm);
        FullControlOnceLoggedInAuthorizationStrategy strategy = new FullControlOnceLoggedInAuthorizationStrategy();
        // allowAnonymousRead=false mirrors "Allow anonymous read access" OFF in the UI
        strategy.setAllowAnonymousRead(false);
        j.jenkins.setAuthorizationStrategy(strategy);
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private JsonObject buildSnapshotJson() throws Exception {
        UsageTracker tracker = new UsageTracker();
        tracker.scan();

        List<org.conjur.jenkins.disco.model.CredentialRecord> credentials =
                new CredentialsDictionaryMapper(config, encryptionService, tracker).mapAll();

        DiscoveryOrchestrator orch = new DiscoveryOrchestrator(true) {};
        List<JenkinsObject> folders = orch.collectFolders();
        List<JenkinsObject> jobs    = orch.collectJobs();

        DiscoverySnapshot snapshot = new DiscoverySnapshot();
        snapshot.setJenkinsId(j.jenkins.getLegacyInstanceId());
        snapshot.setOriginStoreId(j.jenkins.getLegacyInstanceId());
        snapshot.setDataSourceType("JenkinsDiscoveryPlugin");
        snapshot.setVersion("test");
        snapshot.setSnapshotId(java.util.UUID.randomUUID().toString());
        snapshot.setTimestamp(java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now()));
        snapshot.setDisCoConfig(DiscoExporterConfigurationSnapshot.from(config));
        snapshot.setKid(encryptionService.getSelectedKid());
        OpenIdConfiguration oidc = new OpenIdConfiguration();
        oidc.setIssuer(org.conjur.jenkins.api.ConjurAPIUtils.getJenkinsIssuer());
        oidc.setJwksUri(config.getJwksUri());
        snapshot.setOpenIdConfiguration(oidc);
        snapshot.setCredentials(credentials);
        snapshot.setFolders(folders);
        snapshot.setJobs(jobs);

        return new Gson().toJsonTree(snapshot).getAsJsonObject();
    }

    private JsonObject findCred(JsonArray credentials, String id) {
        for (com.google.gson.JsonElement el : credentials) {
            JsonObject o = el.getAsJsonObject();
            if (id.equals(o.get("credentialId").getAsString())) return o;
        }
        return null;
    }

    private List<String> toStringList(JsonArray array) {
        List<String> result = new ArrayList<>();
        if (array != null) array.forEach(e -> result.add(e.getAsString()));
        return result;
    }
}
