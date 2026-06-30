package org.conjur.jenkins.disco;

import org.conjur.jenkins.disco.discovery.UsageTracker;
import org.conjur.jenkins.disco.model.CredentialRecord;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class UsageTrackerTest {

    // ── Groovy / Pipeline forms ───────────────────────────────────────────────

    @Test
    public void extractCredentialIds_groovySingleQuote() {
        String script = "withCredentials([usernamePassword(credentialsId: 'my-cred', ...)]) {}";
        assertThat(UsageTracker.extractCredentialIds(script)).contains("my-cred");
    }

    @Test
    public void extractCredentialIds_groovyDoubleQuote() {
        String script = "withCredentials([string(credentialsId: \"api-token\")]) {}";
        assertThat(UsageTracker.extractCredentialIds(script)).contains("api-token");
    }

    @Test
    public void extractCredentialIds_credentialsHelperMethod() {
        String script = "credentials('deploy-key')";
        assertThat(UsageTracker.extractCredentialIds(script)).contains("deploy-key");
    }

    // ── XML (config.xml) form ─────────────────────────────────────────────────

    @Test
    public void extractCredentialIds_xmlElement() {
        String xml = "<credentialsId>folder-cred</credentialsId>";
        assertThat(UsageTracker.extractCredentialIds(xml)).contains("folder-cred");
    }

    @Test
    public void extractCredentialIds_multipleXmlElements() {
        String xml = "<credentialsId>cred-a</credentialsId><credentialsId>cred-b</credentialsId>";
        List<String> ids = UsageTracker.extractCredentialIds(xml);
        assertThat(ids).contains("cred-a", "cred-b");
    }

    // ── Empty / non-matching inputs ───────────────────────────────────────────

    @Test
    public void extractCredentialIds_emptyString_returnsEmptyList() {
        assertThat(UsageTracker.extractCredentialIds("")).isEmpty();
    }

    @Test
    public void extractCredentialIds_nullString_returnsEmptyList() {
        assertThat(UsageTracker.extractCredentialIds(null)).isEmpty();
    }

    @Test
    public void extractCredentialIds_noMatch_returnsEmptyList() {
        assertThat(UsageTracker.extractCredentialIds("echo hello world")).isEmpty();
    }

    @Test
    public void extractCredentialIds_plainBuildStep_noFalsePositive() {
        String script = "sh 'mvn clean install'";
        assertThat(UsageTracker.extractCredentialIds(script)).isEmpty();
    }

    // ── WhereUsed default state ───────────────────────────────────────────────

    @Test
    public void getWhereUsed_unknownCredential_returnsEmptyPaths() {
        UsageTracker tracker = new UsageTracker();
        var whereUsed = tracker.getWhereUsed("nonexistent-id");
        assertThat(whereUsed).isEmpty();
    }

    // ── Deduplication ─────────────────────────────────────────────────────────

    @Test
    public void setWhereUsed_duplicateEntries_areDeduplicatedInRecord() {
        CredentialRecord rec = new CredentialRecord();
        rec.setWhereUsed(Arrays.asList("team/finance/deploy", "team/finance/deploy", "team/build"));

        assertThat(rec.getWhereUsed())
                .containsExactlyInAnyOrder("team/finance/deploy", "team/build")
                .hasSize(2);
    }

    @Test
    public void setWhereUsed_duplicatesRemovedAndJsonShowsOneEntry() {
        // Simulates: credential used in one job but scanned twice (configXml + script body)
        // → setWhereUsed receives ["team/finance/deploy", "team/finance/deploy"]
        // → snapshot JSON must contain the path exactly once
        CredentialRecord rec = new CredentialRecord();
        rec.setCredentialId("my-secret");
        rec.setWhereUsed(Arrays.asList(
                "team/finance/deploy",
                "team/finance/deploy",
                "team/finance/deploy"
        ));

        List<String> whereUsed = rec.getWhereUsed();
        assertThat(whereUsed)
                .as("whereUsed must deduplicate: same path added multiple times should appear once")
                .containsExactly("team/finance/deploy")
                .hasSize(1);

        // Verify serialisation via Gson produces a single-element array
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String json = gson.toJson(rec);
        com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        com.google.gson.JsonArray arr = obj.getAsJsonArray("whereUsed");
        assertThat(arr).hasSize(1);
        assertThat(arr.get(0).getAsString()).isEqualTo("team/finance/deploy");
    }
}
