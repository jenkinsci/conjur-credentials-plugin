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
 * Verifies that CredentialsDictionaryMapper produces exactly one record per
 * (scope, credentialId) pair, and that two credentials with the same ID stored
 * in different sibling folders are both reported as independent records.
 *
 * credentialsFor() is store-local-only: it never returns credentials that are merely
 * inherited from a parent scope, so inheritance-based duplication cannot occur at the
 * data layer. The seen set uses (scopePath + ":" + credentialId) to guard against any
 * store that vends the same credential twice within a single scope.
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
    // credentialsFor() is store-local-only, so inherited credentials simply never appear
    // in the folder's list — the folder fixture has no entries here.

    @Test
    public void globalCredential_doesNotAppearAgain_forFolder_withNoLocalCreds() {
        StandardCredentials global = mockCred("shared-secret");

        AbstractFolder<?> folder = mockFolder("my-folder");

        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds(global)
                .withFolder(folder /* no local creds */)
                .withNoJobs();

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getCredentialId()).isEqualTo("shared-secret");
        assertThat(records.get(0).getLocation()).isEqualTo("Global");
    }

    // ── same credential ID in two sibling folders → two independent records ───

    @Test
    public void sameIdInSiblingFolders_bothReported_independently() {
        // Each folder owns a credential with the same ID but potentially different
        // descriptions/values. Both must appear as separate records.
        StandardCredentials folder1Cred = mockCred("my-secret");
        StandardCredentials folder2Cred = mockCred("my-secret");

        AbstractFolder<?> folder1 = mockFolder("Folder1");
        AbstractFolder<?> folder2 = mockFolder("Folder2");

        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds()
                .withFolder(folder1, folder1Cred)
                .withFolder(folder2, folder2Cred)
                .withNoJobs();

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records).hasSize(2);
        assertThat(records).extracting(CredentialRecord::getCredentialId)
                .containsExactlyInAnyOrder("my-secret", "my-secret");
        assertThat(records).extracting(CredentialRecord::getLocation)
                .containsExactlyInAnyOrder("Folder1", "Folder2");
    }

    // ── global credential must not appear again in a pipeline job scope ───────

    @Test
    public void globalCredential_doesNotAppearAgain_forJob_withNoLocalCreds() {
        StandardCredentials global = mockCred("pipeline-visible-cred");

        Job<?, ?> job = mockJob("my-pipeline");

        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds(global)
                .withNoFolders()
                .withJob(job /* no local creds */);

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getCredentialId()).isEqualTo("pipeline-visible-cred");
        assertThat(records.get(0).getLocation()).isEqualTo("Global");
    }

    // ── mixed: global + folder-own + job-own — all appear exactly once ────────

    @Test
    public void mixedCredentials_eachAppearExactlyOnce() {
        StandardCredentials globalCred = mockCred("global-cred");
        StandardCredentials folderCred = mockCred("folder-cred");
        StandardCredentials jobCred    = mockCred("job-cred");

        AbstractFolder<?> folder = mockFolder("team-folder");
        Job<?, ?>         job    = mockJob("team-folder/my-job");

        // Each scope returns only its own credential — no inherited entries.
        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds(globalCred)
                .withFolder(folder, folderCred)
                .withJob(job, jobCred);

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

    // ── same ID in three sibling folders → three records ─────────────────────

    @Test
    public void sameIdInThreeSiblingFolders_allThreeReported() {
        StandardCredentials cred1 = mockCred("shared-id");
        StandardCredentials cred2 = mockCred("shared-id");
        StandardCredentials cred3 = mockCred("shared-id");

        AbstractFolder<?> folder1 = mockFolder("Folder1");
        AbstractFolder<?> folder2 = mockFolder("Folder2");
        AbstractFolder<?> folder3 = mockFolder("Folder3");

        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds()
                .withFolder(folder1, cred1)
                .withFolder(folder2, cred2)
                .withFolder(folder3, cred3)
                .withNoJobs();

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records).hasSize(3);
        assertThat(records).extracting(CredentialRecord::getLocation)
                .containsExactlyInAnyOrder("Folder1", "Folder2", "Folder3");
    }

    // ── same ID in global and a folder → both reported ────────────────────────

    @Test
    public void sameIdInGlobalAndFolder_bothReported_withCorrectLocations() {
        StandardCredentials globalCred = mockCred("shared-id");
        StandardCredentials folderCred = mockCred("shared-id");

        AbstractFolder<?> folder = mockFolder("TeamA");

        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds(globalCred)
                .withFolder(folder, folderCred)
                .withNoJobs();

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records).hasSize(2);
        assertThat(records).extracting(CredentialRecord::getLocation)
                .containsExactlyInAnyOrder("Global", "TeamA");
    }

    // ── originId encodes scope + id independently per record ─────────────────

    @Test
    public void sameIdInSiblingFolders_originIdEncodesCorrectScopePerRecord() {
        StandardCredentials cred1 = mockCred("my-secret");
        StandardCredentials cred2 = mockCred("my-secret");

        AbstractFolder<?> folder1 = mockFolder("Folder1");
        AbstractFolder<?> folder2 = mockFolder("Folder2");

        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds()
                .withFolder(folder1, cred1)
                .withFolder(folder2, cred2)
                .withNoJobs();

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records).extracting(CredentialRecord::getOriginId)
                .containsExactlyInAnyOrder("Folder1:my-secret", "Folder2:my-secret");
    }

    // ── same ID in global + sibling folders → all three reported, correct originIds ──

    @Test
    public void sameIdInGlobalAndTwoFolders_allThreeReported_withDistinctOriginIds() {
        StandardCredentials globalCred = mockCred("my-secret");
        StandardCredentials cred1     = mockCred("my-secret");
        StandardCredentials cred2     = mockCred("my-secret");

        AbstractFolder<?> folder1 = mockFolder("Folder1");
        AbstractFolder<?> folder2 = mockFolder("Folder2");

        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds(globalCred)
                .withFolder(folder1, cred1)
                .withFolder(folder2, cred2)
                .withNoJobs();

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records).hasSize(3);
        assertThat(records).extracting(CredentialRecord::getOriginId)
                .containsExactlyInAnyOrder("Global:my-secret", "Folder1:my-secret", "Folder2:my-secret");
    }

    // ── same ID in two job scopes → both reported ─────────────────────────────

    @Test
    public void sameIdInTwoJobScopes_bothReported() {
        StandardCredentials jobCred1 = mockCred("job-secret");
        StandardCredentials jobCred2 = mockCred("job-secret");

        Job<?, ?> job1 = mockJob("pipeline-A");
        Job<?, ?> job2 = mockJob("pipeline-B");

        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds()
                .withNoFolders()
                .withJob(job1, jobCred1)
                .withJob(job2, jobCred2);

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records).hasSize(2);
        assertThat(records).extracting(CredentialRecord::getLocation)
                .containsExactlyInAnyOrder("pipeline-A", "pipeline-B");
    }

    // ── same ID in folder and job → both reported ─────────────────────────────

    @Test
    public void sameIdInFolderAndJob_bothReported() {
        StandardCredentials folderCred = mockCred("shared-secret");
        StandardCredentials jobCred    = mockCred("shared-secret");

        AbstractFolder<?> folder = mockFolder("team");
        Job<?, ?>         job    = mockJob("team/pipeline");

        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds()
                .withFolder(folder, folderCred)
                .withJob(job, jobCred);

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records).hasSize(2);
        assertThat(records).extracting(CredentialRecord::getLocation)
                .containsExactlyInAnyOrder("team", "team/pipeline");
    }

    // ── each record receives the whereUsed list from UsageTracker ────────────

    @Test
    public void sameIdInSiblingFolders_eachRecordHasWhereUsedPopulated() {
        StandardCredentials cred1 = mockCred("my-secret");
        StandardCredentials cred2 = mockCred("my-secret");

        AbstractFolder<?> folder1 = mockFolder("Folder1");
        AbstractFolder<?> folder2 = mockFolder("Folder2");

        when(usageTracker.getWhereUsedInScope("my-secret", "Folder1"))
                .thenReturn(Arrays.asList("Folder1/some-job"));
        when(usageTracker.getWhereUsedInScope("my-secret", "Folder2"))
                .thenReturn(Arrays.asList("Folder2/some-job"));

        TestableMapper mapper = new TestableMapper(config, encryptionService, usageTracker)
                .withGlobalCreds()
                .withFolder(folder1, cred1)
                .withFolder(folder2, cred2)
                .withNoJobs();

        List<CredentialRecord> records = mapper.mapAll();

        assertThat(records).hasSize(2);
        records.forEach(rec -> assertThat(rec.getWhereUsed())
                .as("whereUsed must be populated for record at %s", rec.getLocation())
                .isNotNull()
                .isNotEmpty());
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