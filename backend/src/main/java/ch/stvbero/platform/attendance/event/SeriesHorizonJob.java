package ch.stvbero.platform.attendance.event;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Keeps every series one year ahead (TER-22): every few hours the events that are missing are created. Idempotent;
 * when the clock is set in dev/test, the test support runs it right away.
 */
@ApplicationScoped
public class SeriesHorizonJob {

    private static final Logger LOG = Logger.getLogger(SeriesHorizonJob.class);

    @Inject
    SeriesService series;

    @Scheduled(every = "6h", delayed = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void regularly() {
        int created = series.extend();
        if (created > 0) {
            LOG.infof("Series: %d events created to stay one year ahead", created);
        }
    }
}
