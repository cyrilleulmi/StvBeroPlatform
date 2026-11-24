package ch.stvbero.platform.core.absence;

import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.event.ResponseStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * Rules of absences (ABS): what is a valid absence, which practices it covers and what happens to the response
 * of a covered or no longer covered practice. Pure functions without database access.
 */
public final class AbsenceRules {

    /** At most one year in one absence (ABS-02). */
    static final int MAX_DAYS = 366;

    /** Length of the response comment (column {@code response.comment}). */
    static final int COMMENT_LENGTH = 200;

    private AbsenceRules() {
    }

    /** An absence as the rules see it: days inclusive, with the comment for declined responses. */
    public record Period(UUID id, LocalDate first, LocalDate last, String comment) {
    }

    /** An upcoming practice of the person. */
    public record Practice(UUID eventId, Instant start, Instant end) {
    }

    /** Stored response of the person; {@code absenceId} set if an absence gave it. */
    public record Current(ResponseStatus status, UUID absenceId) {
    }

    /** What to do with the response of one practice. */
    public sealed interface Action permits Decline, Reopen {
    }

    /** Store «nicht dabei» with the comment of the absence (ABS-03). */
    public record Decline(UUID eventId, UUID absenceId, String comment) implements Action {
    }

    /** Remove the response given by an absence that no longer covers the practice: back to «offen» (ABS-05). */
    public record Reopen(UUID eventId) implements Action {
    }

    // ------------------------------------------------------------------ Validation

    /**
     * ABS-02: the last day is not before the first, at most one year, it does not lie completely in the past,
     * «Anderes» needs a note.
     */
    public static void check(LocalDate first, LocalDate last, AbsenceReason reason, String note, LocalDate today) {
        if (last.isBefore(first)) {
            throw DomainError.invalidField("to", "Der letzte Tag liegt vor dem ersten.");
        }
        if (ChronoUnit.DAYS.between(first, last) + 1 > MAX_DAYS) {
            throw DomainError.invalidField("to", "Eine Abwesenheit dauert höchstens ein Jahr.");
        }
        if (last.isBefore(today)) {
            throw DomainError.invalidField("to", "Die Abwesenheit liegt ganz in der Vergangenheit.");
        }
        if (reason == AbsenceReason.OTHER && (note == null || note.isBlank())) {
            throw DomainError.ruleViolation("note", "Bemerkung fehlt",
                    "Bei «Anderes» braucht es eine Bemerkung.", "ABS-02");
        }
    }

    /** ABS-02: two absences of the same person do not share a day. */
    public static void checkNoOverlap(LocalDate first, LocalDate last, Collection<Period> others) {
        for (Period other : others) {
            if (!first.isAfter(other.last()) && !last.isBefore(other.first())) {
                throw DomainError.conflict("Überschneidung",
                        "In dieser Zeit hast du schon eine Abwesenheit eingetragen.", "ABS-02");
            }
        }
    }

    // ------------------------------------------------------------------ Coverage

    /** Comment of a declined response, e.g. «Abwesend: Ferien · Sardinien» (ABS-03). */
    public static String comment(AbsenceReason reason, String note) {
        String text = reason == AbsenceReason.OTHER ? "Abwesend" : "Abwesend: " + reason.label();
        if (note != null && !note.isBlank()) {
            text += (reason == AbsenceReason.OTHER ? ": " : " · ") + note.strip();
        }
        return text.length() <= COMMENT_LENGTH ? text : text.substring(0, COMMENT_LENGTH);
    }

    /**
     * A practice is covered if it touches a day of the absence in local time (Europe/Zurich), from the
     * first day 00:00 to the end of the last day (ABS-03).
     */
    public static boolean covers(Period period, Practice practice) {
        Instant from = period.first().atStartOfDay(BackendClock.ZONE).toInstant();
        Instant until = period.last().plusDays(1).atStartOfDay(BackendClock.ZONE).toInstant();
        return practice.start().isBefore(until) && practice.end().isAfter(from);
    }

    /** The absence that covers the practice; with overlapping absences the one that starts first. */
    public static Optional<Period> covering(Practice practice, Collection<Period> periods) {
        return periods.stream().filter(p -> covers(p, practice))
                .min(Comparator.comparing(Period::first).thenComparing(Period::id));
    }

    /**
     * What happens to the response of an upcoming practice (ABS-03 to ABS-05).
     * <ul>
     *     <li>Covered, no response or one given by an absence: decline with the comment of the absence.</li>
     *     <li>Covered, own «nicht dabei»: stays as it is.</li>
     *     <li>Covered, own «dabei» or «vielleicht»: declined only if {@code override}, i.e. when the absence was
     *     just entered or extended to this practice. Later, a response given by hand wins.</li>
     *     <li>Not covered (any more), response given by an absence: back to «offen».</li>
     * </ul>
     */
    public static Optional<Action> decide(Practice practice, Collection<Period> periods, Current current,
                                          boolean override) {
        Optional<Period> covering = covering(practice, periods);
        if (covering.isEmpty()) {
            return current != null && current.absenceId() != null
                    ? Optional.of(new Reopen(practice.eventId())) : Optional.empty();
        }
        Period period = covering.get();
        Decline decline = new Decline(practice.eventId(), period.id(), period.comment());
        if (current == null || current.status() == ResponseStatus.PENDING || current.absenceId() != null) {
            return Optional.of(decline);
        }
        if (current.status() == ResponseStatus.DECLINED) {
            return Optional.empty();
        }
        return override ? Optional.of(decline) : Optional.empty();
    }
}
