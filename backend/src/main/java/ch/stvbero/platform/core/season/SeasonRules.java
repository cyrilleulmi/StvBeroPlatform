package ch.stvbero.platform.core.season;

import ch.stvbero.platform.core.error.DomainError;

import java.time.LocalDate;
import java.util.Collection;

/** Domain rules for seasons (SAI). Without a database, so that unit tests can check them. */
public final class SeasonRules {

    private SeasonRules() {
    }

    /** An existing season for the checks: name and period. */
    public record Period(String name, LocalDate startDate, LocalDate endDate) {
    }

    /**
     * The archive holds all seasons that start before the current one (SAI-02). A new season after the
     * current one is not archived: it is being prepared and can still be changed (SAI-04).
     */
    public static boolean isArchived(LocalDate startDate, boolean current, LocalDate currentStartDate) {
        return !current && currentStartDate != null && startDate.isBefore(currentStartDate);
    }

    /** Teams and memberships of an archived season cannot be changed (SAI-02). */
    public static void checkWritable(LocalDate startDate, boolean current, LocalDate currentStartDate) {
        if (isArchived(startDate, current, currentStartDate)) {
            throw DomainError.conflict("Saison im Archiv",
                    "Diese Saison ist im Archiv und kann nur angesehen werden.", "SAI-02");
        }
    }

    /** New season: valid period, unique name, no overlap with another season. */
    public static void checkNewSeason(Period candidate, Collection<Period> existing) {
        if (candidate.endDate().isBefore(candidate.startDate())) {
            throw DomainError.invalidField("endDate", "Das Ende muss nach dem Beginn liegen.");
        }
        for (Period other : existing) {
            if (other.name().equalsIgnoreCase(candidate.name().strip())) {
                throw DomainError.conflict("Name vergeben",
                        "Es gibt schon eine Saison «" + other.name() + "».", "SAI-01");
            }
        }
        for (Period other : existing) {
            boolean overlaps = !candidate.startDate().isAfter(other.endDate()) && !candidate.endDate().isBefore(other.startDate());
            if (overlaps) {
                throw DomainError.conflict("Zeitraum überschneidet sich",
                        "Der Zeitraum überschneidet sich mit der Saison «" + other.name() + "».", "SAI-01");
            }
        }
    }
}
