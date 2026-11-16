package ch.stvbero.platform.core.swissunihockey;

import ch.stvbero.platform.core.error.DomainError;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Daily sync of all linked teams (SUH-03). Schedule: {@code stv.swiss-unihockey.sync.cron} (default 04:30
 * Swiss time). Every team runs in its own transaction: if swiss unihockey fails for one team, nothing changes
 * there and the others continue; the next run tries again (SUH-09). In the {@code test} profile the scheduler
 * is off and tests call {@link #syncAll()} directly.
 */
@ApplicationScoped
public class SyncJob {

    private static final Logger LOG = Logger.getLogger(SyncJob.class);

    @Inject
    SuSync sync;

    @Scheduled(identity = "swiss-unihockey-sync", cron = "{stv.swiss-unihockey.sync.cron}",
            timeZone = "Europe/Zurich", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void daily() {
        syncAll();
    }

    /** Syncs all linked teams of the current season. Returns the number of successful ones. */
    public int syncAll() {
        int successful = 0;
        for (UUID team : sync.linkedTeams()) {
            try {
                sync.sync(team);
                successful++;
            } catch (DomainError e) {
                LOG.warnf("Daily sync for team %s not possible: %s", team, e.getMessage());
            } catch (RuntimeException e) {
                LOG.errorf(e, "Daily sync for team %s failed", team);
            }
        }
        return successful;
    }
}
