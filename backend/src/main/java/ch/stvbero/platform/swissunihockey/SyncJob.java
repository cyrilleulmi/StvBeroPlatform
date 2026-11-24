package ch.stvbero.platform.swissunihockey;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.swissunihockey.league.LeagueService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Daily sync of all linked teams (SUH-03), followed by loading their league data (F-17). Schedule: {@code stv.swiss-unihockey.sync.cron} (default 04:30
 * Swiss time). Every team runs in its own transaction: if swiss unihockey fails for one team, nothing changes
 * there and the others continue; the next run tries again (SUH-09). In the {@code test} profile the scheduler
 * is off and tests call {@link #syncAll()} directly.
 */
@ApplicationScoped
public class SyncJob {

    private static final Logger LOG = Logger.getLogger(SyncJob.class);

    @Inject
    SuSync sync;

    @Inject
    LeagueService league;

    @Scheduled(identity = "swiss-unihockey-sync", cron = "{stv.swiss-unihockey.sync.cron}",
            timeZone = "Europe/Zurich", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void daily() {
        syncAll();
        refreshLeagues();
    }

    /**
     * Loads matches and league of one team right now, e.g. after it was linked (SUH-02). Never throws: if swiss
     * unihockey cannot be reached, the link stays and the failure is noted on it (SUH-09, SUH-13).
     *
     * @return {@code true} if the matches were synced
     */
    public boolean load(UUID team) {
        boolean synced = false;
        try {
            sync.sync(team);
            synced = true;
        } catch (DomainError e) {
            LOG.warnf("Sync for team %s not possible: %s", team, e.getMessage());
        } catch (RuntimeException e) {
            LOG.errorf(e, "Sync for team %s failed", team);
        }
        try {
            league.refresh(team);
        } catch (RuntimeException e) {
            LOG.warnf("League of team %s not loaded: %s", team, e.getMessage());
        }
        return synced;
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

    /**
     * Loads the league data of all linked teams of the current season (F-17, criterion 6). A team for which
     * swiss unihockey fails keeps its last data (SUH-09). Returns the number of successful ones.
     */
    public int refreshLeagues() {
        int successful = 0;
        for (UUID team : sync.linkedTeams()) {
            try {
                if (league.refresh(team)) {
                    successful++;
                }
            } catch (DomainError e) {
                LOG.warnf("League of team %s not loaded: %s", team, e.getMessage());
            } catch (RuntimeException e) {
                LOG.errorf(e, "Loading the league of team %s failed", team);
            }
        }
        return successful;
    }
}
