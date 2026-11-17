package ch.stvbero.platform.core.statistics;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.event.AttendanceStatus;
import ch.stvbero.platform.core.event.ResponseStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** F-15: rules of the attendance statistics without a database (STA-02 to STA-08). */
class StatisticsRulesTest {

    private static final LocalDate START = LocalDate.of(2026, 7, 1);
    private static final LocalDate END = LocalDate.of(2027, 6, 30);

    private static StatisticsRules.CountedEvent event(int day, boolean attendance) {
        return new StatisticsRules.CountedEvent(UUID.nameUUIDFromBytes(("e" + day).getBytes()),
                LocalDate.of(2026, 11, day), attendance);
    }

    @Test
    void sta02PeriodDefaultsToTheSeason() {
        assertEquals(new StatisticsRules.Period(START, END), StatisticsRules.period(null, null, "2026/27", START, END));
        LocalDate from = LocalDate.of(2026, 11, 1);
        assertEquals(new StatisticsRules.Period(from, END), StatisticsRules.period(from, null, "2026/27", START, END));
        assertEquals(new StatisticsRules.Period(from, from), StatisticsRules.period(from, from, "2026/27", START, END));
    }

    @Test
    void sta02PeriodMustLieInTheSeason() {
        DomainError reversed = assertThrows(DomainError.class, () -> StatisticsRules.period(
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 1), "2026/27", START, END));
        assertEquals(400, reversed.status());
        DomainError outside = assertThrows(DomainError.class, () -> StatisticsRules.period(
                START, LocalDate.of(2027, 7, 1), "2026/27", START, END));
        assertEquals("Der Zeitraum muss in der Saison 2026/27 liegen (01.07.2026–30.06.2027).", outside.getMessage());
        assertThrows(DomainError.class, () -> StatisticsRules.period(null, LocalDate.of(2026, 6, 1), "2026/27", START, END));
    }

    @Test
    void sta03CountsAndShareOfAttending() {
        var a = event(3, false);
        var b = event(5, true);
        var c = event(10, false);
        var d = event(12, true);
        Map<UUID, StatisticsRules.Fact> facts = Map.of(
                a.id(), new StatisticsRules.Fact(ResponseStatus.ATTENDING, false, null),
                b.id(), new StatisticsRules.Fact(ResponseStatus.ATTENDING, false, AttendanceStatus.PRESENT),
                c.id(), new StatisticsRules.Fact(ResponseStatus.DECLINED, true, null));
        StatisticsRules.Numbers n = StatisticsRules.numbers(List.of(a, b, c, d), facts);
        assertEquals(4, n.events());
        assertEquals(2, n.attending());
        assertEquals(1, n.declined());
        assertEquals(0, n.maybe());
        assertEquals(1, n.pending());
        assertEquals(50, n.attendingRate());
        assertEquals(1, n.lateCancellations());
        // STA-04: only event b has a recorded value for her (d has attendance, but nothing for her)
        assertEquals(1, n.attendanceEvents());
        assertEquals(1, n.present());
        assertEquals(100, n.attendanceRate());
    }

    @Test
    void sta04AttendanceOnlyWhereRecorded() {
        var off = event(3, false);
        var on = event(5, true);
        Map<UUID, StatisticsRules.Fact> facts = Map.of(
                // value kept while the check is switched off (PRS-08): does not count
                off.id(), new StatisticsRules.Fact(ResponseStatus.ATTENDING, false, AttendanceStatus.PRESENT),
                on.id(), new StatisticsRules.Fact(ResponseStatus.DECLINED, false, AttendanceStatus.EXCUSED));
        StatisticsRules.Numbers n = StatisticsRules.numbers(List.of(off, on), facts);
        assertEquals(1, n.attendanceEvents());
        assertEquals(0, n.present());
        assertEquals(1, n.excused());
        assertEquals(0, n.attendanceRate());
    }

    @Test
    void sta03NoEventsNoRate() {
        StatisticsRules.Numbers n = StatisticsRules.numbers(List.of(), Map.of());
        assertNull(n.attendingRate());
        assertNull(n.attendanceRate());
    }

    @Test
    void sta03RateRoundsHalfUp() {
        assertEquals(8, StatisticsRules.rate(2, 26));
        assertEquals(4, StatisticsRules.rate(1, 26));
        assertEquals(50, StatisticsRules.rate(1, 2));
        assertEquals(67, StatisticsRules.rate(2, 3));
        assertNull(StatisticsRules.rate(0, 0));
    }

    @Test
    void sta07OnlyFromTheDaySheJoined() {
        List<StatisticsRules.CountedEvent> events = List.of(event(3, false), event(5, false), event(10, false));
        assertEquals(2, StatisticsRules.countedFor(events, LocalDate.of(2026, 11, 5)).size());
        assertEquals(3, StatisticsRules.countedFor(events, START).size());
        assertEquals(0, StatisticsRules.countedFor(events, LocalDate.of(2026, 11, 11)).size());
    }

    @Test
    void sta05AverageOverThePlayers() {
        var a = new StatisticsRules.Numbers(26, 2, 1, 0, 23, 8, 1, 1, 1, 0, 0, 100);
        var b = new StatisticsRules.Numbers(13, 0, 0, 0, 13, 0, 0, 1, 0, 1, 0, 0);
        var c = new StatisticsRules.Numbers(0, 0, 0, 0, 0, null, 0, 0, 0, 0, 0, null);
        StatisticsRules.Average avg = StatisticsRules.average(List.of(a, b, c));
        assertEquals(13.0, avg.events());
        assertEquals(0.7, avg.attending());
        assertEquals(4, avg.attendingRate());
        assertEquals(50, avg.attendanceRate());
        assertNull(StatisticsRules.average(List.of()));
    }

    @Test
    void sta08OwnNumbersOrCoach() {
        UUID me = UUID.randomUUID();
        assertDoesNotThrow(() -> StatisticsRules.checkCanSee(false, me, me));
        assertDoesNotThrow(() -> StatisticsRules.checkCanSee(true, me, UUID.randomUUID()));
        assertEquals(403, assertThrows(DomainError.class,
                () -> StatisticsRules.checkCanSee(false, me, UUID.randomUUID())).status());
        assertEquals(403, assertThrows(DomainError.class, () -> StatisticsRules.checkCanSeeTeam(false)).status());
    }
}
