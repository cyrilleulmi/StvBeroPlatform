package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.error.DomainError;

import ch.stvbero.platform.core.clock.BackendClock;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    // ------------------------------------------------------------------ Changes by swiss unihockey (SUH-12)

    public static final String NO_SU_CHANGE = "Dieses Spiel hat keine offene Änderung von swiss unihockey.";

    /** What players need to know about an imported match: when, where, against whom, home or away. */
    public record SuState(Instant startTime, String opponent, Boolean home, String venue) {
    }

    /**
     * The differences between the values before the change and the current ones, as texts for display, in the
     * order time, venue, opponent, home/away (SUH-12). Empty if swiss unihockey changed everything back.
     */
    public static List<SuChangeDto.Item> suChanges(SuState before, SuState now) {
        List<SuChangeDto.Item> changes = new ArrayList<>();
        if (before.startTime() != null && !before.startTime().equals(now.startTime())) {
            changes.add(new SuChangeDto.Item(SuChangeDto.Field.START_TIME, dateTime(before.startTime()),
                    dateTime(now.startTime())));
        }
        if (!norm(before.venue()).equals(norm(now.venue()))) {
            changes.add(new SuChangeDto.Item(SuChangeDto.Field.VENUE, text(before.venue()), text(now.venue())));
        }
        if (before.opponent() != null && !before.opponent().equals(now.opponent())) {
            changes.add(new SuChangeDto.Item(SuChangeDto.Field.OPPONENT, before.opponent(), now.opponent()));
        }
        if (before.home() != null && !before.home().equals(now.home())) {
            changes.add(new SuChangeDto.Item(SuChangeDto.Field.HOME_AWAY, side(before.home()), side(now.home())));
        }
        return changes;
    }

    /** Only a match with an unconfirmed change can be confirmed (409). */
    public static void checkHasSuChange(Instant suChangedAt) {
        if (suChangedAt == null) {
            throw DomainError.conflict("Keine Änderung offen", NO_SU_CHANGE, "SUH-12");
        }
    }

    private static final String[] WEEKDAYS = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd.MM. HH:mm");

    /** «So 22.11. 09:30» in Swiss time. */
    static String dateTime(Instant instant) {
        ZonedDateTime zoned = instant.atZone(BackendClock.ZONE);
        return WEEKDAYS[zoned.getDayOfWeek().getValue() - 1] + " " + DAY_TIME.format(zoned);
    }

    private static String side(boolean home) {
        return home ? "Heimspiel" : "Auswärtsspiel";
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "ohne Angabe" : value.strip();
    }

    private static String norm(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** A cancelled match day accepts no changes to its matches (409, TER-06). */
    public static void checkNotCancelled(EventStatus status) {
        if (status == EventStatus.CANCELLED) {
            throw DomainError.conflict("Termin abgesagt", CANCELLED, "TER-06");
        }
    }
}
