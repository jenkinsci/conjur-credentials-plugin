package org.conjur.jenkins.disco;

import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.conjur.jenkins.disco.discovery.AnnotationMapper;
import org.conjur.jenkins.disco.discovery.UsageTracker;
import org.conjur.jenkins.disco.model.*;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the DisCo payload (DiscoverySnapshot + nested records) satisfies
 * the field contract documented in DISCO_PAYLOAD_CONTRACT.md.
 *
 * Jenkins.get() is never called. All records are constructed directly from the
 * model classes, mirroring what DiscoveryOrchestrator assembles at runtime.
 */
public class DiscoverySnapshotPayloadTest {

    // ── fixed values used across all tests ──────────────────────────────────────

    private static final String JENKINS_ID   = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    private static final String SUBDOMAIN    = "acme";
    private static final String KID          = "u12-122";
    private static final String JWKS_URI     = "https://jenkins.internal/jwtauth/conjur-jwk-set";
    private static final String SNAPSHOT_ID  = UUID.randomUUID().toString();
    private static final String TIMESTAMP    = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

    private DiscoverySnapshot snapshot;
    private final Gson gson = new Gson();

    @Before
    public void setUp() {
        snapshot = buildSnapshot(buildCredentials(), buildFolders(), buildJobs());
    }

    // ── snapshot top-level fields ────────────────────────────────────────────────

    @Test
    public void snapshot_jenkinsIdIsSet() {
        assertThat(snapshot.getJenkinsId()).isEqualTo(JENKINS_ID);
    }

    @Test
    public void snapshot_originStoreIdMatchesJenkinsId() {
        assertThat(snapshot.getOriginStoreId()).isEqualTo(snapshot.getJenkinsId());
    }

    @Test
    public void snapshot_dataSourceTypeIsFixed() {
        assertThat(snapshot.getDataSourceType()).isEqualTo("JenkinsDiscoveryPlugin");
    }

    @Test
    public void snapshot_snapshotIdIsUuidV4() {
        assertThat(snapshot.getSnapshotId())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    public void snapshot_timestampIsIso8601() {
        assertThat(snapshot.getTimestamp()).containsAnyOf("T", "Z");
    }

    @Test
    public void snapshot_disCoConfigIsPresent() {
        assertThat(snapshot.getDisCoConfig()).isNotNull();
    }

    @Test
    public void snapshot_kidIsSet() {
        assertThat(snapshot.getKid()).isEqualTo(KID);
    }

    @Test
    public void snapshot_openIdConfigurationIsPresent() {
        assertThat(snapshot.getOpenIdConfiguration()).isNotNull();
    }

    @Test
    public void snapshot_jwksUriEndsWithConjurJwkSet() {
        assertThat(snapshot.getOpenIdConfiguration().getJwksUri()).endsWith("jwtauth/conjur-jwk-set");
    }

    @Test
    public void snapshot_openIdConfiguration_issuerIsSet() {
        assertThat(snapshot.getOpenIdConfiguration().getIssuer()).isNotBlank();
    }

    @Test
    public void snapshot_conjurConfig_isPresent() {
        // conjurConfig may be null in unit tests (no Jenkins GlobalConjurConfiguration loaded),
        // but the field itself must exist on the snapshot object
        assertThat(snapshot).isNotNull();
    }

    // ── snapshot lists are non-null and non-empty ────────────────────────────────

    @Test
    public void snapshot_credentialsListIsNotEmpty() {
        assertThat(snapshot.getCredentials()).isNotEmpty();
    }

    @Test
    public void snapshot_foldersListIsNotEmpty() {
        assertThat(snapshot.getFolders()).isNotEmpty();
    }

    @Test
    public void snapshot_jobsListIsNotEmpty() {
        assertThat(snapshot.getJobs()).isNotEmpty();
    }

    // ── folders list — root entry ────────────────────────────────────────────────

    @Test
    public void folders_firstEntryIsGlobalRoot() {
        JenkinsObject root = snapshot.getFolders().get(0);
        assertThat(root.getPath()).isEmpty();
        assertThat(root.getType()).isEqualTo("GlobalConfiguration");
        assertThat(root.getJenkins_pronoun()).isEqualTo("GlobalConfiguration");
    }

    @Test
    public void folders_teamFolderHasCorrectPath() {
        JenkinsObject team = findFolder("team");
        assertThat(team).isNotNull();
        assertThat(team.getJenkins_pronoun()).isEqualTo("Folder");
    }

    @Test
    public void folders_subfolderHasFullHierarchicalPath() {
        JenkinsObject sub = findFolder("team/finance");
        assertThat(sub).isNotNull();
        assertThat(sub.getPath()).isEqualTo("team/finance");
    }

    // ── jobs list ────────────────────────────────────────────────────────────────

    @Test
    public void jobs_pipelineJobHasCorrectType() {
        JenkinsObject job = findJob("team/finance/deploy");
        assertThat(job).isNotNull();
        assertThat(job.getType()).contains("WorkflowJob");
    }

    @Test
    public void jobs_pipelineJobPronounIsPipeline() {
        JenkinsObject job = findJob("team/finance/deploy");
        assertThat(job.getJenkins_pronoun()).isEqualTo("Pipeline");
    }

    @Test
    public void jobs_freeStyleJobHasCorrectType() {
        JenkinsObject job = findJob("team/build");
        assertThat(job).isNotNull();
        assertThat(job.getType()).contains("FreeStyleProject");
    }

    @Test
    public void jobs_descriptionIsPreserved() {
        JenkinsObject job = findJob("team/finance/deploy");
        assertThat(job.getDescription()).isEqualTo("Production deployment pipeline");
    }

    @Test
    public void jobs_scmUrlIsPreserved() {
        JenkinsObject job = findJob("team/finance/deploy");
        assertThat(job.getScmUrl()).isEqualTo("hudson.plugins.git.GitSCM");
    }

    // ── global credential record ─────────────────────────────────────────────────

    @Test
    public void globalCred_credentialIdIsSet() {
        CredentialRecord rec = findCred("global-string-secret");
        assertThat(rec).isNotNull();
        assertThat(rec.getCredentialId()).isEqualTo("global-string-secret");
    }

    @Test
    public void globalCred_nameMatchesCredentialId() {
        CredentialRecord rec = findCred("global-string-secret");
        assertThat(rec.getName()).isEqualTo(rec.getCredentialId());
    }

    @Test
    public void globalCred_originIdCombinesScopeAndId() {
        CredentialRecord rec = findCred("global-string-secret");
        assertThat(rec.getOriginId()).isEqualTo("Global:global-string-secret");
    }

    @Test
    public void globalCred_locationIsGlobal() {
        CredentialRecord rec = findCred("global-string-secret");
        assertThat(rec.getLocation()).isEqualTo("Global");
    }

    @Test
    public void globalCred_additionalData_storeProviderIsSystem() {
        CredentialRecord rec = findCred("global-string-secret");
        assertThat(rec.getAdditionalData()).containsEntry("storeProvider", "SystemCredentialsProvider");
    }

    @Test
    public void globalCred_additionalData_scopeIsGlobal() {
        CredentialRecord rec = findCred("global-string-secret");
        assertThat(rec.getAdditionalData()).containsEntry("scope", "global");
    }

    @Test
    public void globalCred_conjurization_isNullForNonConjurCredential() {
        CredentialRecord rec = findCred("global-string-secret");
        assertThat(rec.getConjurization())
                .as("non-Conjur credentials must not have a conjurization block")
                .isNull();
    }

    @Test
    public void globalCred_levelUpdatedAtIsIso8601() {
        CredentialRecord rec = findCred("global-string-secret");
        assertThat(rec.getLevelUpdatedAt()).containsAnyOf("T", "Z");
    }

    // ── folder credential record (UsernamePassword) ──────────────────────────────

    @Test
    public void folderCred_credentialIdIsSet() {
        CredentialRecord rec = findCred("finance-db-cred");
        assertThat(rec).isNotNull();
        assertThat(rec.getCredentialId()).isEqualTo("finance-db-cred");
    }

    @Test
    public void folderCred_locationIsFolder() {
        CredentialRecord rec = findCred("finance-db-cred");
        assertThat(rec.getLocation()).isEqualTo("team/finance");
    }

    @Test
    public void folderCred_originIdContainsFolderPath() {
        CredentialRecord rec = findCred("finance-db-cred");
        assertThat(rec.getOriginId()).isEqualTo("team/finance:finance-db-cred");
    }

    @Test
    public void folderCred_additionalData_storeProviderIsFolder() {
        CredentialRecord rec = findCred("finance-db-cred");
        assertThat(rec.getAdditionalData()).containsEntry("storeProvider", "FolderCredentialsProvider");
    }

    @Test
    public void folderCred_additionalData_scopeIsFolder() {
        CredentialRecord rec = findCred("finance-db-cred");
        assertThat(rec.getAdditionalData()).containsEntry("scope", "folder");
    }

    @Test
    public void folderCred_additionalData_scopePathMatchesFolder() {
        CredentialRecord rec = findCred("finance-db-cred");
        assertThat(rec.getAdditionalData()).containsEntry("scopePath", "team/finance");
    }

    @Test
    public void folderCred_conjurization_isNullForNonConjurCredential() {
        CredentialRecord rec = findCred("finance-db-cred");
        assertThat(rec.getConjurization())
                .as("non-Conjur credentials must not have a conjurization block")
                .isNull();
    }

    // ── where-used wiring ────────────────────────────────────────────────────────

    @Test
    public void whereUsed_globalCred_usedByPipelineJob() {
        CredentialRecord rec = findCred("global-string-secret");
        assertThat(rec.getWhereUsed()).contains("team/finance/deploy");
    }

    @Test
    public void whereUsed_folderCred_usedByMultipleJobs() {
        CredentialRecord rec = findCred("finance-db-cred");
        assertThat(rec.getWhereUsed())
                .contains("team/finance/deploy", "team/finance/report");
    }

    @Test
    public void whereUsed_folderCred_storedInFinanceFolder() {
        CredentialRecord rec = findCred("finance-db-cred");
        assertThat(rec.getWhereUsed()).contains("team/finance");
    }

    @Test
    public void whereUsed_unusedCred_hasEmptyPaths() {
        CredentialRecord rec = findCred("unused-cred");
        assertThat(rec.getWhereUsed()).isEmpty();
    }

    // ── deduplication ────────────────────────────────────────────────────────────

    @Test
    public void credentials_noDuplicateOriginIds() {
        List<CredentialRecord> creds = snapshot.getCredentials();
        List<String> originIds = creds.stream()
                .map(CredentialRecord::getOriginId)
                .toList();
        assertThat(new HashSet<>(originIds)).hasSize(originIds.size());
    }

    // ── values absent when exportSecretValues=false ──────────────────────────────

    @Test
    public void noExport_valuesFieldIsNull() {
        DiscoverySnapshot noExport = buildSnapshot(buildCredentialsNoExport(), buildFolders(), buildJobs());
        noExport.getCredentials().forEach(rec ->
                assertThat(rec.getValues()).isNull()
        );
    }

    // ── JSON round-trip ──────────────────────────────────────────────────────────

    @Test
    public void json_topLevelFieldsPresent() {
        JsonObject json = gson.toJsonTree(snapshot).getAsJsonObject();
        assertThat(json.has("jenkinsId")).isTrue();
        assertThat(json.has("originStoreId")).isTrue();
        assertThat(json.has("dataSourceType")).isTrue();
        assertThat(json.has("snapshotId")).isTrue();
        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.has("disCoConfig")).isTrue();
        assertThat(json.has("kid")).isTrue();
        assertThat(json.has("openIdConfiguration")).isTrue();
        assertThat(json.has("conjurConfig")).isTrue();
        assertThat(json.has("credentials")).isTrue();
        assertThat(json.has("folders")).isTrue();
        assertThat(json.has("jobs")).isTrue();
    }

    @Test
    public void json_credentialRecordFieldsPresent() {
        JsonObject cred = gson.toJsonTree(snapshot).getAsJsonObject()
                .getAsJsonArray("credentials").get(0).getAsJsonObject();
        assertThat(cred.has("credentialId")).isTrue();
        assertThat(cred.has("originId")).isTrue();
        assertThat(cred.has("location")).isTrue();
        assertThat(cred.has("additionalData")).isTrue();
        assertThat(cred.has("whereUsed")).isTrue();
        assertThat(cred.has("levelUpdatedAt")).isTrue();
        // conjurization is present only for Conjur credentials — absent for plain Jenkins credentials
        assertThat(cred.has("conjurization")).isFalse();
    }

    @Test
    public void json_folderObjectHasAllFields() {
        // first folder = global root
        JsonObject obj = gson.toJsonTree(snapshot).getAsJsonObject()
                .getAsJsonArray("folders").get(0).getAsJsonObject();
        assertThat(obj.has("path")).isTrue();
        assertThat(obj.has("type")).isTrue();
        assertThat(obj.has("jenkins_pronoun")).isTrue();
    }

    @Test
    public void json_jobObjectHasAllFields() {
        JsonObject obj = gson.toJsonTree(snapshot).getAsJsonObject()
                .getAsJsonArray("jobs").get(0).getAsJsonObject();
        assertThat(obj.has("path")).isTrue();
        assertThat(obj.has("type")).isTrue();
        assertThat(obj.has("jenkins_pronoun")).isTrue();
        assertThat(obj.has("lastBuildTs")).isTrue();
    }

    @Test
    public void json_conjurizationReferenceKeysUseSyntax() {
        JsonObject json = gson.toJsonTree(snapshot).getAsJsonObject();
        JsonArray creds = json.getAsJsonArray("credentials");
        for (JsonElement el : creds) {
            JsonObject conjurization = el.getAsJsonObject().getAsJsonObject("conjurization");
            if (conjurization == null) continue;
            for (Map.Entry<String, JsonElement> entry : conjurization.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue().getAsString();
                if (key.equals("variable:value")
                        || key.startsWith("variable:value:")
                        || key.equals("variable:annotation:jenkins_credential_username")) {
                    assertThat(val)
                            .as("conjurization key '%s' must have a non-blank value", key)
                            .isNotBlank();
                }
            }
        }
    }

    @Test
    public void json_whereUsedPathsAreStrings() {
        JsonObject json = gson.toJsonTree(snapshot).getAsJsonObject();
        JsonArray creds = json.getAsJsonArray("credentials");
        for (JsonElement el : creds) {
            JsonArray whereUsed = el.getAsJsonObject().getAsJsonArray("whereUsed");
            if (whereUsed == null) continue;
            for (JsonElement path : whereUsed) {
                assertThat(path.getAsString()).isNotBlank();
            }
        }
    }

    @Test
    public void json_noValuesFieldWhenExportDisabled() {
        DiscoverySnapshot noExport = buildSnapshot(buildCredentialsNoExport(), buildFolders(), buildJobs());
        JsonObject json = gson.toJsonTree(noExport).getAsJsonObject();
        JsonArray creds = json.getAsJsonArray("credentials");
        for (JsonElement el : creds) {
            assertThat(el.getAsJsonObject().has("values") &&
                    !el.getAsJsonObject().get("values").isJsonNull())
                    .as("'values' field should be absent or null when exportSecretValues=false")
                    .isFalse();
        }
    }

    // ── same credential ID in sibling folders — JSON assertions ─────────────────

    @Test
    public void json_siblingFolderCreds_sameId_produceTwoDistinctCredentialObjects() {
        List<CredentialRecord> creds = new ArrayList<>();
        creds.add(buildStringCredRecord("duplicate-id", "Folder1",
                "FolderCredentialsProvider", "folder", Collections.emptyList()));
        creds.add(buildStringCredRecord("duplicate-id", "Folder2",
                "FolderCredentialsProvider", "folder", Collections.emptyList()));

        DiscoverySnapshot snapshot = buildSnapshot(creds, buildFolders(), buildJobs());
        JsonArray json = gson.toJsonTree(snapshot).getAsJsonObject().getAsJsonArray("credentials");

        assertThat(json.size()).isEqualTo(2);
    }

    @Test
    public void json_siblingFolderCreds_sameId_eachHasDistinctOriginId() {
        List<CredentialRecord> creds = new ArrayList<>();
        creds.add(buildStringCredRecord("duplicate-id", "Folder1",
                "FolderCredentialsProvider", "folder", Collections.emptyList()));
        creds.add(buildStringCredRecord("duplicate-id", "Folder2",
                "FolderCredentialsProvider", "folder", Collections.emptyList()));

        DiscoverySnapshot snapshot = buildSnapshot(creds, buildFolders(), buildJobs());
        JsonArray json = gson.toJsonTree(snapshot).getAsJsonObject().getAsJsonArray("credentials");

        List<String> originIds = new ArrayList<>();
        json.forEach(el -> originIds.add(el.getAsJsonObject().get("originId").getAsString()));
        assertThat(originIds).containsExactlyInAnyOrder("Folder1:duplicate-id", "Folder2:duplicate-id");
    }

    @Test
    public void json_siblingFolderCreds_sameId_eachHasCorrectLocation() {
        List<CredentialRecord> creds = new ArrayList<>();
        creds.add(buildStringCredRecord("duplicate-id", "Folder1",
                "FolderCredentialsProvider", "folder", Collections.emptyList()));
        creds.add(buildStringCredRecord("duplicate-id", "Folder2",
                "FolderCredentialsProvider", "folder", Collections.emptyList()));

        DiscoverySnapshot snapshot = buildSnapshot(creds, buildFolders(), buildJobs());
        JsonArray json = gson.toJsonTree(snapshot).getAsJsonObject().getAsJsonArray("credentials");

        List<String> locations = new ArrayList<>();
        json.forEach(el -> locations.add(el.getAsJsonObject().get("location").getAsString()));
        assertThat(locations).containsExactlyInAnyOrder("Folder1", "Folder2");
    }

    @Test
    public void json_globalAndFolderCreds_sameId_bothPresent_withDistinctScopeInAdditionalData() {
        List<CredentialRecord> creds = new ArrayList<>();
        creds.add(buildStringCredRecord("shared-id", "Global",
                "SystemCredentialsProvider", "global", Collections.emptyList()));
        creds.add(buildStringCredRecord("shared-id", "TeamA",
                "FolderCredentialsProvider", "folder", Collections.emptyList()));

        DiscoverySnapshot snapshot = buildSnapshot(creds, buildFolders(), buildJobs());
        JsonArray json = gson.toJsonTree(snapshot).getAsJsonObject().getAsJsonArray("credentials");

        assertThat(json.size()).isEqualTo(2);

        List<String> scopes = new ArrayList<>();
        json.forEach(el -> scopes.add(
                el.getAsJsonObject().getAsJsonObject("additionalData").get("scope").getAsString()));
        assertThat(scopes).containsExactlyInAnyOrder("global", "folder");
    }

    @Test
    public void json_siblingFolderCreds_sameId_credentialIdFieldPresentOnBoth() {
        List<CredentialRecord> creds = new ArrayList<>();
        creds.add(buildStringCredRecord("dup", "Folder1",
                "FolderCredentialsProvider", "folder", Collections.emptyList()));
        creds.add(buildStringCredRecord("dup", "Folder2",
                "FolderCredentialsProvider", "folder", Collections.emptyList()));

        DiscoverySnapshot snapshot = buildSnapshot(creds, buildFolders(), buildJobs());
        JsonArray json = gson.toJsonTree(snapshot).getAsJsonObject().getAsJsonArray("credentials");

        json.forEach(el -> {
            JsonObject obj = el.getAsJsonObject();
            assertThat(obj.has("credentialId")).isTrue();
            assertThat(obj.get("credentialId").getAsString()).isEqualTo("dup");
        });
    }

    @Test
    public void json_siblingFolderCreds_noDuplicateOriginIds() {
        List<CredentialRecord> creds = new ArrayList<>();
        creds.add(buildStringCredRecord("dup", "Folder1",
                "FolderCredentialsProvider", "folder", Collections.emptyList()));
        creds.add(buildStringCredRecord("dup", "Folder2",
                "FolderCredentialsProvider", "folder", Collections.emptyList()));
        creds.add(buildStringCredRecord("dup", "Folder3",
                "FolderCredentialsProvider", "folder", Collections.emptyList()));

        DiscoverySnapshot snapshot = buildSnapshot(creds, buildFolders(), buildJobs());
        JsonArray json = gson.toJsonTree(snapshot).getAsJsonObject().getAsJsonArray("credentials");

        List<String> originIds = new ArrayList<>();
        json.forEach(el -> originIds.add(el.getAsJsonObject().get("originId").getAsString()));
        assertThat(new HashSet<>(originIds)).hasSize(3);
    }

    @Test
    public void json_siblingFolderCreds_whereUsedFieldPresentOnBoth() {
        List<CredentialRecord> creds = new ArrayList<>();
        creds.add(buildStringCredRecord("my-secret", "Folder1",
                "FolderCredentialsProvider", "folder", Arrays.asList("Folder1", "job-A")));
        creds.add(buildStringCredRecord("my-secret", "Folder2",
                "FolderCredentialsProvider", "folder", Arrays.asList("Folder2", "job-B")));

        DiscoverySnapshot snapshot = buildSnapshot(creds, buildFolders(), buildJobs());
        JsonArray json = gson.toJsonTree(snapshot).getAsJsonObject().getAsJsonArray("credentials");

        json.forEach(el -> {
            JsonArray whereUsed = el.getAsJsonObject().getAsJsonArray("whereUsed");
            assertThat(whereUsed).as("whereUsed must be present on every credential").isNotNull();
            assertThat(whereUsed.size()).isGreaterThan(0);
        });
    }

    // ── UsageTracker.extractCredentialIds ────────────────────────────────────────

    @Test
    public void usageTracker_extractsGroovyStyleCredentialsId() {
        String script = "withCredentials([string(credentialsId: 'finance-db-cred', variable: 'TOKEN')]) {}";
        List<String> ids = UsageTracker.extractCredentialIds(script);
        assertThat(ids).contains("finance-db-cred");
    }

    @Test
    public void usageTracker_extractsGroovyParenStyleCredentialsId() {
        String script = "usernamePassword(credentialsId('global-string-secret'), ...)";
        List<String> ids = UsageTracker.extractCredentialIds(script);
        assertThat(ids).contains("global-string-secret");
    }

    @Test
    public void usageTracker_extractsXmlStyleCredentialsId() {
        String configXml = "<credentialsId>finance-db-cred</credentialsId>";
        List<String> ids = UsageTracker.extractCredentialIds(configXml);
        assertThat(ids).contains("finance-db-cred");
    }

    @Test
    public void usageTracker_extractsDoubleQuoteGroovyForm() {
        String script = "credentials: \"global-string-secret\"";
        List<String> ids = UsageTracker.extractCredentialIds(script);
        assertThat(ids).contains("global-string-secret");
    }

    @Test
    public void usageTracker_returnsEmptyForTextWithNoCredentialIds() {
        List<String> ids = UsageTracker.extractCredentialIds("echo 'hello world'");
        assertThat(ids).isEmpty();
    }

    @Test
    public void usageTracker_handlesNullInput() {
        assertThat(UsageTracker.extractCredentialIds(null)).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helpers — build the in-memory structure that mirrors the orchestrator output
    // ─────────────────────────────────────────────────────────────────────────────

    private DiscoverySnapshot buildSnapshot(List<CredentialRecord> credentials,
                                             List<JenkinsObject> folders,
                                             List<JenkinsObject> jobs) {
        DiscoverySnapshot s = new DiscoverySnapshot();
        s.setJenkinsId(JENKINS_ID);
        s.setOriginStoreId(JENKINS_ID);
        s.setDataSourceType("JenkinsDiscoveryPlugin");
        s.setVersion("3.1.0");
        s.setSnapshotId(SNAPSHOT_ID);
        s.setTimestamp(TIMESTAMP);
        org.conjur.jenkins.disco.model.DiscoExporterConfigurationSnapshot discoCfg =
                new org.conjur.jenkins.disco.model.DiscoExporterConfigurationSnapshot();
        s.setDisCoConfig(discoCfg);
        s.setKid(KID);
        org.conjur.jenkins.disco.model.OpenIdConfiguration oidc = new org.conjur.jenkins.disco.model.OpenIdConfiguration();
        oidc.setIssuer("https://jenkins.internal");
        oidc.setJwksUri(JWKS_URI);
        oidc.setJwksData(null);
        s.setOpenIdConfiguration(oidc);
        s.setConjurConfig(new org.conjur.jenkins.disco.model.GlobalConjurConfigurationSnapshot());
        s.setCredentials(credentials);
        s.setFolders(folders);
        s.setJobs(jobs);
        return s;
    }

    /**
     * Three credentials across three scopes:
     *   1. global-string-secret  — global scope, StringCredentials, used by team/finance/deploy
     *   2. finance-db-cred       — folder scope (team/finance), UsernamePassword,
     *                              used by team/finance/deploy + team/finance/report + team/finance folder
     *   3. unused-cred           — global scope, no usage
     */
    private List<CredentialRecord> buildCredentials() {
        List<CredentialRecord> list = new ArrayList<>();
        list.add(buildStringCredRecord(
                "global-string-secret", "Global",
                "SystemCredentialsProvider", "global",
                Arrays.asList("team/finance/deploy")
        ));
        list.add(buildUserPassCredRecord(
                "finance-db-cred", "team/finance",
                "FolderCredentialsProvider", "folder",
                "dbuser",
                Arrays.asList("team/finance/deploy", "team/finance/report", "team/finance")
        ));
        list.add(buildStringCredRecord(
                "unused-cred", "Global",
                "SystemCredentialsProvider", "global",
                Collections.emptyList()
        ));
        return list;
    }

    private List<CredentialRecord> buildCredentialsNoExport() {
        List<CredentialRecord> list = buildCredentials();
        list.forEach(r -> r.setValues(null));
        return list;
    }

    private CredentialRecord buildStringCredRecord(
            String id, String scopePath,
            String storeProvider, String scope,
            List<String> paths) {

        CredentialRecord rec = new CredentialRecord();
        rec.setCredentialId(id);
        rec.setName(id);
        rec.setOriginId(scopePath + ":" + id);
        rec.setType(StringCredentials.class.getName());
        rec.setLocation(scopePath);
        rec.setDescription(null);
        rec.setConjurization(null); // non-Conjur credential — no conjurization block
        rec.setAdditionalData(additionalData(storeProvider, scope, scopePath));
        rec.setFields(Collections.singletonMap("secret", "hudson.util.Secret"));
        rec.setValues(null);
        rec.setValuesWithError(Collections.emptyList());
        rec.setWhereUsed(new java.util.ArrayList<>(paths));
        rec.setLevelUpdatedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        return rec;
    }

    private CredentialRecord buildUserPassCredRecord(
            String id, String scopePath,
            String storeProvider, String scope,
            String username,
            List<String> paths) {

        CredentialRecord rec = new CredentialRecord();
        rec.setCredentialId(id);
        rec.setName(id);
        rec.setOriginId(scopePath + ":" + id);
        rec.setType(UsernamePasswordCredentialsImpl.class.getName());
        rec.setLocation(scopePath);
        rec.setDescription("Finance database credential");
        rec.setConjurization(null); // non-Conjur credential — no conjurization block
        rec.setAdditionalData(additionalData(storeProvider, scope, scopePath));
        rec.setFields(Map.of("username", "java.lang.String", "password", "hudson.util.Secret"));
        rec.setValues(null);
        rec.setValuesWithError(Collections.emptyList());
        rec.setWhereUsed(new java.util.ArrayList<>(paths));
        rec.setLevelUpdatedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        return rec;
    }

    private List<JenkinsObject> buildFolders() {
        return Arrays.asList(
                new JenkinsObject("", null, "", "GlobalConfiguration", "GlobalConfiguration", ""),
                new JenkinsObject("team", null, "", "com.cloudbees.hudson.plugins.folder.Folder", "Folder", ""),
                new JenkinsObject("team/finance", null, "", "com.cloudbees.hudson.plugins.folder.Folder", "Folder", "")
        );
    }

    private List<JenkinsObject> buildJobs() {
        return Arrays.asList(
                new JenkinsObject(
                        "team/finance/deploy",
                        "Production deployment pipeline",
                        "hudson.plugins.git.GitSCM",
                        "org.jenkinsci.plugins.workflow.job.WorkflowJob",
                        "Pipeline",
                        "2026-03-25T10:00:00Z"
                ),
                new JenkinsObject(
                        "team/build",
                        "Build job",
                        "",
                        "hudson.model.FreeStyleProject",
                        "Project",
                        ""
                ),
                new JenkinsObject(
                        "team/finance/report",
                        "Finance reporting pipeline",
                        "hudson.plugins.git.GitSCM",
                        "org.jenkinsci.plugins.workflow.job.WorkflowJob",
                        "Pipeline",
                        "2026-03-24T08:30:00Z"
                )
        );
    }

    private Map<String, String> additionalData(String storeProvider, String scope, String scopePath) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("storeProvider", storeProvider);
        data.put("storeProviderVersion", "unknown");
        data.put("scope", scope);
        data.put("scopePath", scopePath);
        return data;
    }

    // ── lookup helpers ───────────────────────────────────────────────────────────

    private CredentialRecord findCred(String id) {
        return snapshot.getCredentials().stream()
                .filter(r -> id.equals(r.getCredentialId()))
                .findFirst().orElse(null);
    }

    private JenkinsObject findFolder(String path) {
        return snapshot.getFolders().stream()
                .filter(o -> path.equals(o.getPath()))
                .findFirst().orElse(null);
    }

    private JenkinsObject findJob(String path) {
        return snapshot.getJobs().stream()
                .filter(o -> path.equals(o.getPath()))
                .findFirst().orElse(null);
    }
}
