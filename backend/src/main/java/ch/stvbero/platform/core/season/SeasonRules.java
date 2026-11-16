package ch.stvbero.platform.core.season;

import ch.stvbero.platform.core.error.DomainError;

import java.time.LocalDate;
import java.util.Collection;

/** Fachregeln zu Saisons (SAI). Ohne Datenbank, damit sie als Unit-Test prüfbar sind. */
public final class SeasonRules {

    private SeasonRules() {
    }

    /** Eine bestehende Saison für die Prüfungen: Name und Zeitraum. */
    public record Period(String name, LocalDate startDate, LocalDate endDate) {
    }

    /**
     * Archiv sind alle Saisons, die vor der aktuellen beginnen (SAI-02). Eine neue Saison nach der
     * aktuellen ist kein Archiv: Sie wird vorbereitet und kann angepasst werden (SAI-04).
     */
    public static boolean isArchived(LocalDate startDate, boolean current, LocalDate fromTheCurrent) {
        return !current && fromTheCurrent != null && startDate.isBefore(fromTheCurrent);
    }

    /** Änderungen an Teams und Zugehörigkeiten einer archivierten Saison sind nicht möglich (SAI-02). */
    public static void checkWritable(LocalDate startDate, boolean current, LocalDate fromTheCurrent) {
        if (isArchived(startDate, current, fromTheCurrent)) {
            throw DomainError.conflict("Saison im Archiv",
                    "Diese Saison ist im Archiv und kann nur angesehen werden.", "SAI-02");
        }
    }

    /** Neue Saison: Zeitraum gültig, Name eindeutig, keine Überschneidung mit einer anderen Saison. */
    public static void checkNewSeason(Period newValue, Collection<Period> existing) {
        if (newValue.endDate().isBefore(newValue.startDate())) {
            throw DomainError.invalidField("endDate", "Das Ende muss nach dem Beginn liegen.");
        }
        for (Period b : existing) {
            if (b.name().equalsIgnoreCase(newValue.name().strip())) {
                throw DomainError.conflict("Name vergeben",
                        "Es gibt schon eine Saison «" + b.name() + "».", "SAI-01");
            }
        }
        for (Period b : existing) {
            boolean overlaps = !newValue.startDate().isAfter(b.endDate()) && !newValue.endDate().isBefore(b.startDate());
            if (overlaps) {
                throw DomainError.conflict("Zeitraum überschneidet sich",
                        "Der Zeitraum überschneidet sich mit der Saison «" + b.name() + "».", "SAI-01");
            }
        }
    }
}
