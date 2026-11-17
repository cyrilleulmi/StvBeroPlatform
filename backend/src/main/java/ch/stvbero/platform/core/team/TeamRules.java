package ch.stvbero.platform.core.team;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.person.PersonSummary;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Domain rules for teams and team memberships. Without a database, so that unit tests can check them. */
public final class TeamRules {

    private TeamRules() {
    }

    /** An existing membership in the team (in the season). */
    public record Member(PersonSummary person, TeamRole teamRole) {
    }

    /** Coaches and players separately, each sorted by last name, then first name. */
    public record Split(List<PersonSummary> coaches, List<PersonSummary> players) {
    }

    /** Exactly one team role per person and team (PTF-04): adding the person a second time is rejected. */
    public static void checkCanAdd(Collection<Member> inTeam, UUID personId) {
        Optional<Member> already = inTeam.stream().filter(m -> m.person().id().equals(personId)).findFirst();
        if (already.isPresent()) {
            Member m = already.get();
            throw DomainError.conflict("Schon im Team",
                    m.person().firstName() + " " + m.person().lastName() + " ist schon in diesem Team ("
                            + label(m.teamRole()) + "). Pro Team gibt es nur eine Funktion.", "PTF-04");
        }
    }

    /**
     * Coaches never appear among the players (PTF-07). Only the team role PLAYER counts as a player;
     * further team roles (PTF-06) appear in neither list.
     */
    public static Split split(Collection<Member> members) {
        List<PersonSummary> coaches = members.stream().filter(m -> m.teamRole() == TeamRole.COACH)
                .map(Member::person).sorted(PersonSummary.ORDER).toList();
        List<PersonSummary> players = members.stream().filter(m -> m.teamRole() == TeamRole.PLAYER)
                .map(Member::person).sorted(PersonSummary.ORDER).toList();
        return new Split(coaches, players);
    }

    static String label(TeamRole teamRole) {
        return switch (teamRole) {
            case PLAYER -> "Spielerin";
            case COACH -> "Coach";
        };
    }

    /**
     * Day a person added to a team joins it (STA-07): today, or the start of the season if it has not begun yet
     * (adding to a planned season). Assumption: the day of adding is the day of joining.
     */
    public static LocalDate joinedOn(LocalDate today, LocalDate seasonStart) {
        return today.isBefore(seasonStart) ? seasonStart : today;
    }
}
