package org.conjur.jenkins.disco.discovery;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import org.conjur.jenkins.disco.config.DiscoExporterConfiguration;
import org.conjur.jenkins.disco.model.CredentialRecord;
import org.conjur.jenkins.disco.security.EncryptionService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Verifies that CredentialsDictionaryMapper never produces duplicate credential records.
 *
 * The root cause: CredentialsProvider.lookupCredentials() is scope-propagating — calling
 * it on a folder or job returns inherited credentials from all parent scopes in addition
 * to the ones defined locally. Without the credentialId-only dedup key every inherited
 * credential would appear once per scope it is visible in.
 */
public class CredentialsDictionaryMapperDuplicateTest {

    private DiscoExporterConfiguration config;
    private EncryptionService encryptionService;
    private UsageTracker usageTracker;

    @Before
    public void setUp() {
        config = Mockito.mock(DiscoExporterConfiguration.class);
        when(config.getConjurCredentialId()).thenReturn(null);
        when(config.getDiscoUsernameCredentialId()).thenReturn(null);
        when(config.getDiscoPasswordCredentialId()).thenReturn(null);
        when(config.isExportSecretValues()).thenReturn(false);

        encryptionService = Mockito.mock(EncryptionService.class);
        usageTracker = Mockito.mock(UsageTracker.class);
        when(usageTracker.getWhereUsed(Mockito.anyString())).thenReturn(Collections.emptyList());
    }

    // ── global-only credentials appear exactly once ───────────────────────────

    @Test
    public void globalCredentials_appearExactlyOnce_whenNoFoldersOrJobs() {
        StandardCredentials c1 = mockCred("global-secret-1");
        StandardCredentials c2 = mockCred("global-secret-2");

        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds(c1, c2)
                .withNoFolders()
                .withNoJobs();

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records).hasSize(2);
        assertNoDuplicateIds(records);
    }

    // ── a credential defined globally must not appear again for a folder ──────

    @Test
    public void globalCredential_doesNotAppearAgain_whenFolderAlsoReturnsItAsInherited() {
        StandardCredentials global = mockCred("shared-secret");

        // A folder that stores no credentials of its own, but lookupCredentials() on it
        // returns the globally-defined credential because it is inherited.
        AbstractFolder<?> folder = mockFolder("my-folder");

        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds(global)
                .withFolder(folder, global)   // folder "inherits" the global credential
                .withNoJobs();

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getCredentialId()).isEqualTo("shared-secret");
        assertThat(records.get(0).getLocation()).isEqualTo("Global");
    }

    // ── credential local to a folder is not duplicated across that folder ─────

    @Test
    public void folderCredential_appearsOnce_evenIfMultipleFolderLevels() {
        StandardCredentials folderCred = mockCred("folder-secret");

        AbstractFolder<?> folder1 = mockFolder("folder1");
        AbstractFolder<?> folder2 = mockFolder("folder2");

        // folder1 owns the credential; folder2 also returns it via inheritance
        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds()
                .withFolder(folder1, folderCred)
                .withFolder(folder2, folderCred)   // same cred visible from another folder
                .withNoJobs();

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records).hasSize(1);
        assertNoDuplicateIds(records);
    }

    // ── global credential must not appear again in a pipeline job scope ───────

    @Test
    public void globalCredential_doesNotAppearAgain_whenJobAlsoReturnsItAsInherited() {
        StandardCredentials global = mockCred("pipeline-visible-cred");

        Job<?, ?> job = mockJob("my-pipeline");

        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds(global)
                .withNoFolders()
                .withJob(job, global);   // job "sees" the credential through scope inheritance

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records).hasSize(1);
        assertNoDuplicateIds(records);
    }

    // ── mixed: global + folder-own + job-own — all appear exactly once ────────

    @Test
    public void mixedCredentials_eachAppearExactlyOnce() {
        StandardCredentials globalCred   = mockCred("global-cred");
        StandardCredentials folderCred   = mockCred("folder-cred");
        StandardCredentials jobCred      = mockCred("job-cred");

        AbstractFolder<?> folder = mockFolder("team-folder");
        Job<?, ?>         job    = mockJob("team-folder/my-job");

        // folder sees: its own credential + the inherited global one
        // job   sees: its own credential + folder + global (all inherited)
        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds(globalCred)
                .withFolder(folder, globalCred, folderCred)
                .withJob(job, globalCred, folderCred, jobCred);

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records).hasSize(3);
        assertNoDuplicateIds(records);
    }

    // ── folder-local credentials must appear in output ────────────────────────

    @Test
    public void folderOnlyCred_appearsInOutput() {
        StandardCredentials folderCred = mockCred("folder-only-cred");

        AbstractFolder<?> folder = mockFolder("my-folder");

        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds()
                .withFolder(folder, folderCred)
                .withNoJobs();

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getCredentialId()).isEqualTo("folder-only-cred");
    }

    @Test
    public void folderOnlyCred_hasCorrectLocation() {
        StandardCredentials folderCred = mockCred("folder-only-cred");

        AbstractFolder<?> folder = mockFolder("team/finance");

        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds()
                .withFolder(folder, folderCred)
                .withNoJobs();

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records.get(0).getLocation()).isEqualTo("team/finance");
    }

    @Test
    public void globalAndFolderCreds_bothAppearInOutput() {
        StandardCredentials globalCred = mockCred("global-cred");
        StandardCredentials folderCred = mockCred("folder-cred");

        AbstractFolder<?> folder = mockFolder("team/finance");

        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds(globalCred)
                .withFolder(folder, folderCred)
                .withNoJobs();

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records).hasSize(2);
        assertThat(records).extracting(CredentialRecord::getCredentialId)
                .containsExactlyInAnyOrder("global-cred", "folder-cred");
    }

    @Test
    public void folderCred_scopeAnnotationIsFolder() {
        StandardCredentials folderCred = mockCred("folder-cred");

        AbstractFolder<?> folder = mockFolder("team/finance");

        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds()
                .withFolder(folder, folderCred)
                .withNoJobs();

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records.get(0).getAdditionalData()).containsEntry("scope", "folder");
    }

    // ── disco auth credentials are excluded (not deduplicated into output) ────

    @Test
    public void discoAuthCredential_isExcludedFromOutput() {
        when(config.getDiscoUsernameCredentialId()).thenReturn("disco-user");
        StandardCredentials discoUser = mockCred("disco-user");
        StandardCredentials normalCred = mockCred("regular-cred");

        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds(discoUser, normalCred)
                .withNoFolders()
                .withNoJobs();

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getCredentialId()).isEqualTo("regular-cred");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void assertNoDuplicateIds(List<CredentialRecord> records) {
        List<String> ids = records.stream().map(CredentialRecord::getCredentialId).collect(Collectors.toList());
        assertThat(ids).doesNotHaveDuplicates();
    }

    private StandardCredentials mockCred(String id) {
        StandardCredentials cred = Mockito.mock(StandardCredentials.class);
        when(cred.getId()).thenReturn(id);
        when(cred.getDescription()).thenReturn("");
        return cred;
    }

    @SuppressWarnings("unchecked")
    private AbstractFolder<?> mockFolder(String fullName) {
        AbstractFolder<?> folder = Mockito.mock(AbstractFolder.class);
        when(folder.getFullName()).thenReturn(fullName);
        return folder;
    }

    @SuppressWarnings("unchecked")
    private Job<?, ?> mockJob(String fullName) {
        Job<?, ?> job = Mockito.mock(Job.class);
        when(job.getFullName()).thenReturn(fullName);
        return job;
    }

    // ── Testable subclass — replaces all Jenkins.get() and CredentialsProvider calls ──

    private static class TestableMapper extends CredentialsDictionaryMapper {

        private List<StandardCredentials> globalCreds = Collections.emptyList();
        private final java.util.LinkedHashMap<AbstractFolder<?>, List<StandardCredentials>> folderCreds = new java.util.LinkedHashMap<>();
        private final java.util.LinkedHashMap<Job<?, ?>, List<StandardCredentials>> jobCreds = new java.util.LinkedHashMap<>();

        TestableMapper(DiscoExporterConfiguration config,
                       EncryptionService encryptionService,
                       UsageTracker usageTracker) {
            super(config, encryptionService, usageTracker);
        }

        TestableMapper withGlobalCreds(StandardCredentials... creds) {
            this.globalCreds = Arrays.asList(creds);
            return this;
        }

        TestableMapper withFolder(AbstractFolder<?> folder, StandardCredentials... creds) {
            folderCreds.put(folder, Arrays.asList(creds));
            return this;
        }

        TestableMapper withNoFolders() {
            return this;
        }

        TestableMapper withJob(Job<?, ?> job, StandardCredentials... creds) {
            jobCreds.put(job, Arrays.asList(creds));
            return this;
        }

        TestableMapper withNoJobs() {
            return this;
        }

        @Override
        protected ItemGroup<?> globalContext() {
            return Mockito.mock(ItemGroup.class);  // identity only — credentials injected via credentialsFor()
        }

        @Override
        protected List<AbstractFolder<?>> foldersToScan() {
            return new java.util.ArrayList<>(folderCreds.keySet());
        }

        @Override
        protected List<Job<?, ?>> jobsToScan() {
            return new java.util.ArrayList<>(jobCreds.keySet());
        }

        @Override
        protected List<StandardCredentials> credentialsFor(ItemGroup<?> context) {
            // Only global (Jenkins) credentials are returned via the ItemGroup overload.
            // Folder-local credentials must come through credentialsFor(Item) — this mirrors
            // real Jenkins where FolderCredentialsProvider only responds to the Item overload.
            return globalCreds;
        }

        @Override
        protected List<StandardCredentials> credentialsFor(Item context) {
            // Folders return their own credentials through the Item overload (real Jenkins behaviour).
            for (AbstractFolder<?> folder : folderCreds.keySet()) {
                if (folder == context) return folderCreds.get(folder);
            }
            return jobCreds.getOrDefault(context, Collections.emptyList());
        }
    }
}