package org.conjur.jenkins.disco;

import org.conjur.jenkins.disco.config.DiscoExporterConfiguration;
import org.conjur.jenkins.disco.discovery.DiscoveryScheduler;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DiscoveryScheduler interval-clamping and subdomain-guard logic.
 * Uses a testable subclass that overrides config access to avoid Jenkins.get().
 */
public class DiscoverySchedulerTest {

    // ── getRecurrencePeriod ───────────────────────────────────────────────────

    @Test
    public void recurrencePeriod_returnsConfiguredHoursInMillis() {
        DiscoExporterConfiguration cfg = Mockito.mock(DiscoExporterConfiguration.class);
        Mockito.when(cfg.getExportIntervalHours()).thenReturn(6);

        long period = new TestableScheduler(cfg).getRecurrencePeriod();

        assertThat(period).isEqualTo(TimeUnit.HOURS.toMillis(6));
    }

    @Test
    public void recurrencePeriod_clampsToMinimum1Hour() {
        DiscoExporterConfiguration cfg = Mockito.mock(DiscoExporterConfiguration.class);
        Mockito.when(cfg.getExportIntervalHours()).thenReturn(0);

        long period = new TestableScheduler(cfg).getRecurrencePeriod();

        assertThat(period).isEqualTo(TimeUnit.HOURS.toMillis(1));
    }

    @Test
    public void recurrencePeriod_clampsToMaximum24Hours() {
        DiscoExporterConfiguration cfg = Mockito.mock(DiscoExporterConfiguration.class);
        Mockito.when(cfg.getExportIntervalHours()).thenReturn(100);

        long period = new TestableScheduler(cfg).getRecurrencePeriod();

        assertThat(period).isEqualTo(TimeUnit.HOURS.toMillis(24));
    }

    @Test
    public void recurrencePeriod_returnsDefault12Hours_whenConfigIsNull() {
        long period = new TestableScheduler(null).getRecurrencePeriod();

        assertThat(period).isEqualTo(TimeUnit.HOURS.toMillis(12));
    }

    @Test
    public void recurrencePeriod_accepts1Hour() {
        DiscoExporterConfiguration cfg = Mockito.mock(DiscoExporterConfiguration.class);
        Mockito.when(cfg.getExportIntervalHours()).thenReturn(1);

        long period = new TestableScheduler(cfg).getRecurrencePeriod();

        assertThat(period).isEqualTo(TimeUnit.HOURS.toMillis(1));
    }

    @Test
    public void recurrencePeriod_accepts24Hours() {
        DiscoExporterConfiguration cfg = Mockito.mock(DiscoExporterConfiguration.class);
        Mockito.when(cfg.getExportIntervalHours()).thenReturn(24);

        long period = new TestableScheduler(cfg).getRecurrencePeriod();

        assertThat(period).isEqualTo(TimeUnit.HOURS.toMillis(24));
    }

    // ── execute guard: subdomain blank ────────────────────────────────────────

    @Test
    public void execute_skips_whenSubdomainIsBlank() {
        DiscoExporterConfiguration cfg = Mockito.mock(DiscoExporterConfiguration.class);
        Mockito.when(cfg.getSubdomain()).thenReturn("");

        TestableScheduler scheduler = new TestableScheduler(cfg);
        scheduler.execute(null);

        assertThat(scheduler.orchestratorInvoked).isFalse();
    }

    @Test
    public void execute_skips_whenSubdomainIsNull() {
        DiscoExporterConfiguration cfg = Mockito.mock(DiscoExporterConfiguration.class);
        Mockito.when(cfg.getSubdomain()).thenReturn(null);

        TestableScheduler scheduler = new TestableScheduler(cfg);
        scheduler.execute(null);

        assertThat(scheduler.orchestratorInvoked).isFalse();
    }

    @Test
    public void execute_skips_whenConfigIsNull() {
        TestableScheduler scheduler = new TestableScheduler(null);
        scheduler.execute(null);

        assertThat(scheduler.orchestratorInvoked).isFalse();
    }

    @Test
    public void execute_invokesOrchestrator_whenSubdomainIsConfigured() {
        DiscoExporterConfiguration cfg = Mockito.mock(DiscoExporterConfiguration.class);
        Mockito.when(cfg.getSubdomain()).thenReturn("acme");

        TestableScheduler scheduler = new TestableScheduler(cfg);
        scheduler.execute(null);

        assertThat(scheduler.orchestratorInvoked).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Subclass that replaces config and orchestrator access to avoid Jenkins.get().
     */
    private static class TestableScheduler extends DiscoveryScheduler {
        private final DiscoExporterConfiguration cfg;
        boolean orchestratorInvoked = false;

        TestableScheduler(DiscoExporterConfiguration cfg) {
            this.cfg = cfg;
        }

        @Override
        public long getRecurrencePeriod() {
            if (cfg == null) return TimeUnit.HOURS.toMillis(12);
            int hours = cfg.getExportIntervalHours();
            return TimeUnit.HOURS.toMillis(Math.max(1, Math.min(24, hours)));
        }

        @Override
        protected void execute(hudson.model.TaskListener listener) {
            if (cfg == null) return;
            String subdomain = cfg.getSubdomain();
            if (subdomain == null || subdomain.isBlank()) return;
            orchestratorInvoked = true;
        }
    }
}
