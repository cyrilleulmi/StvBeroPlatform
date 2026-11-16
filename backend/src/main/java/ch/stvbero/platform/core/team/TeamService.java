package ch.stvbero.platform.core.team;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.error.Unique;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.line.LineService;
import ch.stvbero.platform.core.person.Person;
import ch.stvbero.platform.core.person.PersonSummary;
import ch.stvbero.platform.core.person.PersonService;
import ch.stvbero.platform.core.season.Season;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Teams und Teamzugehörigkeiten (PTF). Verwalten dürfen nur Admins (RR-04, PTF-11). */
@ApplicationScoped
public class TeamService {

    private static final Comparator<TeamDto> BY_NAME = Comparator
            .comparing(TeamDto::name, String.CASE_INSENSITIVE_ORDER).thenComparing(TeamDto::id);

    /** Teamzugehörigkeiten nach Teamname in ihrer Saison (SAI-02). */
    static final Comparator<TeamMembershipDto> BY_TEAM_NAME = Comparator
            .comparing((TeamMembershipDto z) -> z.team().name(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(z -> z.team().id());

    /** Teams einer Saison. Admins sehen alle, sonst nur die eigenen (PTF-12). */
    public List<TeamDto> list(CurrentUser user, UUID seasonId) {
        Season season = Season.orCurrent(seasonId);
        Map<UUID, TeamRole> mine = TeamMembership.<TeamMembership>list("person.id = ?1 and season.id = ?2",
                        user.personId(), season.id).stream()
                .collect(Collectors.toMap(z -> z.team.id, z -> z.teamRole));
        List<TeamSeason> teams = TeamSeason.list("season.id", season.id);
        return teams.stream()
                .filter(ts -> user.admin() || mine.containsKey(ts.team.id))
                .map(ts -> TeamDto.of(ts, mine.get(ts.team.id)))
                .sorted(BY_NAME)
                .toList();
    }

    /** Team mit Coaches und Spielerinnen. Nur Mitglieder des Teams in dieser Saison und Admins. */
    public TeamDetailDto get(CurrentUser user, UUID teamId, UUID seasonId) {
        Team team = find(teamId);
        Season season = Season.orCurrent(seasonId);
        List<TeamMembership> memberships = inTeam(team, season);
        Optional<TeamRole> mine = memberships.stream()
                .filter(z -> z.person.id.equals(user.personId())).map(z -> z.teamRole).findFirst();
        if (!user.admin() && mine.isEmpty()) {
            throw DomainError.forbidden();
        }
        if (!team.belongsTo(season)) {
            throw DomainError.notFound("Dieses Team gibt es in der Saison " + season.name + " nicht.");
        }
        TeamRules.Split split = TeamRules.split(members(memberships));
        // Spielerinnen nach Linie, dann Goalies, dann «Ohne Linie» (LIN-06, LIN-09)
        return new TeamDetailDto(team.id, team.nameIn(season), team.suTeamId, mine.orElse(null), split.coaches(),
                LineService.lineup(team, season, memberships));
    }

    @Transactional
    public TeamDto create(CurrentUser user, UUID seasonId, TeamInput input) {
        user.checkAdmin();
        Season season = Season.orCurrent(seasonId);
        season.checkWritable();
        Team team = new Team();
        team.id = UUID.randomUUID();
        team.persist();
        team.addToSeason(season, input.name().strip());
        return TeamDto.of(team.in(season).orElseThrow(), null);
    }

    /**
     * Team umbenennen. Der Name gilt nur in dieser Saison (Standard: aktuelle). Archivierte Saisons
     * bleiben unverändert (409, SAI-02).
     */
    @Transactional
    public TeamDto update(CurrentUser user, UUID teamId, UUID seasonId, TeamInput input) {
        user.checkAdmin();
        Team team = find(teamId);
        Season season = Season.orCurrent(seasonId);
        TeamSeason ts = team.in(season).orElseThrow(
                () -> DomainError.notFound("Dieses Team gibt es in der Saison " + season.name + " nicht."));
        season.checkWritable();
        ts.name = input.name().strip();
        return TeamDto.of(ts, null);
    }

    /** Person mit Funktion ins Team aufnehmen. Pro Team und Saison genau eine Funktion (PTF-04). */
    @Transactional
    public TeamMembershipDto addMember(CurrentUser user, UUID teamId, UUID seasonId, MemberInput input) {
        user.checkAdmin();
        Team team = find(teamId);
        Season season = Season.orCurrent(seasonId);
        season.checkWritable();
        Person person = PersonService.find(input.personId());
        TeamRules.checkCanAdd(members(inTeam(team, season)), person.id);
        // Kommt das Team neu in die Saison, übernimmt es den Namen aus der letzten Saison, in der es bestand.
        team.addToSeason(season, lastName(team));
        TeamMembership z = TeamMembership.create(person, team, season, input.teamRole());
        z.persist();
        // Gleichzeitige Aufnahme derselben Person (Doppelklick): 409 statt 500 (PTF-04).
        Unique.save(Team.getEntityManager(), "team_membership_one_role",
                () -> DomainError.conflict("Schon im Team", person.firstName + " " + person.lastName
                        + " ist schon in diesem Team. Pro Team gibt es nur eine Funktion.", "PTF-04"));
        return TeamMembershipDto.of(z);
    }

    @Transactional
    public TeamMembershipDto changeTeamRole(CurrentUser user, UUID teamId, UUID personId, UUID seasonId,
                                                 TeamRole teamRole) {
        user.checkAdmin();
        TeamMembership z = membership(teamId, personId, seasonId);
        z.season.checkWritable();
        z.teamRole = teamRole;
        // Nur Spielerinnen sind in einer Linie (LIN-10): Beim Wechsel zur Coach entfällt die Zuweisung.
        if (teamRole != TeamRole.PLAYER) {
            z.groups.clear();
        }
        return TeamMembershipDto.of(z);
    }

    @Transactional
    public void removeMember(CurrentUser user, UUID teamId, UUID personId, UUID seasonId) {
        user.checkAdmin();
        TeamMembership z = membership(teamId, personId, seasonId);
        z.season.checkWritable();
        z.delete();
    }

    /** Teamzugehörigkeiten der angemeldeten Person in der aktuellen Saison, nach Teamname (PTF-12). */
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

    private static String lastName(Team team) {
        return team.seasons.stream().max(Comparator.comparing((TeamSeason ts) -> ts.season.startDate))
                .map(ts -> ts.name).orElseThrow();
    }

    private static List<TeamMembership> inTeam(Team team, Season season) {
        return TeamMembership.list("team.id = ?1 and season.id = ?2", team.id, season.id);
    }

    private static List<TeamRules.Member> members(List<TeamMembership> memberships) {
        return memberships.stream().map(z -> new TeamRules.Member(PersonSummary.of(z.person), z.teamRole)).toList();
    }

    private static Team find(UUID id) {
        return Team.<Team>findByIdOptional(id)
                .orElseThrow(() -> DomainError.notFound("Dieses Team gibt es nicht."));
    }
}
