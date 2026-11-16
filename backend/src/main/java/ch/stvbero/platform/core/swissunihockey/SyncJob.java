package ch.stvbero.platform.core.swissunihockey;

import ch.stvbero.platform.core.error.DomainError;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Täglicher Abgleich aller verknüpften Teams (SUH-03). Zeitpunkt: {@code stv.swiss-unihockey.abgleich.cron}
 * (Standard 04:30 Uhr Schweizer Zeit). Jedes Team läuft in einer eigenen Transaktion: Ist swiss unihockey für
 * ein Team gestört, ändert sich dort nichts, die anderen laufen weiter; der nächste Lauf versucht es wieder
 * (SUH-09). Im Profil {@code test} ist der Scheduler aus, Tests rufen {@link #alleAbgleichen()} direkt auf.
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

    /** Gleicht alle verknüpften Teams der aktuellen Saison ab. Gibt die Anzahl der erfolgreichen zurück. */
    public int syncAll() {
        int successful = 0;
        for (UUID team : sync.linkedTeams()) {
            try {
                sync.sync(team);
                successful++;
            } catch (DomainError e) {
                LOG.warnf("Täglicher Abgleich für Team %s nicht möglich: %s", team, e.getMessage());
            } catch (RuntimeException e) {
                LOG.errorf(e, "Täglicher Abgleich für Team %s fehlgeschlagen", team);
            }
        }
        return successful;
    }
}
