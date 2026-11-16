package ch.stvbero.platform.core.team;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.person.PersonSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit-Tests der Teamregeln (PTF-04, PTF-07). */
class TeamRulesTest {

    private static final PersonSummary PETRA = new PersonSummary(UUID.randomUUID(), "Petra", "Keller");
    private static final PersonSummary ANNA = new PersonSummary(UUID.randomUUID(), "Anna", "Muster");
    private static final PersonSummary SARA = new PersonSummary(UUID.randomUUID(), "Sara", "Brun");
    private static final PersonSummary MARC = new PersonSummary(UUID.randomUUID(), "Marc", "Huber");

    @Test
    void ptf04SecondAddInSameTeamIsRejected() {
        List<TeamRules.Member> team = List.of(new TeamRules.Member(PETRA, TeamRole.COACH));

        DomainError f = assertThrows(DomainError.class, () -> TeamRules.checkCanAdd(team, PETRA.id()));

        assertEquals(409, f.status());
        assertEquals("PTF-04", f.rule());
        assertTrue(f.getMessage().contains("Petra Keller ist schon in diesem Team (Coach)"));
    }

    @Test
    void ptf04OtherTeamRoleInOtherTeamIsAllowed() {
        List<TeamRules.Member> otherTeam = List.of(new TeamRules.Member(ANNA, TeamRole.PLAYER));
        assertDoesNotThrow(() -> TeamRules.checkCanAdd(otherTeam, PETRA.id()));
    }

    @Test
    void ptf07CoachesAppearNeverAtThePlayers() {
        TeamRules.Split a = TeamRules.split(List.of(
                new TeamRules.Member(PETRA, TeamRole.COACH),
                new TeamRules.Member(MARC, TeamRole.COACH),
                new TeamRules.Member(ANNA, TeamRole.PLAYER),
                new TeamRules.Member(SARA, TeamRole.PLAYER)));

        assertEquals(List.of(MARC, PETRA), a.coaches());
        assertEquals(List.of(SARA, ANNA), a.players(), "nach Nachname sortiert");
    }
}
