package ch.stvbero.platform.core.line;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.person.PersonSummary;
import ch.stvbero.platform.core.team.TeamRole;
import ch.stvbero.platform.core.team.GroupType;
import ch.stvbero.platform.core.team.TeamDetailDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Unit tests of the line rules (LIN-02, LIN-04, LIN-05, LIN-06, LIN-07, LIN-09, LIN-10). */
class LineRulesTest {

    private static final UUID L1 = UUID.randomUUID();
    private static final UUID L2 = UUID.randomUUID();
    private static final UUID L3 = UUID.randomUUID();
    private static final UUID GOALIES = UUID.randomUUID();

    private static final LineRules.GroupInfo LINE_1 = new LineRules.GroupInfo(L1, GroupType.LINE, "Linie 1", 1);
    private static final LineRules.GroupInfo LINE_2 = new LineRules.GroupInfo(L2, GroupType.LINE, "Linie 2", 2);
    private static final LineRules.GroupInfo GOALIE_GROUP = new LineRules.GroupInfo(GOALIES, GroupType.GOALIES, "Goalies", null);

    private static final PersonSummary ANNA = new PersonSummary(UUID.randomUUID(), "Anna", "Muster");
    private static final PersonSummary MIA = new PersonSummary(UUID.randomUUID(), "Mia", "Frei");
    private static final PersonSummary JANA = new PersonSummary(UUID.randomUUID(), "Jana", "Wyss");
    private static final PersonSummary KIM = new PersonSummary(UUID.randomUUID(), "Kim", "Baumann");

    @Test
    void lin02NewAssignmentReplacesThePrevious() {
        assertEquals(Set.of(L2), LineRules.assign(List.of(L1), L2));
        assertEquals(Set.of(L1), LineRules.assign(List.of(L1), L1));
        assertEquals(Set.of(GOALIES), LineRules.assign(List.of(), GOALIES));
        assertEquals(1, LineRules.MAX_GROUPS_PER_TEAM);
    }

    @Test
    void lin06NoLineRemovesEveryAssignment() {
        assertEquals(Set.of(), LineRules.assign(List.of(L1), null));
    }

    @Test
    void lin04SeveralPreviousAreCutToTheLimit() {
        // If several assignments ever exist (rule relaxed later), only the newest one stays.
        assertEquals(Set.of(L3), LineRules.assign(List.of(L1, L2), L3));
    }

    @Test
    void lin10OnlyPlayersAreAssignable() {
        assertDoesNotThrow(() -> LineRules.checkAssignable(ANNA, TeamRole.PLAYER));
        DomainError error = assertThrows(DomainError.class, () -> LineRules.checkAssignable(ANNA, TeamRole.COACH));
        assertEquals(409, error.status());
        assertEquals("LIN-10", error.rule());
        assertThrows(DomainError.class, () -> LineRules.checkAssignable(ANNA, null));
    }

    @Test
    void lin05GoaliesCannotBeDeleted() {
        DomainError error = assertThrows(DomainError.class, () -> LineRules.checkIsLine(GOALIE_GROUP, "löschen"));
        assertEquals(409, error.status());
        assertEquals("LIN-05", error.rule());
        assertDoesNotThrow(() -> LineRules.checkIsLine(LINE_1, "löschen"));
    }

    @Test
    void lin07OnlyCoachOrAdminCanEdit() {
        assertDoesNotThrow(() -> LineRules.checkCanEdit(false, TeamRole.COACH));
        assertDoesNotThrow(() -> LineRules.checkCanEdit(true, null));
        assertEquals(403, assertThrows(DomainError.class,
                () -> LineRules.checkCanEdit(false, TeamRole.PLAYER)).status());
        assertEquals(403, assertThrows(DomainError.class,
                () -> LineRules.checkCanEdit(false, null)).status());
    }

    @Test
    void onlyMembersAndAdminsCanView() {
        assertDoesNotThrow(() -> LineRules.checkCanView(false, TeamRole.PLAYER));
        assertDoesNotThrow(() -> LineRules.checkCanView(true, null));
        assertEquals(403, assertThrows(DomainError.class, () -> LineRules.checkCanView(false, null)).status());
    }

    @Test
    void lin09NameUniquePerTeamIgnoringCase() {
        List<LineRules.GroupInfo> groups = List.of(LINE_1, LINE_2, GOALIE_GROUP);
        DomainError error = assertThrows(DomainError.class, () -> LineRules.checkName(groups, " linie 2 ", null));
        assertEquals("LIN-09", error.rule());
        assertThrows(DomainError.class, () -> LineRules.checkName(groups, "GOALIES", null));
        assertDoesNotThrow(() -> LineRules.checkName(groups, "Linie 3", null));
        // The group's own name is not «taken»
        assertDoesNotThrow(() -> LineRules.checkName(groups, "Linie 2", L2));
    }

    @Test
    void lin09MoveRenumbers() {
        List<UUID> lines = List.of(L1, L2, L3);
        assertEquals(List.of(L3, L1, L2), LineRules.move(lines, L3, 1));
        assertEquals(List.of(L2, L1, L3), LineRules.move(lines, L1, 2));
        assertEquals(List.of(L2, L3, L1), LineRules.move(lines, L1, 99));
        assertEquals(List.of(L1, L2, L3), LineRules.move(lines, L1, 0));
    }

    @Test
    void displayLinesBySortOrderThenGoaliesThenNoLine() {
        LineRules.GroupInfo front = new LineRules.GroupInfo(L3, GroupType.LINE, "Zuerst", 1);
        LineRules.GroupInfo end = new LineRules.GroupInfo(L1, GroupType.LINE, "Linie 1", 2);
        TeamDetailDto.Lineup lineup = LineRules.buildLineup(
                List.of(GOALIE_GROUP, end, front),
                List.of(ANNA, MIA, JANA, KIM),
                Map.of(ANNA.id(), Set.of(L1), MIA.id(), Set.of(L1), JANA.id(), Set.of(GOALIES)));

        assertEquals(List.of("Zuerst", "Linie 1", "Goalies"), lineup.groups().stream().map(TeamDetailDto.GroupWithPlayers::name).toList());
        // Empty groups stay in the lineup (the lines screen shows them)
        assertEquals(List.of(), lineup.groups().get(0).players());
        // Within a group by last name
        assertEquals(List.of(MIA, ANNA), lineup.groups().get(1).players());
        assertEquals(List.of(JANA), lineup.groups().get(2).players());
        assertEquals(List.of(KIM), lineup.noLine());
    }

    @Test
    void lin06AssignmentToUnknownGroupCountsAsNoLine() {
        TeamDetailDto.Lineup lineup = LineRules.buildLineup(List.of(LINE_1), List.of(ANNA),
                Map.of(ANNA.id(), Set.of(UUID.randomUUID())));
        assertEquals(List.of(ANNA), lineup.noLine());
    }
}
