package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.team.TeamRole;
import ch.stvbero.platform.core.clock.BackendClock;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Domain rules for events and series (TER). No database, so they can be checked in unit tests. */
public final class EventRules {

    /** Default response deadline in hours before the start (TER-10, assumption for O-11). */
    public static final int DEFAULT_RESPONSE_DEADLINE = 24;

    /** Longest response deadline: two weeks. */
    public static final int MAX_RESPONSE_DEADLINE = 336;
    public static final String MIN_RESPONSE_DEADLINE_TEXT = "Mindestens 0 Stunden.";
    public static final String MAX_RESPONSE_DEADLINE_TEXT = "Höchstens 336 Stunden (14 Tage).";

    /**
     * How far ahead the events of a series exist (TER-22): one year from today. A series without an end, or with a
     * later end, gets its further events created regularly, so it never stops by itself.
     */
    public static final java.time.Period SERIES_HORIZON = java.time.Period.ofYears(1);

    /** Last day up to which a series has its events: its end, at most one year from today (TER-22, TER-23). */
    public static LocalDate seriesCap(LocalDate validUntil, LocalDate today) {
        LocalDate horizon = today.plus(SERIES_HORIZON);
        return validUntil == null || validUntil.isAfter(horizon) ? horizon : validUntil;
    }

    /** Time of day, e.g. «18:30». */
    public static final String TIME_PATTERN = "^([01]\\d|2[0-3]):[0-5]\\d$";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** A period from one day to another, both inclusive. */
    public record Period(LocalDate startDate, LocalDate endDate) {
        public boolean contains(LocalDate day) {
            return !day.isBefore(startDate) && !day.isAfter(endDate);
        }
    }

    /** What happens to a future series event that falls into a new series exception (TER-07, O-19). */
    public enum Outcome {
        /** No responses: the event is removed. */
        REMOVE,
        /** With responses: the event is cancelled with the reason of the exception. This way no response is lost. */
        CANCEL
    }

    /** State of an existing future series event when it is synced with the changed series. */
    public record Existing(boolean planned, boolean individuallyChanged) {
    }

    /**
     * Result of syncing a changed series with its future events (TER-08).
     *
     * @param create dates for which a new event is created
     * @param apply  dates whose event takes over the new details of the series
     * @param drop   dates whose event no longer fits the series (removed or cancelled, see {@link Outcome})
     */
    public record SeriesSync(List<LocalDate> create, List<LocalDate> apply, List<LocalDate> drop) {
    }

    private EventRules() {
    }

    // ------------------------------------------------------------------ Permissions

    /**
     * Create events and series: admins and coaches, for every team (TER-11, RR-03, RR-06, E-69).
     * {@code allTeams}: the person is admin or coach in any team ({@code CurrentUser.allTeams()}).
     */
    public static void checkCanCreate(boolean allTeams, Collection<TeamRole> teamRolesInTeams) {
        if (!canEdit(allTeams, teamRolesInTeams)) {
            throw DomainError.forbidden();
        }
    }

    /** Edit and cancel: admins and coaches (TER-11, RR-03, RR-06). */
    public static boolean canEdit(boolean allTeams, Collection<TeamRole> teamRolesInTeams) {
        return allTeams || teamRolesInTeams.contains(TeamRole.COACH);
    }

    /** View: members of a participating team (any team role), coaches and admins (RR-02, RR-03, RR-07). */
    public static void checkCanView(boolean allTeams, Collection<TeamRole> teamRolesInTeams) {
        if (!allTeams && teamRolesInTeams.isEmpty()) {
            throw DomainError.forbidden();
        }
    }

    // ------------------------------------------------------------------ State

    /** Cancelled and past events can no longer be changed or cancelled (409, TER-06). */
    public static void checkChangeable(EventStatus status, Instant end, Instant now) {
        if (status == EventStatus.CANCELLED) {
            throw DomainError.conflict("Termin abgesagt", "Der Termin ist abgesagt und lässt sich nicht mehr ändern.", "TER-06");
        }
        if (!end.isAfter(now)) {
            throw DomainError.conflict("Termin vorbei", "Der Termin ist vorbei und lässt sich nicht mehr ändern.", "TER-06");
        }
    }

    /** A changed start time counts as rescheduling (TER-06). Once rescheduled, the flag stays. */
    public static boolean rescheduled(boolean alreadyRescheduled, Instant oldStart, Instant newStart) {
        return alreadyRescheduled || !oldStart.equals(newStart);
    }

    /** Response deadline: X hours before the start (TER-09). */
    public static Instant responseDeadline(Instant start, int hours) {
        return start.minus(Duration.ofHours(hours));
    }

    /** Without a value, the default response deadline of 24 hours applies (TER-10). */
    public static int responseDeadline(Integer hours) {
        return hours == null ? DEFAULT_RESPONSE_DEADLINE : hours;
    }

    // ------------------------------------------------------------------ Input

    /** An event that is already over can neither be created nor be rescheduled into the past. */
    public static void checkNotOver(Instant end, Instant now) {
        if (!end.isAfter(now)) {
            throw DomainError.invalidField("end", "Der Termin liegt in der Vergangenheit.");
        }
    }

    public static void checkTime(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw DomainError.invalidField("end", "Das Ende muss nach dem Beginn liegen.");
        }
    }

    /** A match day belongs to exactly one team (AUF-03, O-14). */
    public static void checkTeams(EventType type, int teamCount) {
        if (type == EventType.MATCH_DAY && teamCount != 1) {
            throw DomainError.invalidField("teamIds", "Ein Spieltag gehört zu genau einem Team.");
        }
    }

    /** A match day with matches or a call-up stays a match day (409, AUF-01). */
    public static void checkTypeChange(EventType previousType, EventType newType, boolean hasMatchesOrCallUp) {
        if (previousType == EventType.MATCH_DAY && newType != EventType.MATCH_DAY && hasMatchesOrCallUp) {
            throw DomainError.conflict("Spieltag", "Ein Spieltag mit Spielen oder Aufgebot bleibt ein Spieltag.", "AUF-01");
        }
    }

    public static void checkSeries(LocalTime start, LocalTime end, LocalDate startDate, LocalDate endDate) {
        if (!end.isAfter(start)) {
            throw DomainError.invalidField("endTime", "Das Ende muss nach dem Beginn liegen.");
        }
        // Without an end the series runs on (TER-23)
        if (endDate != null && endDate.isBefore(startDate)) {
            throw DomainError.invalidField("validUntil", "Das Ende der Serie muss nach ihrem Beginn liegen.");
        }
    }

    public static void checkException(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw DomainError.invalidField("endDate", "Das Ende der Ausnahme muss nach ihrem Beginn liegen.");
        }
    }

    // ------------------------------------------------------------------ Series

    /**
     * All dates of a series: every {@code day} of the week in the validity period, except the dates in a
     * series exception (TER-05, TER-07). Sorted ascending.
     */
    public static List<LocalDate> dates(DayOfWeek day, LocalDate startDate, LocalDate endDate, Collection<Period> exceptions) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = startDate.with(TemporalAdjusters.nextOrSame(day)); !d.isAfter(endDate); d = d.plusWeeks(1)) {
            if (!inException(d, exceptions)) {
                dates.add(d);
            }
        }
        return dates;
    }

    public static boolean inException(LocalDate day, Collection<Period> exceptions) {
        return exceptions.stream().anyMatch(p -> p.contains(day));
    }

    /** Instant of a series event in the time zone of the domain logic (handles summer and winter time). */
    public static Instant instant(LocalDate day, LocalTime time) {
        return day.atTime(time).atZone(BackendClock.ZONE).toInstant();
    }

    /** New series exception: remove an event without responses, cancel one with responses (TER-07, docs/states.md). */
    public static Outcome onException(boolean hasResponses) {
        return hasResponses ? Outcome.CANCEL : Outcome.REMOVE;
    }

    /**
     * Sync after a change of the series (TER-08). Only future dates are considered.
     * Individually changed or cancelled events stay as they are (TER-06). A date that already has an event
     * (even a cancelled one) does not get a second one.
     *
     * @param existing future events of the series by their series date
     * @param target   future dates of the changed series
     */
    public static SeriesSync syncSeries(Map<LocalDate, Existing> existing, Collection<LocalDate> target) {
        Set<LocalDate> targetDates = new TreeSet<>(target);
        List<LocalDate> create = new ArrayList<>();
        List<LocalDate> apply = new ArrayList<>();
        List<LocalDate> drop = new ArrayList<>();
        for (LocalDate d : targetDates) {
            Existing e = existing.get(d);
            if (e == null) {
                create.add(d);
            } else if (e.planned() && !e.individuallyChanged()) {
                apply.add(d);
            }
        }
        new TreeSet<>(existing.keySet()).forEach(d -> {
            Existing e = existing.get(d);
            if (!targetDates.contains(d) && e.planned() && !e.individuallyChanged()) {
                drop.add(d);
            }
        });
        return new SeriesSync(create, apply, drop);
    }

    // ------------------------------------------------------------------ Display

    /** Number of players per response status. Each player counts once, even if she is in several teams. */
    public static CountsDto count(Collection<ResponseStatus> playerResponses) {
        int attending = 0;
        int declined = 0;
        int maybe = 0;
        int pending = 0;
        for (ResponseStatus s : playerResponses) {
            switch (s) {
                case ATTENDING -> attending++;
                case DECLINED -> declined++;
                case MAYBE -> maybe++;
                case PENDING -> pending++;
            }
        }
        return new CountsDto(attending, declined, maybe, pending);
    }

    /** Calendar day of an instant in the time zone of the domain logic. */
    public static LocalDate date(Instant instant) {
        return instant.atZone(BackendClock.ZONE).toLocalDate();
    }

    public static String time(LocalTime time) {
        return time.format(TIME);
    }

    public static LocalTime time(String time) {
        return LocalTime.parse(time, TIME);
    }

    public static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
