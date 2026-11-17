package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.error.DomainError;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Domain rules of the attendance check (PRS-02 to PRS-10). */
class AttendanceRulesTest {

    static final UUID ANNA = UUID.randomUUID();
    static final UUID KIM = UUID.randomUUID();
    static final UUID PETRA = UUID.randomUUID();
    static final Instant START = Instant.parse("2026-11-12T18:00:00Z");

    private static DomainError recordable(boolean check, EventStatus status, Instant now) {
        return assertThrows(DomainError.class, () -> AttendanceRules.checkRecordable(check, status, START, now));
    }

    @Test
    void prs07SwitchedOffGives409() {
        DomainError error = recordable(false, EventStatus.PLANNED, START.plusSeconds(3600));
        assertEquals(409, error.status());
        assertEquals("PRS-07", error.rule());
        assertFalse(AttendanceRules.recordable(false, EventStatus.PLANNED, START, START.plusSeconds(3600)));
    }

    @Test
    void prs09CancelledGives409() {
        DomainError error = recordable(true, EventStatus.CANCELLED, START.plusSeconds(3600));
        assertEquals(409, error.status());
        assertEquals("PRS-09", error.rule());
        assertFalse(AttendanceRules.recordable(true, EventStatus.CANCELLED, START, START.plusSeconds(3600)));
    }

    @Test
    void prs02OnlyFromTheStartOn() {
        DomainError error = recordable(true, EventStatus.PLANNED, START.minusSeconds(1));
        assertEquals(409, error.status());
        assertEquals("PRS-02", error.rule());
        assertFalse(AttendanceRules.recordable(true, EventStatus.PLANNED, START, START.minusSeconds(1)));
        // From the start on, and without an end (assumption PRS-02)
        assertDoesNotThrow(() -> AttendanceRules.checkRecordable(true, EventStatus.PLANNED, START, START));
        assertTrue(AttendanceRules.recordable(true, EventStatus.PLANNED, START, START));
        assertTrue(AttendanceRules.recordable(true, EventStatus.PLANNED, START, START.plusSeconds(86400 * 60)));
    }

    @Test
    void prs10OnlyCoachesAndAdmins() {
        assertTrue(AttendanceRules.canRecord(true));
        assertFalse(AttendanceRules.canRecord(false));
        DomainError error = assertThrows(DomainError.class, () -> AttendanceRules.checkCanRecord(false));
        assertEquals(403, error.status());
        assertDoesNotThrow(() -> AttendanceRules.checkCanRecord(true));
    }

    @Test
    void prs05OnlyPlayersOfTheTeams() {
        Set<UUID> players = Set.of(ANNA, KIM);
        assertDoesNotThrow(() -> AttendanceRules.checkOnlyPlayers(List.of(ANNA, KIM), players));
        assertDoesNotThrow(() -> AttendanceRules.checkOnlyPlayers(List.of(), players));
        DomainError error = assertThrows(DomainError.class,
                () -> AttendanceRules.checkOnlyPlayers(List.of(ANNA, PETRA), players));
        assertEquals(422, error.status());
        assertEquals("PRS-05", error.rule());
    }

    @Test
    void eachPersonOnlyOnce() {
        assertDoesNotThrow(() -> AttendanceRules.checkUnique(List.of(ANNA, KIM)));
        DomainError error = assertThrows(DomainError.class, () -> AttendanceRules.checkUnique(List.of(ANNA, ANNA)));
        assertEquals(400, error.status());
    }

    @Test
    void prs03PreselectionFromTheResponse() {
        assertEquals(AttendanceStatus.PRESENT, AttendanceRules.shown(null, ResponseStatus.ATTENDING));
        assertEquals(AttendanceStatus.ABSENT, AttendanceRules.shown(null, ResponseStatus.DECLINED));
        assertEquals(AttendanceStatus.ABSENT, AttendanceRules.shown(null, ResponseStatus.MAYBE));
        assertEquals(AttendanceStatus.ABSENT, AttendanceRules.shown(null, ResponseStatus.PENDING));
    }

    @Test
    void prs04RecordedValueWinsOverTheResponse() {
        assertEquals(AttendanceStatus.ABSENT, AttendanceRules.shown(AttendanceStatus.ABSENT, ResponseStatus.ATTENDING));
        assertEquals(AttendanceStatus.EXCUSED, AttendanceRules.shown(AttendanceStatus.EXCUSED, ResponseStatus.DECLINED));
        assertEquals(AttendanceStatus.PRESENT, AttendanceRules.shown(AttendanceStatus.PRESENT, ResponseStatus.PENDING));
    }

    @Test
    void prs06OwnAttendanceOnlyWhileTheCheckIsOn() {
        assertEquals(AttendanceStatus.EXCUSED, AttendanceRules.mine(true, AttendanceStatus.EXCUSED));
        assertNull(AttendanceRules.mine(true, null));
        assertNull(AttendanceRules.mine(false, AttendanceStatus.PRESENT));
    }
}
