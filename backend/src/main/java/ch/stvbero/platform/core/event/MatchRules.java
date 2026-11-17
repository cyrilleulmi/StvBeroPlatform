package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.error.DomainError;

import java.time.Instant;
import java.util.Objects;

/** Domain rules for the matches of a match day (AUF-14, AUF-15, SUH-06). No database. */
public final class MatchRules {

    /** Result as home:away, e.g. «5:3». */
    public static final String RESULT_PATTERN = "^\\d{1,2}:\\d{1,2}$";
    public static final String RESULT_TEXT = "Resultat bitte als Heim:Gast, z.B. 5:3.";
    public static final String ON_MATCH_DAY = "Das Spiel muss am Tag des Spieltags stattfinden.";
    public static final String IMPORTED = "Gegner, Anspielzeit und Heim/Auswärts kommen von swiss unihockey und lassen sich nicht ändern.";
    public static final String NOT_DELETABLE = "Spiele von swiss unihockey lassen sich nur löschen, wenn swiss unihockey sie nicht mehr liefert.";
    public static final String ONLY_MATCH_DAY = "Nur ein Spieltag hat Spiele.";
    public static final String IMPORTED_RESCHEDULE = "Der Spieltag hat Spiele von swiss unihockey. Er lässt sich nur am selben Tag verschieben; das Datum kommt von swiss unihockey.";
    public static final String CANCELLED = "Der Spieltag ist abgesagt. Spiele lassen sich nicht mehr ändern.";

    private MatchRules() {
    }

    /** Only match days have matches (AUF-01, AUF-14). */
    public static void checkMatchDay(EventType type) {
        if (type != EventType.MATCH_DAY) {
            throw DomainError.conflict("Kein Spieltag", ONLY_MATCH_DAY, "AUF-01");
        }
    }

    /**
     * When a match is changed, the same-day rule only applies to a new start time. This way a result can always
     * be entered (AUF-15), even if an earlier state does not meet the rule.
     */
    public static void checkOnMatchDayWhenChanging(Instant oldStartTime, Instant newStartTime, Instant matchDayStart) {
        if (!oldStartTime.equals(newStartTime)) {
            checkOnMatchDay(newStartTime, matchDayStart);
        }
    }

    /**
     * A match day with matches from swiss unihockey cannot be moved to another day: date and time of the
     * matches come from swiss unihockey (SUH-05, SUH-06), they do not move along (O-48). 422 on the field
     * {@code start}.
     */
    public static void checkReschedulable(boolean hasImportedMatches, Instant oldStart, Instant newStart) {
        if (hasImportedMatches && !EventRules.date(oldStart).equals(EventRules.date(newStart))) {
            throw DomainError.ruleViolation("start", "Spiele von swiss unihockey", IMPORTED_RESCHEDULE, "SUH-05");
        }
    }

    /** A match day takes place on one day (AUF-01): its matches are on the date of its start. */
    public static void checkOnMatchDay(Instant startTime, Instant matchDayStart) {
        if (!EventRules.date(startTime).equals(EventRules.date(matchDayStart))) {
            throw DomainError.invalidField("startTime", ON_MATCH_DAY);
        }
    }

    /**
     * For matches from swiss unihockey, opponent, start time and home/away are read-only (409). The result can
     * be entered (AUF-15); the sync may overwrite it later.
     */
    public static void checkChangeable(MatchSource source, String oldOpponent, Instant oldStartTime, boolean oldHome,
                                       String newOpponent, Instant newStartTime, boolean newHome) {
        if (source == MatchSource.SWISS_UNIHOCKEY && (!Objects.equals(oldOpponent, newOpponent)
                || !oldStartTime.equals(newStartTime) || oldHome != newHome)) {
            throw DomainError.conflict("Importiertes Spiel", IMPORTED, "SUH-06");
        }
    }

    /** Matches created by hand can be deleted, imported ones only once SU no longer delivers them (409). */
    public static void checkDeletable(MatchSource source, boolean missingAtSu) {
        if (source == MatchSource.SWISS_UNIHOCKEY && !missingAtSu) {
            throw DomainError.conflict("Importiertes Spiel", NOT_DELETABLE, "SUH-06");
        }
    }

    /** A cancelled match day accepts no changes to its matches (409, TER-06). */
    public static void checkNotCancelled(EventStatus status) {
        if (status == EventStatus.CANCELLED) {
            throw DomainError.conflict("Termin abgesagt", CANCELLED, "TER-06");
        }
    }
}
