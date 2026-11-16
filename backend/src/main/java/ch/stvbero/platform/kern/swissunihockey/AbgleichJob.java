package ch.stvbero.platform.kern.swissunihockey;

import ch.stvbero.platform.kern.fehler.FachFehler;
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
public class AbgleichJob {

    private static final Logger LOG = Logger.getLogger(AbgleichJob.class);

    @Inject
    Abgleich abgleich;

    @Scheduled(identity = "swiss-unihockey-abgleich", cron = "{stv.swiss-unihockey.abgleich.cron}",
            timeZone = "Europe/Zurich", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void taeglich() {
        alleAbgleichen();
    }

    /** Gleicht alle verknüpften Teams der aktuellen Saison ab. Gibt die Anzahl der erfolgreichen zurück. */
    public int alleAbgleichen() {
        int erfolgreich = 0;
        for (UUID team : abgleich.verknuepfteTeams()) {
            try {
                abgleich.abgleichen(team);
                erfolgreich++;
            } catch (FachFehler e) {
                LOG.warnf("Täglicher Abgleich für Team %s nicht möglich: %s", team, e.getMessage());
            } catch (RuntimeException e) {
                LOG.errorf(e, "Täglicher Abgleich für Team %s fehlgeschlagen", team);
            }
        }
        return erfolgreich;
    }
}
