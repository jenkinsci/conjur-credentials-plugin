package org.conjur.jenkins.disco.discovery;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import org.conjur.jenkins.disco.DiscoCode;
import org.conjur.jenkins.disco.config.DiscoExporterConfiguration;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.conjur.jenkins.disco.DiscoCode.*;

/**
 * Background scheduler that periodically triggers the DisCo discovery pipeline.
 * The interval is read from DiscoExporterConfiguration at each recurrence.
 * Manual triggers do NOT reset this scheduler's timer.
 */
@Extension
public class DiscoveryScheduler extends AsyncPeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(DiscoveryScheduler.class.getName());
    private static final long DEFAULT_INTERVAL_MS = TimeUnit.HOURS.toMillis(DiscoExporterConfiguration.DEFAULT_EXPORT_INTERVAL_HOURS);

    public DiscoveryScheduler() {
        super("DisCo Discovery Scheduler");
    }

    @Override
    public long getRecurrencePeriod() {
        DiscoExporterConfiguration config = DiscoExporterConfiguration.get();
        if (config == null) return DEFAULT_INTERVAL_MS;
        int hours = config.getExportIntervalHours();
        return TimeUnit.HOURS.toMillis(hours);
    }

    @Override
    protected void execute(TaskListener listener) {
        DiscoExporterConfiguration config = DiscoExporterConfiguration.get();
        if (config == null) {
            LOGGER.warning(CONFIG_NOT_AVAILABLE_SCHEDULER.format());
            return;
        }
        if (config.getSubdomain() == null || config.getSubdomain().isBlank()) {
            LOGGER.fine(SUBDOMAIN_NOT_CONFIGURED_SCHEDULER.format());
            return;
        }
        LOGGER.info(SCHEDULER_TRIGGERED.format());
        DiscoveryOrchestrator.getInstance().run(DiscoveryOrchestrator.TriggerType.CRON);
    }
}
