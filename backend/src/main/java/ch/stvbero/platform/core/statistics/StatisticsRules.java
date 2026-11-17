package ch.stvbero.platform.core.statistics;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.event.AttendanceStatus;
import ch.stvbero.platform.core.event.ResponseStatus;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;

/** Domain rules of the attendance statistics (STA). No database, so they can be checked in unit tests. */
public final class StatisticsRules {

    public static final String END_BEFORE_START = "Das Ende liegt vor dem Beginn.";
    public static final String NO_SEASON = "Für diesen Tag gibt es keine Saison.";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private StatisticsRules() {
    }

    /** Period of the statistics, both days included. */
    public record Period(LocalDate from, LocalDate to) {
    }

    /**
     * Period within one season (STA-02): by default the whole season. A custom period must lie in the season,
     * otherwise 400 at the field.
     */
    public static Period period(LocalDate from, LocalDate to, String seasonName, LocalDate seasonStart,
                                LocalDate seasonEnd) {
        LocalDate start = from == null ? seasonStart : from;
        LocalDate end = to == null ? seasonEnd : to;
        if (end.isBefore(start)) {
            throw DomainError.invalidField("to", END_BEFORE_START);
        }
        String outside = "Der Zeitraum muss in der Saison " + seasonName + " liegen (" + DATE.format(seasonStart)
                + "–" + DATE.format(seasonEnd) + ").";
        if (start.isBefore(seasonStart) || start.isAfter(seasonEnd)) {
            throw DomainError.invalidField("from", outside);
        }
        if (end.isAfter(seasonEnd)) {
            throw DomainError.invalidField("to", outside);
        }
        return new Period(start, end);
    }

    /** An event that counts: past, not cancelled, in the period (STA-07). Attendance recorded with the check on. */
    public record CountedEvent(UUID id, LocalDate date, boolean attendanceRecorded) {
    }

    /** What is stored for one player and event: response (pending if none), late cancellation, attendance. */
    public record Fact(ResponseStatus response, boolean lateCancellation, AttendanceStatus attendance) {

        static final Fact NONE = new Fact(ResponseStatus.PENDING, false, null);
    }

    /** Numbers of one player (STA-03, STA-04). Rates in percent, {@code null} without a base. */
    public record Numbers(int events, int attending, int declined, int maybe, int pending, Integer attendingRate,
                          int lateCancellations, int attendanceEvents, int present, int absent, int excused,
                          Integer attendanceRate) {
    }

    /** The events a player counts for: only from the day she joined the team (STA-07). */
    public static List<CountedEvent> countedFor(List<CountedEvent> events, LocalDate joinedOn) {
        return events.stream().filter(e -> !e.date().isBefore(joinedOn)).toList();
    }

    /**
     * Numbers of one player over the events she counts for (STA-03, STA-04). The share of «dabei» is based on all
     * of them; the attendance rate («anwesend» of all recorded values) only on events with recorded attendance
     * where a value is stored for her.
     */
    public static Numbers numbers(List<CountedEvent> events, Map<UUID, Fact> facts) {
        int attending = 0, declined = 0, maybe = 0, pending = 0, late = 0, present = 0, absent = 0, excused = 0;
        for (CountedEvent event : events) {
            Fact fact = facts.getOrDefault(event.id(), Fact.NONE);
            switch (fact.response() == null ? ResponseStatus.PENDING : fact.response()) {
                case ATTENDING -> attending++;
                case DECLINED -> declined++;
                case MAYBE -> maybe++;
                case PENDING -> pending++;
            }
            if (fact.lateCancellation()) {
                late++;
            }
            if (event.attendanceRecorded() && fact.attendance() != null) {
                switch (fact.attendance()) {
                    case PRESENT -> present++;
                    case ABSENT -> absent++;
                    case EXCUSED -> excused++;
                }
            }
        }
        int recorded = present + absent + excused;
        return new Numbers(events.size(), attending, declined, maybe, pending, rate(attending, events.size()), late,
                recorded, present, absent, excused, rate(present, recorded));
    }

    /** Share in whole percent, half up; {@code null} if there is no base. */
    public static Integer rate(int part, int base) {
        return base == 0 ? null : (int) Math.round(100.0 * part / base);
    }

    /** Team average (STA-05): mean per player, counts with one decimal, rates over the players that have one. */
    public record Average(double events, double attending, double declined, double maybe, double pending,
                          Integer attendingRate, double lateCancellations, double present, double absent,
                          double excused, Integer attendanceRate) {
    }

    /** {@code null} without players. */
    public static Average average(Collection<Numbers> players) {
        if (players.isEmpty()) {
            return null;
        }
        return new Average(mean(players, Numbers::events), mean(players, Numbers::attending),
                mean(players, Numbers::declined), mean(players, Numbers::maybe), mean(players, Numbers::pending),
                meanRate(players.stream().map(Numbers::attendingRate).toList()),
                mean(players, Numbers::lateCancellations), mean(players, Numbers::present),
                mean(players, Numbers::absent), mean(players, Numbers::excused),
                meanRate(players.stream().map(Numbers::attendanceRate).toList()));
    }

    private static double mean(Collection<Numbers> players, ToIntFunction<Numbers> value) {
        double sum = players.stream().mapToInt(value).sum();
        return Math.round(10.0 * sum / players.size()) / 10.0;
    }

    private static Integer meanRate(List<Integer> rates) {
        List<Integer> present = rates.stream().filter(r -> r != null).toList();
        if (present.isEmpty()) {
            return null;
        }
        return (int) Math.round(present.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    /** Own statistics or those of others: the person herself, coaches (of any team, E-69) and admins (STA-08). */
    public static void checkCanSee(boolean allTeams, UUID me, UUID person) {
        if (!allTeams && !me.equals(person)) {
            throw DomainError.forbidden();
        }
    }

    /** Team statistics: coaches (of any team, E-69) and admins (STA-01); everyone else 403. */
    public static void checkCanSeeTeam(boolean allTeams) {
        if (!allTeams) {
            throw DomainError.forbidden();
        }
    }
}
