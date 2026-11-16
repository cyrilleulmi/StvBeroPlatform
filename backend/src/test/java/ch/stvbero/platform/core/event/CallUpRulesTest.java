package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.team.TeamRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Domain rules for call-ups and matches (AUF-01, AUF-02, AUF-15, AUF-22 to AUF-28, SUH-06). */
class CallUpRulesTest {

    static final UUID ANNA = UUID.randomUUID();
    static final UUID KIM = UUID.randomUUID();
    static final UUID PETRA = UUID.randomUUID();

    @Test
    void auf02OnlyMatchDaysHaveACallUp() {
        DomainError error = assertThrows(DomainError.class, () -> CallUpRules.checkMatchDay(EventType.PRACTICE));
        assertEquals(409, error.status());
        assertEquals("AUF-02", error.rule());
        assertThrows(DomainError.class, () -> CallUpRules.checkMatchDay(EventType.OTHER));
        assertDoesNotThrow(() -> CallUpRules.checkMatchDay(EventType.MATCH_DAY));
    }

    @Test
    void rr03OnlyCoachesAndAdminsEditTheCallUp() {
        assertTrue(CallUpRules.canEdit(false, List.of(TeamRole.COACH)));
        assertTrue(CallUpRules.canEdit(true, List.of()));
        assertFalse(CallUpRules.canEdit(false, List.of(TeamRole.PLAYER)));
        DomainError error = assertThrows(DomainError.class, () -> CallUpRules.checkCanEdit(false, List.of()));
        assertEquals(403, error.status());
    }

    @Test
    void auf22AndAuf28OnlyPlayersOfTheTeamCanBeCalledUp() {
        Set<UUID> players = Set.of(ANNA, KIM);
        assertDoesNotThrow(() -> CallUpRules.checkCanBeCalledUp(List.of(ANNA, KIM), players));
        assertDoesNotThrow(() -> CallUpRules.checkCanBeCalledUp(List.of(), players));
        DomainError error = assertThrows(DomainError.class, () -> CallUpRules.checkCanBeCalledUp(List.of(ANNA, PETRA), players));
        assertEquals(422, error.status());
        assertEquals("AUF-28", error.rule());
        assertEquals("personIds", error.toProblem().fields().get(0).field());
    }

    @Test
    void auf23StatusAndPublish() {
        assertEquals(CallUpStatus.NONE, CallUpRules.status(false, 0));
        assertEquals(CallUpStatus.DRAFT, CallUpRules.status(false, 3));
        assertEquals(CallUpStatus.PUBLISHED, CallUpRules.status(true, 3));
        assertEquals(CallUpStatus.PUBLISHED, CallUpRules.status(true, 0));

        assertDoesNotThrow(() -> CallUpRules.checkPublishable(CallUpStatus.DRAFT));
        DomainError empty = assertThrows(DomainError.class, () -> CallUpRules.checkPublishable(CallUpStatus.NONE));
        assertEquals(409, empty.status());
        DomainError twice = assertThrows(DomainError.class,
                () -> CallUpRules.checkPublishable(CallUpStatus.PUBLISHED));
        assertEquals(409, twice.status());
        assertEquals("AUF-23", twice.rule());
    }

    @Test
    void auf23OnlyCoachesSeeTheDraft() {
        assertEquals(CallUpStatus.DRAFT, CallUpRules.visibleStatus(CallUpStatus.DRAFT, true));
        assertEquals(CallUpStatus.NONE, CallUpRules.visibleStatus(CallUpStatus.DRAFT, false));
        assertEquals(CallUpStatus.PUBLISHED, CallUpRules.visibleStatus(CallUpStatus.PUBLISHED, false));
    }

    @Test
    void auf24MyCallUpOnlyAfterPublishing() {
        assertEquals(MyCallUp.NO_CALL_UP_YET, CallUpRules.myCallUp(CallUpStatus.NONE, false));
        assertEquals(MyCallUp.NO_CALL_UP_YET, CallUpRules.myCallUp(CallUpStatus.DRAFT, true));
        assertEquals(MyCallUp.CALLED_UP, CallUpRules.myCallUp(CallUpStatus.PUBLISHED, true));
        assertEquals(MyCallUp.NOT_CALLED_UP, CallUpRules.myCallUp(CallUpStatus.PUBLISHED, false));
    }

    // ------------------------------------------------------------------ Matches

    static final Instant MATCH_DAY = Instant.parse("2026-11-22T08:30:00Z"); // Sun 22.11. 09:30 Zurich

    @Test
    void auf01MatchesAreOnTheDayOfTheMatchDay() {
        assertDoesNotThrow(() -> MatchRules.checkOnMatchDay(Instant.parse("2026-11-22T22:30:00Z"), MATCH_DAY)); // 23:30
        DomainError error = assertThrows(DomainError.class,
                () -> MatchRules.checkOnMatchDay(Instant.parse("2026-11-22T23:30:00Z"), MATCH_DAY)); // 00:30 on 23.11.
        assertEquals(400, error.status());
        assertEquals("startTime", error.toProblem().fields().get(0).field());
    }

    @Test
    void o48DayRuleOnUpdateOnlyForANewStartTime() {
        Instant matchOnOldDay = MATCH_DAY;
        Instant matchDayRescheduled = MATCH_DAY.plusSeconds(7 * 24 * 3600);
        // Entering a result: start time unchanged, even if it is not (or no longer) on the day of the match day
        assertDoesNotThrow(() -> MatchRules.checkOnMatchDayWhenChanging(matchOnOldDay, matchOnOldDay, matchDayRescheduled));
        DomainError error = assertThrows(DomainError.class, () -> MatchRules.checkOnMatchDayWhenChanging(matchOnOldDay,
                matchOnOldDay.plusSeconds(60), matchDayRescheduled));
        assertEquals(400, error.status());
        assertEquals("startTime", error.toProblem().fields().get(0).field());
    }

    @Test
    void suh05MatchDayWithImportedMatchesOnlyReschedulableOnSameDay() {
        Instant otherDay = MATCH_DAY.plusSeconds(7 * 24 * 3600);
        assertDoesNotThrow(() -> MatchRules.checkReschedulable(true, MATCH_DAY, MATCH_DAY.plusSeconds(3600)));
        assertDoesNotThrow(() -> MatchRules.checkReschedulable(false, MATCH_DAY, otherDay));
        DomainError error = assertThrows(DomainError.class, () -> MatchRules.checkReschedulable(true, MATCH_DAY, otherDay));
        assertEquals(422, error.status());
        assertEquals("SUH-05", error.rule());
        assertEquals("start", error.toProblem().fields().get(0).field());
    }

    @Test
    void matchesOnlyOnMatchDays() {
        assertDoesNotThrow(() -> MatchRules.checkMatchDay(EventType.MATCH_DAY));
        DomainError error = assertThrows(DomainError.class, () -> MatchRules.checkMatchDay(EventType.PRACTICE));
        assertEquals(409, error.status());
        assertEquals(MatchRules.ONLY_MATCH_DAY, error.getMessage());
    }

    @Test
    void suh06ImportedFieldsAreProtectedButNotTheResult() {
        assertDoesNotThrow(() -> MatchRules.checkChangeable(MatchSource.SWISS_UNIHOCKEY, "UHC Musterdorf", MATCH_DAY, true,
                "UHC Musterdorf", MATCH_DAY, true));
        DomainError error = assertThrows(DomainError.class, () -> MatchRules.checkChangeable(MatchSource.SWISS_UNIHOCKEY,
                "UHC Musterdorf", MATCH_DAY, true, "UHC Anders", MATCH_DAY, true));
        assertEquals(409, error.status());
        assertEquals("SUH-06", error.rule());
        assertThrows(DomainError.class, () -> MatchRules.checkChangeable(MatchSource.SWISS_UNIHOCKEY,
                "UHC Musterdorf", MATCH_DAY, true, "UHC Musterdorf", MATCH_DAY.plusSeconds(60), true));
        assertThrows(DomainError.class, () -> MatchRules.checkChangeable(MatchSource.SWISS_UNIHOCKEY,
                "UHC Musterdorf", MATCH_DAY, true, "UHC Musterdorf", MATCH_DAY, false));
        assertDoesNotThrow(() -> MatchRules.checkChangeable(MatchSource.MANUAL, "A", MATCH_DAY, true, "B",
                MATCH_DAY.plusSeconds(60), false));
    }

    @Test
    void importedMatchesOnlyDeletableWhenSuNoLongerDeliversThem() {
        assertDoesNotThrow(() -> MatchRules.checkDeletable(MatchSource.MANUAL, false));
        assertDoesNotThrow(() -> MatchRules.checkDeletable(MatchSource.SWISS_UNIHOCKEY, true));
        assertEquals(409, assertThrows(DomainError.class,
                () -> MatchRules.checkDeletable(MatchSource.SWISS_UNIHOCKEY, false)).status());
    }

    @Test
    void auf15ResultAsHomeColonAway() {
        assertTrue("5:3".matches(MatchRules.RESULT_PATTERN));
        assertTrue("10:12".matches(MatchRules.RESULT_PATTERN));
        assertFalse("5-3".matches(MatchRules.RESULT_PATTERN));
        assertFalse("123:1".matches(MatchRules.RESULT_PATTERN));
    }

    @Test
    void auf01MatchDayWithMatchesStaysMatchDay() {
        DomainError error = assertThrows(DomainError.class,
                () -> EventRules.checkTypeChange(EventType.MATCH_DAY, EventType.PRACTICE, true));
        assertEquals(409, error.status());
        assertDoesNotThrow(() -> EventRules.checkTypeChange(EventType.MATCH_DAY, EventType.PRACTICE, false));
        assertDoesNotThrow(() -> EventRules.checkTypeChange(EventType.PRACTICE, EventType.OTHER, true));
    }

    @Test
    void cancelledMatchDayAcceptsNoChanges() {
        assertEquals(409, assertThrows(DomainError.class,
                () -> MatchRules.checkNotCancelled(EventStatus.CANCELLED)).status());
        assertDoesNotThrow(() -> MatchRules.checkNotCancelled(EventStatus.PLANNED));
    }
}
