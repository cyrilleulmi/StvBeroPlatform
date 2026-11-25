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
import ch.stvbero.platform.core.season.SeasonDto;
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

    @Inject
    ch.stvbero.platform.core.absence.AbsenceService absences;

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
        Map<UUID, Integer> suTeamIds = SuLink.primaryIds(season.id);
        return teams.stream()
                .filter(ts -> user.allTeams() || mine.containsKey(ts.team.id))
                .map(ts -> TeamDto.of(ts, mine.get(ts.team.id), suTeamIds.get(ts.team.id)))
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
        return new TeamDetailDto(team.id, team.nameIn(season), team.suTeamIdIn(season), mine.orElse(null),
                split.coaches(), LineService.lineup(team, season, memberships),
                user.allTeams() ? team.minimums() : null, user.allTeams() ? team.matchDaySettings() : null);
    }

    /**
     * Change the minimums for the warnings (F-16, criterion 4): coaches (of any team, E-69) and admins. They apply
     * to the team, not per season (assumption).
     */
    @Transactional
    public WarningMinimums setWarningMinimums(CurrentUser user, UUID teamId, WarningMinimums minimums) {
        user.checkAllTeams();
        Team team = find(teamId);
        team.setMinimums(minimums);
        return team.minimums();
    }

    /**
     * Change the match day settings (TML-02, TML-03, AUF-32): coaches (of any team, E-69) and admins. They apply to
     * the team, not per season, and to the timeline of every match day from now on.
     */
    @Transactional
    public MatchDaySettings setMatchDaySettings(CurrentUser user, UUID teamId, MatchDaySettings settings) {
        user.checkAllTeams();
        Team team = find(teamId);
        team.setMatchDaySettings(settings);
        return team.matchDaySettings();
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
        // The four standard event variants exist from the start (VAR-01)
        team.getEntityManager().flush();
        team.getEntityManager().createNativeQuery(ch.stvbero.platform.core.variant.VariantRules.ENSURE_STANDARD_SQL)
                .executeUpdate();
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
        // The practices of the new team in the time of an absence are declined as well (ABS-04)
        absences.applyForPerson(person.id, practice -> false);
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

    /**
     * Taking a person out of a team: admins and coaches (PTF-11, E-103). It is not deleting (RR-08): the person, her
     * responses and her other teams stay.
     */
    @Transactional
    public void removeMember(CurrentUser user, UUID teamId, UUID personId, UUID seasonId) {
        user.checkAllTeams();
        TeamMembership membership = membership(teamId, personId, seasonId);
        membership.season.checkWritable();
        membership.delete();
    }

    // ------------------------------------------------------------------ The whole club (PTF-16, PTF-17)

    /**
     * Everyone of the club with their teams in the season and in the season before (PTF-16). The columns are the
     * teams of the season and, after them, the teams of the season before that are not part of it yet: they join
     * with their first member, so a new season can be filled without creating every team again.
     */
    public MembershipOverviewDto memberships(CurrentUser user, UUID seasonId) {
        user.checkAllTeams();
        Season season = Season.orCurrent(seasonId);
        Season current = Season.current().orElse(null);
        Season previous = Season.<Season>find("startDate < ?1 order by startDate desc", season.startDate)
                .firstResult();
        Comparator<MembershipOverviewDto.TeamColumn> byName = Comparator
                .comparing(MembershipOverviewDto.TeamColumn::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(MembershipOverviewDto.TeamColumn::id);
        List<TeamSeason> inSeason = TeamSeason.list("season.id", season.id);
        List<TeamSeason> inPrevious = previous == null ? List.of() : TeamSeason.list("season.id", previous.id);
        java.util.Set<UUID> here = inSeason.stream().map(ts -> ts.team.id).collect(Collectors.toSet());
        List<MembershipOverviewDto.TeamColumn> teams = new java.util.ArrayList<>(inSeason.stream()
                .map(ts -> new MembershipOverviewDto.TeamColumn(ts.team.id, ts.name, true)).sorted(byName).toList());
        teams.addAll(inPrevious.stream().filter(ts -> !here.contains(ts.team.id))
                .map(ts -> new MembershipOverviewDto.TeamColumn(ts.team.id, ts.name, false)).sorted(byName).toList());
        List<MembershipOverviewDto.TeamColumn> previousTeams = inPrevious.stream()
                .map(ts -> new MembershipOverviewDto.TeamColumn(ts.team.id, ts.name, here.contains(ts.team.id)))
                .sorted(byName).toList();

        Map<UUID, List<MembershipOverviewDto.Entry>> now = entries(season);
        Map<UUID, List<MembershipOverviewDto.Entry>> before = previous == null ? Map.of() : entries(previous);
        List<MembershipOverviewDto.Row> people = Person.<Person>list("order by lower(lastName), lower(firstName), id")
                .stream()
                .map(p -> new MembershipOverviewDto.Row(ch.stvbero.platform.core.person.PersonDto.of(p),
                        now.getOrDefault(p.id, List.of()), before.getOrDefault(p.id, List.of())))
                .toList();
        return new MembershipOverviewDto(SeasonDto.of(season, current),
                previous == null ? null : SeasonDto.of(previous, current), teams, previousTeams, people, user.allTeams(),
                !season.isArchived());
    }

    private static Map<UUID, List<MembershipOverviewDto.Entry>> entries(Season season) {
        return TeamMembership.<TeamMembership>list("season.id", season.id).stream()
                .collect(Collectors.groupingBy(m -> m.person.id,
                        Collectors.mapping(m -> new MembershipOverviewDto.Entry(m.team.id, m.teamRole), Collectors.toList())));
    }

    /**
     * Several changes at once (PTF-17), all or nothing: people join teams (someone who is already in the team gets
     * the new team role) and leave teams (E-103).
     */
    @Transactional
    public MembershipOverviewDto.BatchResult changeMemberships(CurrentUser user, UUID seasonId, MembershipBatchInput input) {
        user.checkAllTeams();
        List<MembershipBatchInput.Add> adds = input.add() == null ? List.of() : input.add();
        List<MembershipBatchInput.Remove> removes = input.remove() == null ? List.of() : input.remove();
        Season season = Season.orCurrent(seasonId);
        season.checkWritable();
        LocalDate today = LocalDate.ofInstant(clock.now(), BackendClock.ZONE);
        int added = 0;
        int changed = 0;
        int removed = 0;
        java.util.Set<UUID> joined = new java.util.LinkedHashSet<>();
        for (MembershipBatchInput.Add add : adds) {
            Team team = find(add.teamId());
            Person person = PersonService.find(add.personId());
            TeamMembership existing = TeamMembership.<TeamMembership>find(
                    "team.id = ?1 and person.id = ?2 and season.id = ?3", team.id, person.id, season.id).firstResult();
            if (existing != null) {
                if (existing.teamRole != add.teamRole()) {
                    existing.teamRole = add.teamRole();
                    // Only players are in a line (LIN-10)
                    if (add.teamRole() != TeamRole.PLAYER) {
                        existing.groups.clear();
                    }
                    changed++;
                }
                continue;
            }
            team.addToSeason(season, latestName(team));
            TeamMembership.create(person, team, season, add.teamRole(), TeamRules.joinedOn(today, season.startDate))
                    .persist();
            Team.getEntityManager().flush();
            joined.add(person.id);
            added++;
        }
        for (MembershipBatchInput.Remove remove : removes) {
            TeamMembership existing = TeamMembership.<TeamMembership>find(
                    "team.id = ?1 and person.id = ?2 and season.id = ?3", remove.teamId(), remove.personId(), season.id)
                    .firstResult();
            if (existing != null) {
                existing.delete();
                removed++;
            }
        }
        // The practices of the new teams in the time of an absence are declined as well (ABS-04)
        for (UUID personId : joined) {
            absences.applyForPerson(personId, practice -> false);
        }
        return new MembershipOverviewDto.BatchResult(added, changed, removed);
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
