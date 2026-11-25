package ch.stvbero.platform.driver;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.attendance.event.EventStatus;
import ch.stvbero.platform.attendance.event.EventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F-20: rules of the driver list (DRV-01 to DRV-10). */
class DriverRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-16T05:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3600);
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();
    private static final UUID D = UUID.randomUUID();

    private static int status(Runnable check) {
        return assertThrows(DomainError.class, check::run).status();
    }

    @Test
    void drv01OnlyMatchDaysWithMeetingPointAndVenue() {
        assertEquals(409, status(() -> DriverRules.checkMatchDay(EventType.PRACTICE)));
        assertDoesNotThrow(() -> DriverRules.checkMatchDay(EventType.MATCH_DAY));
        assertEquals(List.of(DriverRules.Missing.MEETING_POINT, DriverRules.Missing.VENUE), DriverRules.missing(false, false));
        assertEquals(List.of(DriverRules.Missing.VENUE), DriverRules.missing(true, false));
        assertEquals(List.of(), DriverRules.missing(true, true));
        assertEquals(409, status(() -> DriverRules.checkChangeable(List.of(DriverRules.Missing.VENUE),
                EventStatus.PLANNED, LATER, NOW, true)));
    }

    @Test
    void drv10CancelledCannotBeChangedAndOverOnlyByCoaches() {
        assertEquals(409, status(() -> DriverRules.checkChangeable(List.of(), EventStatus.CANCELLED, LATER, NOW, true)));
        assertEquals(409, status(() -> DriverRules.checkChangeable(List.of(), EventStatus.PLANNED, NOW, NOW, false)));
        assertDoesNotThrow(() -> DriverRules.checkChangeable(List.of(), EventStatus.PLANNED, NOW, NOW, true));
        assertDoesNotThrow(() -> DriverRules.checkChangeable(List.of(), EventStatus.PLANNED, LATER, NOW, false));
    }

    @Test
    void drv02SelfAsMemberOthersOnlyAsCoachOrAdmin() {
        assertDoesNotThrow(() -> DriverRules.checkCanActFor(A, A, true, false));
        assertEquals(403, status(() -> DriverRules.checkCanActFor(A, A, false, false)));
        assertEquals(403, status(() -> DriverRules.checkCanActFor(A, B, true, false)));
        assertDoesNotThrow(() -> DriverRules.checkCanActFor(A, B, false, true));
        assertEquals(422, status(() -> DriverRules.checkMember(false, "driverId")));
        assertEquals(409, status(() -> DriverRules.checkNotDriving(true, "Petra Keller")));
    }

    @Test
    void drv03NeverMorePassengersThanSeats() {
        assertDoesNotThrow(() -> DriverRules.checkFreeSeat(3, 2));
        assertEquals(409, status(() -> DriverRules.checkFreeSeat(3, 3)));
        assertEquals(422, status(() -> DriverRules.checkSeats(2, 3)));
        assertDoesNotThrow(() -> DriverRules.checkSeats(3, 3));
        assertEquals(400, status(() -> DriverRules.checkSeats(0, 0)));
        assertEquals(400, status(() -> DriverRules.checkSeats(9, 0)));
        assertEquals(422, status(() -> DriverRules.checkNotDriver(true)));
    }

    @Test
    void drv04WithoutSeatFollowsThePublishedCallUpOtherwiseTheResponses() {
        // Published call-up: called up A, B, C; A drives, B rides along
        assertEquals(List.of(C), DriverRules.withoutSeat(true, List.of(A, B, C), List.of(D), Set.of(A, B)));
        // No published call-up: «dabei» D and C
        assertEquals(List.of(D, C), DriverRules.withoutSeat(false, List.of(A), List.of(D, C), Set.of()));
        assertEquals(List.of(), DriverRules.withoutSeat(false, List.of(), List.of(D), Set.of(D)));
    }

    @Test
    void drv05CreditIsTheReturnTrip() {
        assertEquals(46_800, DriverRules.credit(23_400, EventStatus.PLANNED));
        assertEquals(46.8, DriverRules.kilometres(DriverRules.credit(23_400, EventStatus.PLANNED)));
        assertNull(DriverRules.credit(null, EventStatus.PLANNED));
        assertEquals(26, DriverRules.minutes(1561));
        assertEquals(1, DriverRules.minutes(10));
        assertNull(DriverRules.minutes(null));
        assertEquals(23.4, DriverRules.kilometres(23_412));
    }

    @Test
    void drv07ManualKilometres() {
        assertEquals(12_500, DriverRules.meters(12.5));
        assertEquals(400, status(() -> DriverRules.meters(0)));
        assertEquals(400, status(() -> DriverRules.meters(1000.1)));
    }

    @Test
    void drv08CalculatedOnceAgainOnlyForOtherPlacesOrAfterAnOutage() {
        assertTrue(DriverRules.needsCalculation(null, null, null, "a\nb", NOW));
        assertFalse(DriverRules.needsCalculation("a\nb", RouteStatus.CALCULATED, NOW, "a\nb", NOW.plusSeconds(86_400)));
        assertFalse(DriverRules.needsCalculation("a\nb", RouteStatus.NOT_FOUND, NOW, "a\nb", NOW.plusSeconds(86_400)));
        assertFalse(DriverRules.needsCalculation("a\nb", RouteStatus.MANUAL, NOW, "a\nb", NOW.plusSeconds(86_400)));
        assertTrue(DriverRules.needsCalculation("a\nb", RouteStatus.MANUAL, NOW, "a\nc", NOW));
        assertFalse(DriverRules.needsCalculation("a\nb", RouteStatus.UNAVAILABLE, NOW, "a\nb", NOW.plusSeconds(599)));
        assertTrue(DriverRules.needsCalculation("a\nb", RouteStatus.UNAVAILABLE, NOW, "a\nb", NOW.plusSeconds(600)));
    }

    @Test
    void drv09And10TotalsWithoutCancelledMatchDays() {
        UUID e1 = UUID.randomUUID();
        UUID e2 = UUID.randomUUID();
        UUID e3 = UUID.randomUUID();
        Map<UUID, DriverRules.Total> totals = DriverRules.totals(List.of(
                new DriverRules.Trip(e1, A, EventStatus.PLANNED, 23_400),
                new DriverRules.Trip(e2, A, EventStatus.PLANNED, null),
                new DriverRules.Trip(e3, A, EventStatus.CANCELLED, 10_000),
                new DriverRules.Trip(e3, B, EventStatus.CANCELLED, 10_000),
                new DriverRules.Trip(e1, C, EventStatus.PLANNED, 23_400)));
        assertEquals(new DriverRules.Total(2, 46_800, 1), totals.get(A));
        assertNull(totals.get(B));
        assertEquals(new DriverRules.Total(1, 46_800, 0), totals.get(C));
        assertNull(DriverRules.credit(10_000, EventStatus.CANCELLED));
    }

    @Test
    void drv11Texts() {
        assertEquals("Anna Muster fährt am Spieltag So 22.11. bei dir mit.",
                DriverRules.passengerJoinedText("Anna Muster", "Spieltag So 22.11."));
        assertEquals("Petra Keller fährt am Spieltag So 22.11. nicht. Du hast keinen Platz mehr in der Fahrerliste.",
                DriverRules.carRemovedText("Petra Keller", "Spieltag So 22.11."));
        assertEquals("Dein Auto hat am Spieltag So 22.11. jetzt 1 Platz.",
                DriverRules.seatsChangedText("Spieltag So 22.11.", 1));
    }
}
