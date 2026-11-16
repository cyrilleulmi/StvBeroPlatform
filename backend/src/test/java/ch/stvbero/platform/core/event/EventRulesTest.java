package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.team.TeamRole;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Domain rules for events and series without a database (TER). */
class EventRulesTest {

    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    @Test
    void ter05EveryWeekdayInPeriod() {
        assertEquals(List.of(d(2026, 11, 17), d(2026, 11, 24), d(2026, 12, 1)),
                EventRules.dates(DayOfWeek.TUESDAY, d(2026, 11, 16), d(2026, 12, 1), List.of()));
        // Start and end of the period are included
        assertEquals(List.of(d(2026, 11, 17)),
                EventRules.dates(DayOfWeek.TUESDAY, d(2026, 11, 17), d(2026, 11, 17), List.of()));
        assertEquals(List.of(), EventRules.dates(DayOfWeek.MONDAY, d(2026, 11, 17), d(2026, 11, 22), List.of()));
    }

    @Test
    void ter07ExceptionsAreLeftOut() {
        List<EventRules.Period> holidays = List.of(new EventRules.Period(d(2026, 12, 21), d(2027, 1, 3)));
        List<LocalDate> dates = EventRules.dates(DayOfWeek.TUESDAY, d(2026, 12, 15), d(2027, 1, 12), holidays);
        assertEquals(List.of(d(2026, 12, 15), d(2027, 1, 5), d(2027, 1, 12)), dates);
        assertTrue(EventRules.inException(d(2027, 1, 3), holidays));
        assertFalse(EventRules.inException(d(2027, 1, 4), holidays));
    }

    @Test
    void ter07RemoveWithoutResponsesCancelWithResponses() {
        assertEquals(EventRules.Outcome.REMOVE, EventRules.onException(false));
        assertEquals(EventRules.Outcome.CANCEL, EventRules.onException(true));
    }

    @Test
    void ter05InstantWithSummerAndWinterTime() {
        assertEquals(OffsetDateTime.parse("2026-10-20T18:30:00+02:00").toInstant(),
                EventRules.instant(d(2026, 10, 20), LocalTime.of(18, 30)));
        assertEquals(OffsetDateTime.parse("2026-11-17T18:30:00+01:00").toInstant(),
                EventRules.instant(d(2026, 11, 17), LocalTime.of(18, 30)));
    }

    @Test
    void ter09ResponseDeadlineBeforeStart() {
        Instant start = OffsetDateTime.parse("2026-11-17T18:30:00+01:00").toInstant();
        assertEquals(OffsetDateTime.parse("2026-11-16T18:30:00+01:00").toInstant(), EventRules.responseDeadline(start, 24));
        assertEquals(start, EventRules.responseDeadline(start, 0));
    }

    @Test
    void ter10DefaultDeadlineIsTwentyFourHours() {
        assertEquals(24, EventRules.responseDeadline((Integer) null));
        assertEquals(48, EventRules.responseDeadline(48));
    }

    @Test
    void ter11CoachOfATeamOrAdmin() {
        assertDoesNotThrow(() -> EventRules.checkCanCreate(false, Set.of(TeamRole.COACH)));
        assertDoesNotThrow(() -> EventRules.checkCanCreate(false, Set.of(TeamRole.COACH, TeamRole.PLAYER)));
        assertDoesNotThrow(() -> EventRules.checkCanCreate(true, Set.of()));
        DomainError error = assertThrows(DomainError.class, () -> EventRules.checkCanCreate(false, Set.of(TeamRole.PLAYER)));
        assertEquals(403, error.status());
        assertEquals(403, assertThrows(DomainError.class, () -> EventRules.checkCanCreate(false, Set.of())).status());
    }

    @Test
    void rr02OnlyMembersCanView() {
        assertDoesNotThrow(() -> EventRules.checkCanView(false, Set.of(TeamRole.PLAYER)));
        assertDoesNotThrow(() -> EventRules.checkCanView(true, Set.of()));
        assertEquals(403, assertThrows(DomainError.class, () -> EventRules.checkCanView(false, Set.of())).status());
    }

    @Test
    void ter06CancelledOrPastIsNotChangeable() {
        Instant now = Instant.parse("2026-11-16T05:00:00Z");
        assertDoesNotThrow(() -> EventRules.checkChangeable(EventStatus.PLANNED, now.plusSeconds(60), now));
        DomainError cancelled = assertThrows(DomainError.class,
                () -> EventRules.checkChangeable(EventStatus.CANCELLED, now.plusSeconds(60), now));
        assertEquals(409, cancelled.status());
        assertEquals("TER-06", cancelled.rule());
        assertEquals(409, assertThrows(DomainError.class,
                () -> EventRules.checkChangeable(EventStatus.PLANNED, now, now)).status());
    }

    @Test
    void ter06RescheduledOnlyWithOtherStartTime() {
        Instant a = Instant.parse("2026-11-19T18:00:00Z");
        assertFalse(EventRules.rescheduled(false, a, a));
        assertTrue(EventRules.rescheduled(false, a, a.plusSeconds(3600)));
        assertTrue(EventRules.rescheduled(true, a, a));
    }

    @Test
    void inputsAreChecked() {
        Instant a = Instant.parse("2026-11-19T18:00:00Z");
        assertEquals(400, assertThrows(DomainError.class, () -> EventRules.checkTime(a, a)).status());
        assertEquals(400, assertThrows(DomainError.class, () -> EventRules.checkTeams(EventType.MATCH_DAY, 2)).status());
        assertDoesNotThrow(() -> EventRules.checkTeams(EventType.PRACTICE, 2));
        assertDoesNotThrow(() -> EventRules.checkTeams(EventType.MATCH_DAY, 1));
        assertEquals(400, assertThrows(DomainError.class, () -> EventRules.checkNotOver(a, a)).status());
        LocalTime eight = LocalTime.of(20, 0);
        assertEquals(400, assertThrows(DomainError.class,
                () -> EventRules.checkSeries(eight, eight, d(2026, 11, 1), d(2026, 12, 1))).status());
        assertEquals(400, assertThrows(DomainError.class,
                () -> EventRules.checkSeries(LocalTime.of(18, 0), eight, d(2026, 12, 1), d(2026, 11, 1))).status());
        assertEquals(400, assertThrows(DomainError.class,
                () -> EventRules.checkSeries(LocalTime.of(18, 0), eight, d(2026, 8, 1), d(2027, 8, 5))).status());
        assertDoesNotThrow(() -> EventRules.checkSeries(LocalTime.of(18, 0), eight, d(2026, 8, 18), d(2027, 4, 27)));
        assertEquals(400, assertThrows(DomainError.class,
                () -> EventRules.checkException(d(2026, 12, 2), d(2026, 12, 1))).status());
    }

    @Test
    void ter08SyncOnSeriesChange() {
        LocalDate a = d(2026, 11, 17);
        LocalDate b = d(2026, 11, 24);
        LocalDate c = d(2026, 12, 1);
        LocalDate e = d(2026, 12, 8);
        LocalDate f = d(2026, 12, 15);
        Map<LocalDate, EventRules.Existing> existing = Map.of(
                a, new EventRules.Existing(true, false),
                b, new EventRules.Existing(false, false),   // cancelled
                c, new EventRules.Existing(true, true),     // changed individually
                e, new EventRules.Existing(true, false));
        EventRules.SuSync sync = EventRules.sync(existing, List.of(a, b, c, f));
        assertEquals(List.of(f), sync.create());
        assertEquals(List.of(a), sync.apply());
        assertEquals(List.of(e), sync.drop());
    }

    @Test
    void countsEveryPlayerOnce() {
        assertEquals(new CountsDto(2, 1, 0, 3), EventRules.count(List.of(ResponseStatus.ATTENDING, ResponseStatus.PENDING,
                ResponseStatus.DECLINED, ResponseStatus.ATTENDING, ResponseStatus.PENDING, ResponseStatus.PENDING)));
    }
}
