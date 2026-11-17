package ch.stvbero.platform.core.team;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.Unique;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.line.LineService;
import ch.stvbero.platform.core.person.Person;
import ch.stvbero.platform.core.person.PersonSummary;
import ch.stvbero.platform.core.person.PersonService;
import ch.stvbero.platform.core.season.Season;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Teams and team memberships (PTF). Admins and coaches manage them (RR-03, RR-04, PTF-11, E-69); removing a person
 * from a team is deleting and stays with the admins (RR-08).
 */
@ApplicationScoped
public class TeamService {

    @Inject
    BackendClock clock;

    private static final Comparator<TeamDto> BY_NAME = Comparator
            .comparing(TeamDto::name, String.CASE_INSENSITIVE_ORDER).thenComparing(TeamDto::id);

    /** Team memberships by team name in their season (SAI-02). */
    static final Comparator<TeamMembershipDto> BY_TEAM_NAME = Comparator
            .comparing((TeamMembershipDto m) -> m.team().name(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(m -> m.team().id());

    /** Teams of a season. Admins and coaches see all of them, players only their own (PTF-12, RR-03). */
    public List<TeamDto> list(CurrentUser user, UUID seasonId) {
        Season season = Season.orCurrent(seasonId);
        Map<UUID, TeamRole> mine = TeamMembership.<TeamMembership>list("person.id = ?1 and season.id = ?2",
                        user.personId(), season.id).stream()
                .collect(Collectors.toMap(m -> m.team.id, m -> m.teamRole));
        List<TeamSeason> teams = TeamSeason.list("season.id", season.id);
        return teams.stream()
                .filter(ts -> user.allTeams() || mine.containsKey(ts.team.id))
                .map(ts -> TeamDto.of(ts, mine.get(ts.team.id)))
                .sorted(BY_NAME)
                .toList();
    }

    /** Team with coaches and players. Only members of the team in this season, coaches and admins. */
    public TeamDetailDto get(CurrentUser user, UUID teamId, UUID seasonId) {
        Team team = find(teamId);
        Season season = Season.orCurrent(seasonId);
        List<TeamMembership> memberships = inTeam(team, season);
        Optional<TeamRole> mine = memberships.stream()
                .filter(m -> m.person.id.equals(user.personId())).map(m -> m.teamRole).findFirst();
        if (!user.allTeams() && mine.isEmpty()) {
            throw DomainError.forbidden();
        }
        if (!team.belongsTo(season)) {
            throw DomainError.notFound("Dieses Team gibt es in der Saison " + season.name + " nicht.");
        }
        TeamRules.Split split = TeamRules.split(members(memberships));
        // Players by line, then goalies, then «Ohne Linie» (LIN-06, LIN-09)
        return new TeamDetailDto(team.id, team.nameIn(season), team.suTeamId, mine.orElse(null), split.coaches(),
                LineService.lineup(team, season, memberships));
    }

    @Transactional
    public TeamDto create(CurrentUser user, UUID seasonId, TeamInput input) {
        user.checkAllTeams();
        Season season = Season.orCurrent(seasonId);
        season.checkWritable();
        Team team = new Team();
        team.id = UUID.randomUUID();
        team.persist();
        team.addToSeason(season, input.name().strip());
        return TeamDto.of(team.in(season).orElseThrow(), null);
    }

    /**
     * Renames the team. The name only applies in this season (default: the current one). Archived seasons
     * stay unchanged (409, SAI-02).
     */
    @Transactional
    public TeamDto update(CurrentUser user, UUID teamId, UUID seasonId, TeamInput input) {
        user.checkAllTeams();
        Team team = find(teamId);
        Season season = Season.orCurrent(seasonId);
        TeamSeason ts = team.in(season).orElseThrow(
                () -> DomainError.notFound("Dieses Team gibt es in der Saison " + season.name + " nicht."));
        season.checkWritable();
        ts.name = input.name().strip();
        return TeamDto.of(ts, null);
    }

    /** Adds a person with a team role to the team. Exactly one team role per team and season (PTF-04). */
    @Transactional
    public TeamMembershipDto addMember(CurrentUser user, UUID teamId, UUID seasonId, MemberInput input) {
        user.checkAllTeams();
        Team team = find(teamId);
        Season season = Season.orCurrent(seasonId);
        season.checkWritable();
        Person person = PersonService.find(input.personId());
        TeamRules.checkCanAdd(members(inTeam(team, season)), person.id);
        // If the team is new in the season, it takes the name from the latest season it existed in.
        team.addToSeason(season, latestName(team));
        LocalDate today = LocalDate.ofInstant(clock.now(), BackendClock.ZONE);
        TeamMembership membership = TeamMembership.create(person, team, season, input.teamRole(),
                TeamRules.joinedOn(today, season.startDate));
        membership.persist();
        // The same person added twice at the same time (double click): 409 instead of 500 (PTF-04).
        Unique.save(Team.getEntityManager(), "team_membership_one_role",
                () -> DomainError.conflict("Schon im Team", person.firstName + " " + person.lastName
                        + " ist schon in diesem Team. Pro Team gibt es nur eine Funktion.", "PTF-04"));
        return TeamMembershipDto.of(membership);
    }

    @Transactional
    public TeamMembershipDto changeTeamRole(CurrentUser user, UUID teamId, UUID personId, UUID seasonId,
                                                 TeamRole teamRole) {
        user.checkAllTeams();
        TeamMembership membership = membership(teamId, personId, seasonId);
        membership.season.checkWritable();
        membership.teamRole = teamRole;
        // Only players are in a line (LIN-10): switching to coach removes the assignment.
        if (teamRole != TeamRole.PLAYER) {
            membership.groups.clear();
        }
        return TeamMembershipDto.of(membership);
    }

    /** Removing a person from a team counts as deleting: admins only (RR-08, E-69). */
    @Transactional
    public void removeMember(CurrentUser user, UUID teamId, UUID personId, UUID seasonId) {
        user.checkAdmin();
        TeamMembership membership = membership(teamId, personId, seasonId);
        membership.season.checkWritable();
        membership.delete();
    }

    /** Team memberships of the signed-in person in the given season, by team name (PTF-12). */
    public List<TeamMembershipDto> mine(UUID personId, Season season) {
        return TeamMembership.<TeamMembership>list("person.id = ?1 and season.id = ?2", personId, season.id)
                .stream().map(TeamMembershipDto::of).sorted(BY_TEAM_NAME).toList();
    }

    private TeamMembership membership(UUID teamId, UUID personId, UUID seasonId) {
        Team team = find(teamId);
        Season season = Season.orCurrent(seasonId);
        return TeamMembership.<TeamMembership>find("team.id = ?1 and person.id = ?2 and season.id = ?3",
                        team.id, personId, season.id).firstResultOptional()
                .orElseThrow(() -> DomainError.notFound("Diese Person ist in dieser Saison nicht im Team."));
    }

    private static String latestName(Team team) {
        return team.seasons.stream().max(Comparator.comparing((TeamSeason ts) -> ts.season.startDate))
                .map(ts -> ts.name).orElseThrow();
    }

    private static List<TeamMembership> inTeam(Team team, Season season) {
        return TeamMembership.list("team.id = ?1 and season.id = ?2", team.id, season.id);
    }

    private static List<TeamRules.Member> members(List<TeamMembership> memberships) {
        return memberships.stream().map(m -> new TeamRules.Member(PersonSummary.of(m.person), m.teamRole)).toList();
    }

    private static Team find(UUID id) {
        return Team.<Team>findByIdOptional(id)
                .orElseThrow(() -> DomainError.notFound("Dieses Team gibt es nicht."));
    }
}
