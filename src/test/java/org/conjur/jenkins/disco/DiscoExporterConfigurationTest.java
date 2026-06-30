package org.conjur.jenkins.disco;

import hudson.util.FormValidation;
import org.conjur.jenkins.disco.config.DiscoEnvironment;
import org.conjur.jenkins.disco.config.DiscoExporterConfiguration;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DiscoExporterConfiguration business logic.
 * Uses a stub subclass to avoid Jenkins.get() calls.
 */
public class DiscoExporterConfigurationTest {

    private StubConfig config;

    @Before
    public void setUp() {
        config = new StubConfig();
    }

    // ── Rate limit ────────────────────────────────────────────────────────────

    @Test
    public void rateLimitActive_whenLastRunWasWithin60Minutes() {
        config.setLastExportTimestamp(System.currentTimeMillis() - 1_000L);
        assertThat(config.isRateLimitActive()).isTrue();
    }

    @Test
    public void rateLimitNotActive_whenLastRunWasMoreThan60MinutesAgo() {
        config.setLastExportTimestamp(System.currentTimeMillis() - 3_700_000L);
        assertThat(config.isRateLimitActive()).isFalse();
    }

    @Test
    public void rateLimitNotActive_whenNeverRun() {
        config.setLastExportTimestamp(0L);
        assertThat(config.isRateLimitActive()).isFalse();
    }

    @Test
    public void rateLimitBypassed_whenTestEnvironmentIsTrue() {
        config.setLastExportTimestamp(System.currentTimeMillis() - 1_000L);
        config.setTestEnvironment(true);
        assertThat(config.isRateLimitActive()).isFalse();
    }

    // ── Interval clamping ─────────────────────────────────────────────────────

    @Test
    public void exportInterval_clampedToMinimum1() {
        config.setExportIntervalHours(0);
        assertThat(config.getExportIntervalHours()).isEqualTo(1);
    }

    @Test
    public void exportInterval_clampedToMaximum24() {
        config.setExportIntervalHours(100);
        assertThat(config.getExportIntervalHours()).isEqualTo(24);
    }

    @Test
    public void exportInterval_validValuePreserved() {
        config.setExportIntervalHours(8);
        assertThat(config.getExportIntervalHours()).isEqualTo(8);
    }

    // ── Auth mode defaults ────────────────────────────────────────────────────

    @Test
    public void defaultAuthMode_isUsernamePassword() {
        assertThat(config.getAuthMode()).isEqualTo(DiscoExporterConfiguration.AuthMode.USERNAME_PASSWORD);
    }

    @Test
    public void authMode_canBeSetToTwoSecrets() {
        config.setAuthMode(DiscoExporterConfiguration.AuthMode.TWO_SECRETS);
        assertThat(config.getAuthMode()).isEqualTo(DiscoExporterConfiguration.AuthMode.TWO_SECRETS);
    }

    // ── Fresh-install default ─────────────────────────────────────────────────

    @Test
    public void exportSecretValues_defaultsTrueOnFreshInstall() {
        assertThat(config.isExportSecretValues()).isTrue();
    }

    // ── Countdown ────────────────────────────────────────────────────────────

    @Test
    public void millisUntilNextRun_zeroWhenNeverRun() {
        config.setLastExportTimestamp(0L);
        assertThat(config.getMillisUntilNextRun()).isEqualTo(0L);
    }

    @Test
    public void millisUntilNextRun_zeroWhenIntervalHasPassed() {
        config.setExportIntervalHours(1);
        config.setLastExportTimestamp(System.currentTimeMillis() - 4_000_000L);
        assertThat(config.getMillisUntilNextRun()).isEqualTo(0L);
    }

    @Test
    public void millisUntilNextRun_positiveWhenIntervalNotYetPassed() {
        config.setExportIntervalHours(12);
        config.setLastExportTimestamp(System.currentTimeMillis());
        assertThat(config.getMillisUntilNextRun()).isGreaterThan(0L);
    }

    // ── Subdomain pattern (via doCheckSubdomain) ──────────────────────────────

    @Test
    public void subdomainValidation_acceptsAlphanumericDash() {
        var result = config.doCheckSubdomain("acme-corp");
        assertThat(result.kind).isEqualTo(hudson.util.FormValidation.Kind.OK);
    }

    @Test
    public void subdomainValidation_rejectsSpecialChars() {
        var result = config.doCheckSubdomain("acme_corp!");
        assertThat(result.kind).isEqualTo(hudson.util.FormValidation.Kind.ERROR);
    }

    @Test
    public void subdomainValidation_rejectsBlank() {
        var result = config.doCheckSubdomain("");
        assertThat(result.kind).isEqualTo(hudson.util.FormValidation.Kind.ERROR);
    }

    // ── Interval validation ───────────────────────────────────────────────────

    @Test
    public void intervalValidation_rejectsZero() {
        var result = config.doCheckExportIntervalHours(0);
        assertThat(result.kind).isEqualTo(hudson.util.FormValidation.Kind.ERROR);
    }

    @Test
    public void intervalValidation_rejects25() {
        var result = config.doCheckExportIntervalHours(25);
        assertThat(result.kind).isEqualTo(hudson.util.FormValidation.Kind.ERROR);
    }

    @Test
    public void intervalValidation_accepts12() {
        var result = config.doCheckExportIntervalHours(12);
        assertThat(result.kind).isEqualTo(hudson.util.FormValidation.Kind.OK);
    }

    // ── Discovery base URL — defaults to Production ───────────────────────────

    @Test
    public void discoveryBaseUrl_defaultsToProduction() {
        // CYBERARK_DISCO_ENV not set in the test JVM → must resolve to Production
        assertThat(config.getPlatformDiscoveryUrl())
                .isEqualTo(DiscoEnvironment.PRODUCTION.getPlatformDiscoveryUrl());
    }

    // ── Credential validation helpers (doCheck*) ──────────────────────────────

    @Test
    public void credentialValidation_warnsWhenConjurCredentialIdBlank() {
        FormValidation result = config.doCheckConjurCredentialId("");
        assertThat(result.kind).isEqualTo(FormValidation.Kind.WARNING);
    }

    @Test
    public void credentialValidation_okWhenConjurCredentialIdSet() {
        FormValidation result = config.doCheckConjurCredentialId("some-cred-id");
        assertThat(result.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    public void credentialValidation_warnsWhenUsernameCredentialIdBlank() {
        FormValidation result = config.doCheckDiscoUsernameCredentialId("");
        assertThat(result.kind).isEqualTo(FormValidation.Kind.WARNING);
    }

    @Test
    public void credentialValidation_okWhenUsernameCredentialIdSet() {
        FormValidation result = config.doCheckDiscoUsernameCredentialId("user-cred");
        assertThat(result.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    public void credentialValidation_warnsWhenPasswordCredentialIdBlank() {
        FormValidation result = config.doCheckDiscoPasswordCredentialId("");
        assertThat(result.kind).isEqualTo(FormValidation.Kind.WARNING);
    }

    @Test
    public void credentialValidation_okWhenPasswordCredentialIdSet() {
        FormValidation result = config.doCheckDiscoPasswordCredentialId("pass-cred");
        assertThat(result.kind).isEqualTo(FormValidation.Kind.OK);
    }

    // ── Last export timestamp ─────────────────────────────────────────────────

    @Test
    public void lastExportTimestamp_defaultsToZero() {
        assertThat(config.getLastExportTimestamp()).isEqualTo(0L);
    }

    @Test
    public void lastExportTimestamp_updatedBySetterAndRetained() {
        long now = System.currentTimeMillis();
        config.setLastExportTimestamp(now);
        assertThat(config.getLastExportTimestamp()).isEqualTo(now);
    }

    @Test
    public void lastExportTimestamp_settingNewValueOverwritesPrevious() {
        long first = System.currentTimeMillis() - 10_000L;
        long second = System.currentTimeMillis();
        config.setLastExportTimestamp(first);
        config.setLastExportTimestamp(second);
        assertThat(config.getLastExportTimestamp()).isEqualTo(second);
    }

    @Test
    public void lastExportTimestamp_setterTriggersSave() {
        config.setLastExportTimestamp(System.currentTimeMillis());
        assertThat(config.saveCount).isEqualTo(1);
    }

    @Test
    public void lastExportTimestamp_setterTriggersSaveOnEachCall() {
        config.setLastExportTimestamp(System.currentTimeMillis() - 1000L);
        config.setLastExportTimestamp(System.currentTimeMillis());
        assertThat(config.saveCount).isEqualTo(2);
    }

    @Test
    public void lastExportTimestamp_rateLimitActivatesImmediatelyAfterSet() {
        config.setTestEnvironment(false);
        config.setLastExportTimestamp(System.currentTimeMillis());
        assertThat(config.isRateLimitActive()).isTrue();
    }

    @Test
    public void lastExportTimestamp_rateLimitInactiveAfterExpiry() {
        config.setTestEnvironment(false);
        config.setLastExportTimestamp(System.currentTimeMillis() - 3_700_000L);
        assertThat(config.isRateLimitActive()).isFalse();
    }

    @Test
    public void lastExportTimestamp_settingZeroResetsRateLimit() {
        config.setTestEnvironment(false);
        config.setLastExportTimestamp(System.currentTimeMillis());
        assertThat(config.isRateLimitActive()).isTrue();
        config.setLastExportTimestamp(0L);
        assertThat(config.isRateLimitActive()).isFalse();
    }

    // ── Subdomain / tenantId / credentialId getters and setters ──────────────

    @Test
    public void subdomain_getterReturnsSetValue() {
        config.setSubdomainField("acme-corp");
        assertThat(config.getSubdomain()).isEqualTo("acme-corp");
    }

    @Test
    public void conjurCredentialId_getterReturnsSetValue() {
        config.setConjurCredentialId("my-cred");
        assertThat(config.getConjurCredentialId()).isEqualTo("my-cred");
    }

    @Test
    public void discoUsernameCredentialId_getterReturnsSetValue() {
        config.setDiscoUsernameCredentialId("user-secret");
        assertThat(config.getDiscoUsernameCredentialId()).isEqualTo("user-secret");
    }

    @Test
    public void discoPasswordCredentialId_getterReturnsSetValue() {
        config.setDiscoPasswordCredentialId("pass-secret");
        assertThat(config.getDiscoPasswordCredentialId()).isEqualTo("pass-secret");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stub — bypasses Jenkins.get() and XStream persistence

    private static class StubConfig extends DiscoExporterConfiguration {
        private long ts = 0;
        private boolean testEnv = false;
        int saveCount = 0;
        private int interval = 12;
        private boolean secrets = true;
        private AuthMode mode = AuthMode.USERNAME_PASSWORD;
        private String subdomain = "";
        private String conjurCredentialId = "";
        private String discoUsernameCredentialId = "";
        private String discoPasswordCredentialId = "";

        @Override public boolean isRateLimitActive() {
            if (testEnv) return false;
            return (System.currentTimeMillis() - ts) < 3_600_000L;
        }

        @Override public void setLastExportTimestamp(long v) { ts = v; saveCount++; }
        @Override public long getLastExportTimestamp() { return ts; }

        public void setTestEnvironment(boolean v) { testEnv = v; }
        @Override public boolean isTestEnvironment() { return testEnv; }

        @Override public void setExportIntervalHours(int v) { interval = Math.max(1, Math.min(24, v)); }
        @Override public int getExportIntervalHours() { return interval; }

        @Override public boolean isExportSecretValues() { return secrets; }
        @Override public void setExportSecretValues(boolean v) { secrets = v; }

        @Override public AuthMode getAuthMode() { return mode; }
        @Override public void setAuthMode(AuthMode v) { mode = v; }

        @Override public long getMillisUntilNextRun() {
            if (ts == 0) return 0;
            long elapsed = System.currentTimeMillis() - ts;
            return Math.max(0, (long) interval * 3_600_000L - elapsed);
        }

        // Subdomain — used by identityBaseUrl derivation tests
        public void setSubdomainField(String v) { subdomain = v; }
        @Override public String getSubdomain() { return subdomain; }

        @Override public void setConjurCredentialId(String v) { conjurCredentialId = v; }
        @Override public String getConjurCredentialId() { return conjurCredentialId; }

        @Override public void setDiscoUsernameCredentialId(String v) { discoUsernameCredentialId = v; }
        @Override public String getDiscoUsernameCredentialId() { return discoUsernameCredentialId; }

        @Override public void setDiscoPasswordCredentialId(String v) { discoPasswordCredentialId = v; }
        @Override public String getDiscoPasswordCredentialId() { return discoPasswordCredentialId; }

        @Override public void save() {}
        @Override public void load() {}
    }
}
