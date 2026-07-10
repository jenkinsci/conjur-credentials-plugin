package org.conjur.jenkins.disco;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import hudson.model.FreeStyleProject;
import hudson.util.Secret;
import org.conjur.jenkins.conjursecrets.ConjurSecretStringCredentialsImpl;
import org.conjur.jenkins.conjursecrets.ConjurSecretUsernameCredentialsImpl;
import org.conjur.jenkins.disco.config.DiscoExporterConfiguration;
import org.conjur.jenkins.disco.discovery.CredentialsDictionaryMapper;
import org.conjur.jenkins.disco.discovery.DiscoveryOrchestrator;
import org.conjur.jenkins.disco.discovery.UsageTracker;
import org.conjur.jenkins.disco.model.*;
import org.conjur.jenkins.disco.security.EncryptionService;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Integration tests that run against a live in-process Jenkins instance
 * (JenkinsRule).  A real folder/job hierarchy is created and credentials are
 * stored in the matching Jenkins credential stores.  EncryptionService is
 * mocked so we never need a real DisCo endpoint, but every other component
 * (CredentialsDictionaryMapper, UsageTracker, DiscoveryOrchestrator helpers)
 * exercises the real Jenkins API.
 *
 * Hierarchy under test:
 *
 *   Jenkins (global)
 *   └─ team/                          (Folder)
 *       ├─ team/finance/              (Folder)
 *       │   ├─ team/finance/deploy    (WorkflowJob — uses finance-db + global-secret)
 *       │   └─ team/finance/report   (WorkflowJob — uses finance-db)
 *       └─ team/build                (FreeStyleProject — no credentials)
 *
 *   Credentials:
 *     global  : global-secret   (StringCredentials)
 *     team/finance : finance-db (UsernamePasswordCredentials, username=dbuser)
 */
@Ignore
public class DiscoJenkinsIntegrationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static final String GLOBAL_SECRET_ID        = "global-secret";
    private static final String FINANCE_DB_ID            = "finance-db";
    private static final String CONJUR_STRING_ID         = "conjur-string-cred";
    private static final String CONJUR_USERNAME_ID       = "conjur-username-cred";

    private EncryptionService encryptionService;
    private DiscoExporterConfiguration config;

    @Before
    public void setUp() throws Exception {
        // ── credentials ──────────────────────────────────────────────────────────

        SystemCredentialsProvider.getInstance().getCredentials().add(
                new StringCredentialsImpl(
                        CredentialsScope.GLOBAL,
                        GLOBAL_SECRET_ID,
                        "A global string secret",
                        Secret.fromString("top-secret-value")));
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new ConjurSecretStringCredentialsImpl(
                        CredentialsScope.GLOBAL,
                        CONJUR_STRING_ID,
                        "conjur/path/to/string",
                        "A Conjur string credential"));
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new ConjurSecretUsernameCredentialsImpl(
                        CredentialsScope.GLOBAL,
                        CONJUR_USERNAME_ID,
                        "svc-user",
                        "conjur/path/to/username",
                        "A Conjur username credential"));
        SystemCredentialsProvider.getInstance().save();

        Folder team = j.jenkins.createProject(Folder.class, "team");
        Folder finance = team.createProject(Folder.class, "finance");

        FolderCredentialsProvider.FolderCredentialsProperty prop =
                finance.getProperties().get(FolderCredentialsProvider.FolderCredentialsProperty.class);
        if (prop == null) {
            prop = new FolderCredentialsProvider.FolderCredentialsProperty(new java.util.ArrayList<>());
            finance.addProperty(prop);
        }
        ((CredentialsStore) prop.getStore()).addCredentials(Domain.global(),
                new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, FINANCE_DB_ID, "Finance DB credentials",
                        "dbuser", "s3cr3t"));

        // ── jobs ─────────────────────────────────────────────────────────────────

        WorkflowJob deploy = finance.createProject(WorkflowJob.class, "deploy");
        deploy.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  withCredentials([string(credentialsId: '" + GLOBAL_SECRET_ID + "', variable: 'TOKEN')]) {}\n" +
                "  withCredentials([usernamePassword(credentialsId: '" + FINANCE_DB_ID + "', " +
                "      usernameVariable: 'U', passwordVariable: 'P')]) {}\n" +
                "}", true));

        WorkflowJob report = finance.createProject(WorkflowJob.class, "report");
        report.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  withCredentials([usernamePassword(credentialsId: '" + FINANCE_DB_ID + "', " +
                "      usernameVariable: 'U', passwordVariable: 'P')]) {}\n" +
                "}", true));

        team.createProject(FreeStyleProject.class, "build");

        // ── mocks ─────────────────────────────────────────────────────────────────

        encryptionService = mock(EncryptionService.class);
        when(encryptionService.encryptValue(anyString())).thenAnswer(inv -> "ENC[" + inv.getArgument(0) + "]");
        when(encryptionService.getSelectedKid()).thenReturn("test-kid-001");

        config = mock(DiscoExporterConfiguration.class);
        when(config.isExportSecretValues()).thenReturn(false);
        when(config.getSubdomain()).thenReturn("acme");
        when(config.getJwksUri()).thenReturn(j.getURL() + "jwtauth/conjur-jwk-set");
    }

    // ── CredentialsDictionaryMapper — full scan ──────────────────────────────────

    @Test
    public void mapper_findsGlobalCredential() throws Exception {
        List<CredentialRecord> records = runMapper(false);
        assertThat(records).extracting(CredentialRecord::getCredentialId).contains(GLOBAL_SECRET_ID);
    }

    @Test
    public void mapper_findsFolderCredential() throws Exception {
        List<CredentialRecord> records = runMapper(false);
        assertThat(records).extracting(CredentialRecord::getCredentialId).contains(FINANCE_DB_ID);
    }

    @Test
    public void mapper_globalCred_locationIsGlobal() throws Exception {
        CredentialRecord rec = findRecord(runMapper(false), GLOBAL_SECRET_ID);
        assertThat(rec.getLocation()).isEqualTo("Global");
    }

    @Test
    public void mapper_globalCred_storeProviderIsSystem() throws Exception {
        CredentialRecord rec = findRecord(runMapper(false), GLOBAL_SECRET_ID);
        assertThat(rec.getAdditionalData()).containsEntry("storeProvider", "SystemCredentialsProvider");
        assertThat(rec.getAdditionalData()).containsEntry("scope", "global");
    }

    @Test
    public void mapper_folderCred_locationIsFinanceFolder() throws Exception {
        CredentialRecord rec = findRecord(runMapper(false), FINANCE_DB_ID);
        assertThat(rec.getLocation()).isEqualTo("team/finance");
    }

    @Test
    public void mapper_folderCred_storeProviderIsFolder() throws Exception {
        CredentialRecord rec = findRecord(runMapper(false), FINANCE_DB_ID);
        assertThat(rec.getAdditionalData()).containsEntry("storeProvider", "FolderCredentialsProvider");
        assertThat(rec.getAdditionalData()).containsEntry("scope", "folder");
        assertThat(rec.getAdditionalData()).containsEntry("scopePath", "team/finance");
    }

    @Test
    public void mapper_folderCred_originIdContainsFolderScopeAndId() throws Exception {
        CredentialRecord rec = findRecord(runMapper(false), FINANCE_DB_ID);
        assertThat(rec.getOriginId()).isEqualTo("team/finance:" + FINANCE_DB_ID);
    }

    @Test
    public void mapper_noDuplicateOriginIds() throws Exception {
        List<CredentialRecord> records = runMapper(false);
        List<String> originIds = records.stream().map(CredentialRecord::getOriginId).toList();
        assertThat(originIds).doesNotHaveDuplicates();
    }

    // ── conjurization — non-Conjur credentials have no conjurization block ────────

    @Test
    public void mapper_globalCred_conjurizationIsNullForPlainJenkins() throws Exception {
        CredentialRecord rec = findRecord(runMapper(false), GLOBAL_SECRET_ID);
        assertThat(rec.getConjurization())
                .as("plain Jenkins StringCredentials must not have a conjurization block")
                .isNull();
    }

    @Test
    public void mapper_folderCred_conjurizationIsNullForPlainJenkins() throws Exception {
        CredentialRecord rec = findRecord(runMapper(false), FINANCE_DB_ID);
        assertThat(rec.getConjurization())
                .as("plain Jenkins UsernamePasswordCredentials must not have a conjurization block")
                .isNull();
    }

    // ── conjurization — Conjur credentials have a populated conjurization block ──

    @Test
    public void mapper_conjurStringCred_conjurizationTypeIsStringcredential() throws Exception {
        CredentialRecord rec = findRecord(runMapper(false), CONJUR_STRING_ID);
        assertThat(rec.getConjurization())
                .containsEntry("variable:annotation:jenkins_credential_type", "stringcredential");
    }

    @Test
    public void mapper_conjurStringCred_conjurizationValueIsReferenceKey() throws Exception {
        CredentialRecord rec = findRecord(runMapper(false), CONJUR_STRING_ID);
        assertThat(rec.getConjurization()).containsKey("variable:value");
        assertThat(rec.getConjurization().get("variable:value")).isNotBlank();
    }

    @Test
    public void mapper_conjurStringCred_noDoubleMapping() throws Exception {
        CredentialRecord rec = findRecord(runMapper(false), CONJUR_STRING_ID);
        assertThat(rec.getConjurization())
                .doesNotContainKey("variable:annotation:jenkins_credential_type_alt");
    }

    @Test
    public void mapper_conjurUsernameCred_conjurizationTypeIsUsernamecredential() throws Exception {
        CredentialRecord rec = findRecord(runMapper(false), CONJUR_USERNAME_ID);
        assertThat(rec.getConjurization())
                .containsEntry("variable:annotation:jenkins_credential_type", "usernamecredential");
    }

    @Test
    public void mapper_conjurUsernameCred_conjurizationUsernameAnnotationPresent() throws Exception {
        CredentialRecord rec = findRecord(runMapper(false), CONJUR_USERNAME_ID);
        assertThat(rec.getConjurization())
                .containsEntry("variable:annotation:jenkins_credential_username", "{{username}}");
    }

    @Test
    public void mapper_conjurUsernameCred_conjurizationValueIsPasswordReferenceKey() throws Exception {
        CredentialRecord rec = findRecord(runMapper(false), CONJUR_USERNAME_ID);
        assertThat(rec.getConjurization()).containsEntry("variable:value", "{{password}}");
    }

    @Test
    public void mapper_conjurUsernameCred_conjurizationHasStringcredentialAlt() throws Exception {
        CredentialRecord rec = findRecord(runMapper(false), CONJUR_USERNAME_ID);
        assertThat(rec.getConjurization())
                .containsEntry("variable:annotation:jenkins_credential_type_alt", "stringcredential");
    }

    // ── where-used wiring ────────────────────────────────────────────────────────

    @Test
    public void whereUsed_globalSecret_isReferencedByDeployJob() throws Exception {
        CredentialRecord rec = findRecord(runMapper(false), GLOBAL_SECRET_ID);
        assertThat(rec.getWhereUsed()).contains("team/finance/deploy");
    }

    @Test
    public void whereUsed_globalSecret_notReferencedByReportJob() throws Exception {
        CredentialRecord rec = findRecord(runMapper(false), GLOBAL_SECRET_ID);
        assertThat(rec.getWhereUsed()).doesNotContain("team/finance/report");
    }

    @Test
    public void whereUsed_financeDb_isReferencedByBothPipelineJobs() throws Exception {
        CredentialRecord rec = findRecord(runMapper(false), FINANCE_DB_ID);
        assertThat(rec.getWhereUsed())
                .contains("team/finance/deploy", "team/finance/report");
    }

    @Test
    public void whereUsed_financeDb_notUsedByBuildJob() throws Exception {
        CredentialRecord rec = findRecord(runMapper(false), FINANCE_DB_ID);
        assertThat(rec.getWhereUsed()).doesNotContain("team/build");
    }

    // ── exportSecretValues — values field presence ───────────────────────────────

    @Test
    public void exportSecretValues_true_valuesFieldIsPresent() throws Exception {
        CredentialRecord rec = findRecord(runMapper(true), GLOBAL_SECRET_ID);
        assertThat(rec.getValues()).isNotNull();
    }

    @Test
    public void exportSecretValues_false_valuesFieldIsAbsent() throws Exception {
        runMapper(false).forEach(rec ->
            assertThat(rec.getValues())
                .as("values must be null when exportSecretValues=false for cred %s", rec.getCredentialId())
                .isNull()
        );
    }

    // ── UsageTracker — standalone scan ───────────────────────────────────────────

    @Test
    public void usageTracker_findsDeployJobForGlobalSecret() throws Exception {
        UsageTracker tracker = new UsageTracker();
        tracker.scan();
        assertThat(tracker.getWhereUsed(GLOBAL_SECRET_ID)).contains("team/finance/deploy");
    }

    @Test
    public void usageTracker_findsBothJobsForFinanceDb() throws Exception {
        UsageTracker tracker = new UsageTracker();
        tracker.scan();
        assertThat(tracker.getWhereUsed(FINANCE_DB_ID))
                .contains("team/finance/deploy", "team/finance/report");
    }

    @Test
    public void usageTracker_buildJobHasNoCredentialRefs() throws Exception {
        UsageTracker tracker = new UsageTracker();
        tracker.scan();
        assertThat(tracker.getWhereUsed(GLOBAL_SECRET_ID)).doesNotContain("team/build");
    }

    // ── collectFolders ───────────────────────────────────────────────────────────

    @Test
    public void collectFolders_rootIsFirstEntry() throws Exception {
        List<JenkinsObject> folders = collectFolders();
        assertThat(folders).isNotEmpty();
        JenkinsObject root = folders.get(0);
        assertThat(root.getPath()).isEmpty();
        assertThat(root.getType()).isEqualTo("GlobalConfiguration");
        assertThat(root.getJenkins_pronoun()).isEqualTo("GlobalConfiguration");
    }

    @Test
    public void collectFolders_teamFolderIsPresent() throws Exception {
        assertThat(collectFolders()).extracting(JenkinsObject::getPath).contains("team");
    }

    @Test
    public void collectFolders_financeFolderHasFullHierarchicalPath() throws Exception {
        assertThat(collectFolders()).extracting(JenkinsObject::getPath).contains("team/finance");
    }

    @Test
    public void collectFolders_allHaveNonNullType() throws Exception {
        collectFolders().forEach(obj ->
            assertThat(obj.getType()).as("type must be non-null for path %s", obj.getPath()).isNotNull()
        );
    }

    // ── collectJobs ──────────────────────────────────────────────────────────────

    @Test
    public void collectJobs_deployJobIsPresent() throws Exception {
        JenkinsObject deploy = findObject(collectJobs(), "team/finance/deploy");
        assertThat(deploy).isNotNull();
        assertThat(deploy.getType()).contains("WorkflowJob");
    }

    @Test
    public void collectJobs_buildJobIsFreeStyleProject() throws Exception {
        JenkinsObject build = findObject(collectJobs(), "team/build");
        assertThat(build).isNotNull();
        assertThat(build.getType()).contains("FreeStyleProject");
    }

    @Test
    public void collectJobs_allHaveNonBlankPronoun() throws Exception {
        collectJobs().forEach(obj ->
            assertThat(obj.getJenkins_pronoun())
                .as("pronoun must be non-blank for path %s", obj.getPath())
                .isNotBlank()
        );
    }

    // ── JSON payload round-trip ──────────────────────────────────────────────────

    @Test
    public void jsonPayload_credentialsArrayContainsExpectedIds() throws Exception {
        JsonObject json = new Gson().toJsonTree(buildSnapshot(false)).getAsJsonObject();
        JsonArray creds = json.getAsJsonArray("credentials");

        List<String> ids = new java.util.ArrayList<>();
        for (com.google.gson.JsonElement el : creds) {
            ids.add(el.getAsJsonObject().get("credentialId").getAsString());
        }
        assertThat(ids).contains(GLOBAL_SECRET_ID, FINANCE_DB_ID);
    }

    @Test
    public void jsonPayload_foldersArrayContainsRootAndFolderPaths() throws Exception {
        JsonObject json = new Gson().toJsonTree(buildSnapshot(false)).getAsJsonObject();
        JsonArray folders = json.getAsJsonArray("folders");

        List<String> paths = new java.util.ArrayList<>();
        for (com.google.gson.JsonElement el : folders) {
            paths.add(el.getAsJsonObject().get("path").getAsString());
        }
        assertThat(paths).contains("", "team", "team/finance");
    }

    @Test
    public void jsonPayload_jobsArrayContainsJobPaths() throws Exception {
        JsonObject json = new Gson().toJsonTree(buildSnapshot(false)).getAsJsonObject();
        JsonArray jobs = json.getAsJsonArray("jobs");

        List<String> paths = new java.util.ArrayList<>();
        for (com.google.gson.JsonElement el : jobs) {
            paths.add(el.getAsJsonObject().get("path").getAsString());
        }
        assertThat(paths).contains("team/finance/deploy", "team/finance/report", "team/build");
    }

    @Test
    public void jsonPayload_whereUsed_deployJobAppearsUnderGlobalSecret() throws Exception {
        JsonObject json = new Gson().toJsonTree(buildSnapshot(false)).getAsJsonObject();
        JsonObject globalSecretJson = findCredJson(json.getAsJsonArray("credentials"), GLOBAL_SECRET_ID);
        assertThat(globalSecretJson).isNotNull();

        JsonArray whereUsed = globalSecretJson.getAsJsonArray("whereUsed");
        List<String> pathList = new java.util.ArrayList<>();
        whereUsed.forEach(e -> pathList.add(e.getAsString()));
        assertThat(pathList).contains("team/finance/deploy");
    }

    @Test
    public void jsonPayload_conjurizationValues_areNonBlankStrings() throws Exception {
        JsonObject json = new Gson().toJsonTree(buildSnapshot(false)).getAsJsonObject();
        json.getAsJsonArray("credentials").forEach(el -> {
            JsonObject conjurization = el.getAsJsonObject().getAsJsonObject("conjurization");
            if (conjurization == null) return;
            conjurization.entrySet().forEach(entry -> {
                String key = entry.getKey();
                String val = entry.getValue().getAsString();
                if (key.equals("variable:value")
                        || key.startsWith("variable:value:")
                        || key.equals("variable:annotation:jenkins_credential_username")) {
                    assertThat(val)
                            .as("conjurization key '%s' must have a non-blank value", key)
                            .isNotBlank();
                }
            });
        });
    }

    @Test
    public void jsonPayload_exportSecretValues_true_valuesFieldPresent() throws Exception {
        JsonObject json = new Gson().toJsonTree(buildSnapshot(true)).getAsJsonObject();
        JsonObject globalSecretJson = findCredJson(json.getAsJsonArray("credentials"), GLOBAL_SECRET_ID);
        assertThat(globalSecretJson).isNotNull();
        assertThat(globalSecretJson.has("values")).isTrue();
        assertThat(globalSecretJson.get("values").isJsonNull()).isFalse();
    }

    @Test
    public void jsonPayload_exportSecretValues_false_valuesFieldNull() throws Exception {
        JsonObject json = new Gson().toJsonTree(buildSnapshot(false)).getAsJsonObject();
        json.getAsJsonArray("credentials").forEach(el -> {
            JsonObject cred = el.getAsJsonObject();
            boolean hasNonNullValues = cred.has("values") && !cred.get("values").isJsonNull();
            assertThat(hasNonNullValues)
                    .as("values must be absent/null when exportSecretValues=false for cred %s",
                            cred.get("credentialId").getAsString())
                    .isFalse();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────────

    private List<CredentialRecord> runMapper(boolean exportSecrets) throws Exception {
        when(config.isExportSecretValues()).thenReturn(exportSecrets);
        UsageTracker tracker = new UsageTracker();
        tracker.scan();
        return new CredentialsDictionaryMapper(config, encryptionService, tracker).mapAll();
    }

    private DiscoverySnapshot buildSnapshot(boolean exportSecrets) throws Exception {
        when(config.isExportSecretValues()).thenReturn(exportSecrets);
        UsageTracker tracker = new UsageTracker();
        tracker.scan();

        List<CredentialRecord> credentials =
                new CredentialsDictionaryMapper(config, encryptionService, tracker).mapAll();

        DiscoveryOrchestrator orch = new DiscoveryOrchestrator(true) {};
        List<JenkinsObject> folders = orch.collectFolders();
        List<JenkinsObject> jobs    = orch.collectJobs();

        DiscoverySnapshot snapshot = new DiscoverySnapshot();
        snapshot.setJenkinsId(j.jenkins.getLegacyInstanceId());
        snapshot.setOriginStoreId(j.jenkins.getLegacyInstanceId());
        snapshot.setDataSourceType("JenkinsDiscoveryPlugin");
        snapshot.setVersion("3.1.0-test");
        snapshot.setSnapshotId(java.util.UUID.randomUUID().toString());
        snapshot.setTimestamp(java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now()));
        snapshot.setDisCoConfig(
                org.conjur.jenkins.disco.model.DiscoExporterConfigurationSnapshot.from(config));
        snapshot.setKid(encryptionService.getSelectedKid());
        org.conjur.jenkins.disco.model.OpenIdConfiguration oidc = new org.conjur.jenkins.disco.model.OpenIdConfiguration();
        oidc.setIssuer(org.conjur.jenkins.api.ConjurAPIUtils.getJenkinsIssuer());
        oidc.setJwksUri(config.getJwksUri());
        snapshot.setOpenIdConfiguration(oidc);
        snapshot.setCredentials(credentials);
        snapshot.setFolders(folders);
        snapshot.setJobs(jobs);
        return snapshot;
    }

    private List<JenkinsObject> collectFolders() {
        return new DiscoveryOrchestrator(true) {}.collectFolders();
    }

    private List<JenkinsObject> collectJobs() {
        return new DiscoveryOrchestrator(true) {}.collectJobs();
    }

    private CredentialRecord findRecord(List<CredentialRecord> records, String id) {
        return records.stream().filter(r -> id.equals(r.getCredentialId())).findFirst().orElse(null);
    }

    private JenkinsObject findObject(List<JenkinsObject> objects, String path) {
        return objects.stream().filter(o -> path.equals(o.getPath())).findFirst().orElse(null);
    }

    private JsonObject findCredJson(JsonArray array, String id) {
        for (com.google.gson.JsonElement el : array) {
            JsonObject o = el.getAsJsonObject();
            if (id.equals(o.get("credentialId").getAsString())) return o;
        }
        return null;
    }
}
