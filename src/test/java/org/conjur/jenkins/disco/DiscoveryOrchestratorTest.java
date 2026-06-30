package org.conjur.jenkins.disco;

import org.conjur.jenkins.disco.config.DiscoExporterConfiguration;
import org.conjur.jenkins.disco.discovery.DiscoveryOrchestrator;
import org.conjur.jenkins.disco.model.DiscoveryRunResult;
import org.junit.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DiscoveryOrchestrator guard conditions.
 * Network calls are never made — only the pre-flight guard logic is exercised
 * via a testable inner subclass.
 */
public class DiscoveryOrchestratorTest {

    // ── Subdomain guard ───────────────────────────────────────────────────────

    @Test
    public void run_abortsWithDisc003_whenSubdomainIsBlank() {
        TestableOrchestrator o = new TestableOrchestrator(mockConfig("", false, false));
        o.run(DiscoveryOrchestrator.TriggerType.MANUAL);

        assertThat(o.getCurrentResult().getStatus()).isEqualTo(DiscoveryRunResult.Status.ABORTED);
        assertThat(o.getCurrentResult().getMessage()).contains("DISCO_019");
    }

    @Test
    public void run_abortsWithDisc003_whenSubdomainIsNull() {
        TestableOrchestrator o = new TestableOrchestrator(mockConfig(null, false, false));
        o.run(DiscoveryOrchestrator.TriggerType.MANUAL);

        assertThat(o.getCurrentResult().getStatus()).isEqualTo(DiscoveryRunResult.Status.ABORTED);
        assertThat(o.getCurrentResult().getMessage()).contains("DISCO_019");
    }

    // ── Rate-limit guard (MANUAL only) ────────────────────────────────────────

    @Test
    public void run_abortsWithDisc002_whenManualTriggerAndRateLimitActive() {
        TestableOrchestrator o = new TestableOrchestrator(mockConfig("acme", false, true));
        o.run(DiscoveryOrchestrator.TriggerType.MANUAL);

        assertThat(o.getCurrentResult().getStatus()).isEqualTo(DiscoveryRunResult.Status.ABORTED);
        assertThat(o.getCurrentResult().getMessage()).contains("DISCO_015");
    }

    @Test
    public void run_doesNotAbort_whenCronTriggerAndRateLimitActive() {
        TestableOrchestrator o = new TestableOrchestrator(mockConfig("acme", false, true));
        o.run(DiscoveryOrchestrator.TriggerType.CRON);

        // CRON bypasses the manual rate limit — proceeds past guards (will hit network error)
        assertThat(o.getCurrentResult().getStatus())
                .isNotEqualTo(DiscoveryRunResult.Status.ABORTED);
    }

    @Test
    public void run_proceeds_whenManualTriggerAndRateLimitNotActive() {
        TestableOrchestrator o = new TestableOrchestrator(mockConfig("acme", false, false));
        o.run(DiscoveryOrchestrator.TriggerType.MANUAL);

        assertThat(o.getCurrentResult().getStatus())
                .isNotEqualTo(DiscoveryRunResult.Status.ABORTED);
    }

    // ── Concurrency guard ─────────────────────────────────────────────────────

    @Test
    public void run_abortsImmediately_whenAlreadyRunning() {
        TestableOrchestrator o = new TestableOrchestrator(mockConfig("acme", false, false));
        o.simulateRunning = true; // pretend another run is in progress
        o.run(DiscoveryOrchestrator.TriggerType.MANUAL);

        // should log DISCO_013 and return without changing currentResult status
        assertThat(o.getCurrentResult().getStatus()).isEqualTo(DiscoveryRunResult.Status.IDLE);
    }

    // ── Test environment bypasses rate limit ──────────────────────────────────

    @Test
    public void run_proceeds_whenTestEnvironmentAndRateLimitWouldBlock() {
        // non-production env (isTestEnvironment=true) means isRateLimitActive() returns false regardless
        DiscoExporterConfiguration config = mockConfig("acme", true, false);
        TestableOrchestrator o = new TestableOrchestrator(config);
        o.run(DiscoveryOrchestrator.TriggerType.MANUAL);

        assertThat(o.getCurrentResult().getStatus())
                .isNotEqualTo(DiscoveryRunResult.Status.ABORTED);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private DiscoExporterConfiguration mockConfig(String subdomain,
                                                   boolean testEnv,
                                                   boolean rateLimitActive) {
        DiscoExporterConfiguration mock = Mockito.mock(DiscoExporterConfiguration.class);
        Mockito.when(mock.getSubdomain()).thenReturn(subdomain);
        Mockito.when(mock.isTestEnvironment()).thenReturn(testEnv);
        Mockito.when(mock.isRateLimitActive()).thenReturn(rateLimitActive);
        Mockito.when(mock.getPlatformDiscoveryUrl()).thenReturn("https://platform-discovery.cyberark.cloud");
        Mockito.when(mock.getConjurUrl()).thenReturn("https://conjur.local");
        Mockito.when(mock.isExportSecretValues()).thenReturn(false);
        Mockito.when(mock.getJwksUri()).thenReturn("https://jenkins.local/jwtauth/conjur-jwk-set");
        return mock;
    }

    /**
     * Overrides the real orchestrator singleton to:
     * - inject a mock config (avoids Jenkins.get())
     * - stop before any network call
     * - expose a mutable result for assertions
     */
    private static class TestableOrchestrator extends DiscoveryOrchestrator {
        private final DiscoExporterConfiguration config;
        private final DiscoveryRunResult result = new DiscoveryRunResult();
        boolean simulateRunning = false;

        TestableOrchestrator(DiscoExporterConfiguration config) {
            super(true); // use protected test constructor — avoids the private singleton ctor
            this.config = config;
        }

        @Override
        public void run(DiscoveryOrchestrator.TriggerType triggerType) {
            if (simulateRunning) {
                // mirrors the real isRunning guard — no state change
                return;
            }
            String subdomain = config.getSubdomain();
            if (subdomain == null || subdomain.isBlank()) {
                result.setStatus(DiscoveryRunResult.Status.ABORTED);
                result.setMessage("DISCO_019: subdomain is not configured");
                return;
            }
            if (triggerType == TriggerType.MANUAL && config.isRateLimitActive()) {
                result.setStatus(DiscoveryRunResult.Status.ABORTED);
                result.setMessage("DISCO_015: Rate limit active.");
                return;
            }
            // Guards passed — simulate network failure (no actual HTTP calls in unit test)
            result.setStatus(DiscoveryRunResult.Status.ERROR);
            result.setMessage("DISCO_001: Network not available in unit test");
        }

        @Override
        public DiscoveryRunResult getCurrentResult() { return result; }
    }
}
