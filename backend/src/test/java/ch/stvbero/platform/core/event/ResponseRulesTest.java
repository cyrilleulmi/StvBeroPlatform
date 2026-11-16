package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.team.TeamRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Domain rules for responses (ANM-04, ANM-05, ANM-07, ANM-10, ANM-11, RR-06, TER-06). */
class ResponseRulesTest {

    static final UUID TEAM_1 = UUID.randomUUID();
    static final UUID TEAM_2 = UUID.randomUUID();
    static final Instant DEADLINE = Instant.parse("2026-11-16T17:30:00Z");
    static final Instant BEFORE_DEADLINE = DEADLINE.minusSeconds(60);

    @Test
    void anm04MaybeRequiresComment() {
        DomainError error = assertThrows(DomainError.class, () -> ResponseRules.checkComment(ResponseStatus.MAYBE, null));
        assertEquals(422, error.status());
        assertEquals("ANM-04", error.rule());
        assertEquals("comment", error.toProblem().fields().get(0).field());
        assertThrows(DomainError.class, () -> ResponseRules.checkComment(ResponseStatus.MAYBE, "   "));
        assertDoesNotThrow(() -> ResponseRules.checkComment(ResponseStatus.MAYBE, "Komme später"));
    }

    @Test
    void anm05DeclineWithoutReasonIsAllowed() {
        assertDoesNotThrow(() -> ResponseRules.checkComment(ResponseStatus.DECLINED, null));
        assertDoesNotThrow(() -> ResponseRules.checkComment(ResponseStatus.ATTENDING, null));
    }

    @Test
    void anm06PendingIsNoResponse() {
        assertEquals(400, assertThrows(DomainError.class,
                () -> ResponseRules.checkComment(ResponseStatus.PENDING, null)).status());
    }

    @Test
    void anm10LeavingAttendingAfterDeadlineIsLate() {
        assertTrue(ResponseRules.lateCancellation(ResponseStatus.ATTENDING, false, ResponseStatus.DECLINED, DEADLINE, DEADLINE));
        assertTrue(ResponseRules.lateCancellation(ResponseStatus.ATTENDING, false, ResponseStatus.MAYBE, DEADLINE, DEADLINE.plusSeconds(3600)));
    }

    @Test
    void anm10BeforeTheDeadlineIsNotLate() {
        assertFalse(ResponseRules.lateCancellation(ResponseStatus.ATTENDING, false, ResponseStatus.DECLINED, DEADLINE, BEFORE_DEADLINE));
    }

    @Test
    void anm10WithoutPreviousAttendingIsNotLate() {
        assertFalse(ResponseRules.lateCancellation(ResponseStatus.PENDING, false, ResponseStatus.DECLINED, DEADLINE, DEADLINE));
        assertFalse(ResponseRules.lateCancellation(ResponseStatus.MAYBE, false, ResponseStatus.DECLINED, DEADLINE, DEADLINE));
    }

    @Test
    void anm10MarkStaysUntilAttendingAgain() {
        assertTrue(ResponseRules.lateCancellation(ResponseStatus.DECLINED, true, ResponseStatus.MAYBE, DEADLINE, DEADLINE));
        assertTrue(ResponseRules.lateCancellation(ResponseStatus.DECLINED, true, ResponseStatus.DECLINED, DEADLINE, DEADLINE));
        assertFalse(ResponseRules.lateCancellation(ResponseStatus.DECLINED, true, ResponseStatus.ATTENDING, DEADLINE, DEADLINE));
    }

    @Test
    void ter06CancelledOrPastEventTakesNoResponse() {
        Instant now = Instant.parse("2026-11-16T05:00:00Z");
        DomainError cancelled = assertThrows(DomainError.class,
                () -> ResponseRules.checkOpenForResponses(EventStatus.CANCELLED, now.plusSeconds(3600), now));
        assertEquals(409, cancelled.status());
        assertEquals(ResponseRules.CANCELLED, cancelled.getMessage());
        assertEquals(409, assertThrows(DomainError.class,
                () -> ResponseRules.checkOpenForResponses(EventStatus.PLANNED, now, now)).status());
        assertDoesNotThrow(() -> ResponseRules.checkOpenForResponses(EventStatus.PLANNED, now.plusSeconds(1), now));
    }

    @Test
    void anm11CoachRespondsForPlayersOfOwnTeams() {
        assertTrue(ResponseRules.canRespondOnBehalf(false, Map.of(TEAM_1, TeamRole.COACH),
                Map.of(TEAM_1, TeamRole.PLAYER)));
        // Lea: player in Team 1 and Team 2, the coach of Team 2 may as well
        assertTrue(ResponseRules.canRespondOnBehalf(false, Map.of(TEAM_2, TeamRole.COACH),
                Map.of(TEAM_1, TeamRole.PLAYER, TEAM_2, TeamRole.PLAYER)));
    }

    @Test
    void rr06NoRespondingOnBehalfForOtherTeams() {
        assertFalse(ResponseRules.canRespondOnBehalf(false, Map.of(TEAM_2, TeamRole.COACH),
                Map.of(TEAM_1, TeamRole.PLAYER)));
        // Petra is a player in Team 2: there she may not respond for others
        assertFalse(ResponseRules.canRespondOnBehalf(false,
                Map.of(TEAM_1, TeamRole.COACH, TEAM_2, TeamRole.PLAYER), Map.of(TEAM_2, TeamRole.PLAYER)));
        // Not for other coaches
        assertFalse(ResponseRules.canRespondOnBehalf(false, Map.of(TEAM_1, TeamRole.COACH), Map.of(TEAM_1, TeamRole.COACH)));
        // Players not at all
        assertEquals(403, assertThrows(DomainError.class, () -> ResponseRules.checkCanRespondOnBehalf(false,
                Map.of(TEAM_1, TeamRole.PLAYER), Map.of(TEAM_1, TeamRole.PLAYER))).status());
    }

    @Test
    void rr04AdminRespondsForEveryMember() {
        assertTrue(ResponseRules.canRespondOnBehalf(true, Map.of(), Map.of(TEAM_1, TeamRole.COACH)));
        assertFalse(ResponseRules.canRespondOnBehalf(true, Map.of(), Map.of()));
    }

    @Test
    void anm07CommentVisibleOnlyToCoachesSelfAndAdmins() {
        assertTrue(ResponseRules.commentVisible(false, false, TeamRole.COACH));
        assertTrue(ResponseRules.commentVisible(false, true, TeamRole.PLAYER));
        assertTrue(ResponseRules.commentVisible(true, false, null));
        assertFalse(ResponseRules.commentVisible(false, false, TeamRole.PLAYER));
        assertFalse(ResponseRules.commentVisible(false, false, null));
    }

    @Test
    void anm01OnlyMembersRespondForThemselves() {
        assertEquals(403, assertThrows(DomainError.class, () -> ResponseRules.checkCanRespondForSelf(false)).status());
        assertDoesNotThrow(() -> ResponseRules.checkCanRespondForSelf(true));
    }
}
