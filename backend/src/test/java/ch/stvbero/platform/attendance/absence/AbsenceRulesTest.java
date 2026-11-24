package ch.stvbero.platform.attendance.absence;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.attendance.event.ResponseStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rules of absences (ABS-02 to ABS-05) without database. */
class AbsenceRulesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 16);
    static final UUID HOLIDAYS = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    static final UUID INJURY = UUID.fromString("d0000000-0000-4000-8000-000000000002");
    static final UUID EVENT = UUID.fromString("40000000-0000-4000-8000-000000000001");

    static AbsenceRules.Period holidays(String first, String last) {
        return new AbsenceRules.Period(HOLIDAYS, LocalDate.parse(first), LocalDate.parse(last), "Abwesend: Ferien");
    }

    static AbsenceRules.Practice practice(String start, String end) {
        return new AbsenceRules.Practice(EVENT, OffsetDateTime.parse(start).toInstant(), OffsetDateTime.parse(end).toInstant());
    }

    static final AbsenceRules.Practice TUESDAY = practice("2026-11-17T18:30:00+01:00", "2026-11-17T20:00:00+01:00");

    // ------------------------------------------------------------------ ABS-02 validation

    @Test
    void abs02ValidAbsence() {
        assertDoesNotThrow(() -> AbsenceRules.check(TODAY, TODAY.plusDays(14), AbsenceReason.VACATION, null, TODAY));
        // Started last week, still running (an injury)
        assertDoesNotThrow(() -> AbsenceRules.check(TODAY.minusDays(7), TODAY, AbsenceReason.INJURY, null, TODAY));
    }

    @Test
    void abs02LastDayBeforeFirst() {
        DomainError e = assertThrows(DomainError.class,
                () -> AbsenceRules.check(TODAY.plusDays(3), TODAY.plusDays(2), AbsenceReason.VACATION, null, TODAY));
        assertEquals(400, e.status());
    }

    @Test
    void abs02AtMostOneYear() {
        assertDoesNotThrow(() -> AbsenceRules.check(TODAY, TODAY.plusDays(365), AbsenceReason.INJURY, null, TODAY));
        assertThrows(DomainError.class,
                () -> AbsenceRules.check(TODAY, TODAY.plusDays(366), AbsenceReason.INJURY, null, TODAY));
    }

    @Test
    void abs02NotCompletelyInThePast() {
        assertThrows(DomainError.class,
                () -> AbsenceRules.check(TODAY.minusDays(9), TODAY.minusDays(1), AbsenceReason.VACATION, null, TODAY));
    }

    @Test
    void abs02OtherNeedsANote() {
        DomainError e = assertThrows(DomainError.class,
                () -> AbsenceRules.check(TODAY, TODAY, AbsenceReason.OTHER, "  ", TODAY));
        assertEquals(422, e.status());
        assertEquals("ABS-02", e.rule());
        assertDoesNotThrow(() -> AbsenceRules.check(TODAY, TODAY, AbsenceReason.OTHER, "Militär", TODAY));
    }

    @Test
    void abs02NoOverlap() {
        List<AbsenceRules.Period> existing = List.of(holidays("2026-12-14", "2026-12-20"));
        assertDoesNotThrow(() -> AbsenceRules.checkNoOverlap(LocalDate.parse("2026-12-21"), LocalDate.parse("2026-12-24"), existing));
        assertDoesNotThrow(() -> AbsenceRules.checkNoOverlap(LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-13"), existing));
        DomainError e = assertThrows(DomainError.class,
                () -> AbsenceRules.checkNoOverlap(LocalDate.parse("2026-12-20"), LocalDate.parse("2026-12-22"), existing));
        assertEquals(409, e.status());
    }

    // ------------------------------------------------------------------ ABS-03 comment and coverage

    @Test
    void abs03Comment() {
        assertEquals("Abwesend: Ferien", AbsenceRules.comment(AbsenceReason.VACATION, null));
        assertEquals("Abwesend: Ferien · Skiferien", AbsenceRules.comment(AbsenceReason.VACATION, " Skiferien "));
        assertEquals("Abwesend: Verletzung · Knie", AbsenceRules.comment(AbsenceReason.INJURY, "Knie"));
        assertEquals("Abwesend: Militär", AbsenceRules.comment(AbsenceReason.OTHER, "Militär"));
    }

    @Test
    void abs03CoversWholeDaysInZurichTime() {
        assertTrue(AbsenceRules.covers(holidays("2026-11-17", "2026-11-17"), TUESDAY));
        assertTrue(AbsenceRules.covers(holidays("2026-11-10", "2026-11-17"), TUESDAY));
        assertFalse(AbsenceRules.covers(holidays("2026-11-18", "2026-11-20"), TUESDAY));
        assertFalse(AbsenceRules.covers(holidays("2026-11-10", "2026-11-16"), TUESDAY));
        // A practice until 00:30 touches the next day as well
        AbsenceRules.Practice late = practice("2026-11-17T22:00:00+01:00", "2026-11-18T00:30:00+01:00");
        assertTrue(AbsenceRules.covers(holidays("2026-11-18", "2026-11-20"), late));
    }

    @Test
    void abs03CoveringTakesTheEarliestAbsence() {
        AbsenceRules.Period injury = new AbsenceRules.Period(INJURY, LocalDate.parse("2026-11-01"),
                LocalDate.parse("2026-11-30"), "Abwesend: Verletzung");
        Optional<AbsenceRules.Period> covering = AbsenceRules.covering(TUESDAY,
                List.of(holidays("2026-11-15", "2026-11-20"), injury));
        assertEquals(INJURY, covering.orElseThrow().id());
    }

    // ------------------------------------------------------------------ ABS-03 to ABS-05 decisions

    @Test
    void abs03DeclinesAPendingPractice() {
        AbsenceRules.Action action = AbsenceRules.decide(TUESDAY, List.of(holidays("2026-11-17", "2026-11-17")), null, false)
                .orElseThrow();
        AbsenceRules.Decline decline = assertInstanceOf(AbsenceRules.Decline.class, action);
        assertEquals(HOLIDAYS, decline.absenceId());
        assertEquals("Abwesend: Ferien", decline.comment());
    }

    @Test
    void abs03OwnDeclineStays() {
        assertTrue(AbsenceRules.decide(TUESDAY, List.of(holidays("2026-11-17", "2026-11-17")),
                new AbsenceRules.Current(ResponseStatus.DECLINED, null), true).isEmpty());
    }

    @Test
    void abs03AttendingIsOverriddenOnlyWhenTheAbsenceIsNew() {
        AbsenceRules.Current attending = new AbsenceRules.Current(ResponseStatus.ATTENDING, null);
        List<AbsenceRules.Period> periods = List.of(holidays("2026-11-17", "2026-11-17"));
        assertInstanceOf(AbsenceRules.Decline.class, AbsenceRules.decide(TUESDAY, periods, attending, true).orElseThrow());
        assertTrue(AbsenceRules.decide(TUESDAY, periods, attending, false).isEmpty());
        AbsenceRules.Current maybe = new AbsenceRules.Current(ResponseStatus.MAYBE, null);
        assertTrue(AbsenceRules.decide(TUESDAY, periods, maybe, false).isEmpty());
    }

    @Test
    void abs04ResponseOfAnAbsenceFollowsIt() {
        AbsenceRules.Current byAbsence = new AbsenceRules.Current(ResponseStatus.DECLINED, INJURY);
        AbsenceRules.Action action = AbsenceRules.decide(TUESDAY, List.of(holidays("2026-11-17", "2026-11-17")),
                byAbsence, false).orElseThrow();
        assertEquals(HOLIDAYS, assertInstanceOf(AbsenceRules.Decline.class, action).absenceId());
    }

    @Test
    void abs05NoLongerCoveredIsReopened() {
        AbsenceRules.Current byAbsence = new AbsenceRules.Current(ResponseStatus.DECLINED, HOLIDAYS);
        assertInstanceOf(AbsenceRules.Reopen.class,
                AbsenceRules.decide(TUESDAY, List.of(holidays("2026-11-18", "2026-11-20")), byAbsence, false).orElseThrow());
        assertInstanceOf(AbsenceRules.Reopen.class, AbsenceRules.decide(TUESDAY, List.of(), byAbsence, false).orElseThrow());
        // A response given by hand is never touched outside an absence
        assertTrue(AbsenceRules.decide(TUESDAY, List.of(),
                new AbsenceRules.Current(ResponseStatus.DECLINED, null), true).isEmpty());
    }
}
