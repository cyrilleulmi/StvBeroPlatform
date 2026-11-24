package ch.stvbero.platform.attendance.lineup;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.attendance.event.ResponseStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rules of the special line-ups (LIN-13 to LIN-16), without a database. */
class SpecialLineupRulesTest {

    static final UUID A = new UUID(0, 1);
    static final UUID B = new UUID(0, 2);
    static final UUID OTHER = new UUID(0, 9);

    @Test
    void lin14PlayersAtMostOnceAndOnlyOfTheTeam() {
        SpecialLineupRules.checkPlayers(Arrays.asList(A, null, B, null), Set.of(A, B));
        SpecialLineupRules.checkPlayers(List.of(), Set.of());

        DomainError twice = assertThrows(DomainError.class,
                () -> SpecialLineupRules.checkPlayers(Arrays.asList(A, null, A), Set.of(A, B)));
        assertEquals(422, twice.status());
        DomainError stranger = assertThrows(DomainError.class,
                () -> SpecialLineupRules.checkPlayers(List.of(A, OTHER), Set.of(A, B)));
        assertEquals(422, stranger.status());
    }

    @Test
    void lin13AtMostTwelvePerTeam() {
        SpecialLineupRules.checkRoom(11);
        assertEquals(409, assertThrows(DomainError.class, () -> SpecialLineupRules.checkRoom(12)).status());
    }

    @Test
    void lin16APlaceIsAGapWhenItsPlayerIsNotComing() {
        // Empty places are never a gap
        assertFalse(SpecialLineupRules.gap(false, null, true, false));
        // Without a published call-up only a cancellation counts
        assertFalse(SpecialLineupRules.gap(true, ResponseStatus.ATTENDING, false, false));
        assertFalse(SpecialLineupRules.gap(true, ResponseStatus.PENDING, false, false));
        assertFalse(SpecialLineupRules.gap(true, ResponseStatus.MAYBE, false, false));
        assertTrue(SpecialLineupRules.gap(true, ResponseStatus.DECLINED, false, false));
        // With a published call-up, not being called up is a gap too
        assertTrue(SpecialLineupRules.gap(true, ResponseStatus.ATTENDING, true, false));
        assertFalse(SpecialLineupRules.gap(true, ResponseStatus.ATTENDING, true, true));
        assertTrue(SpecialLineupRules.gap(true, ResponseStatus.DECLINED, true, true));
    }
}
