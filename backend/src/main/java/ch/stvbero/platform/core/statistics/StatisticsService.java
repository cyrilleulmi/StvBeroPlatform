package ch.stvbero.platform.core.statistics;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.event.AttendanceStatus;
import ch.stvbero.platform.core.event.EventType;
import ch.stvbero.platform.core.event.OverviewDto;
import ch.stvbero.platform.core.event.ResponseStatus;
import ch.stvbero.platform.core.line.Group;
import ch.stvbero.platform.core.person.Person;
import ch.stvbero.platform.core.person.PersonSummary;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.team.GroupType;
import ch.stvbero.platform.core.team.TeamMembership;
import ch.stvbero.platform.core.team.TeamRole;
import ch.stvbero.platform.core.team.TeamSeason;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Attendance statistics (STA): per player of a team the responses and, where recorded, the attendance of the past
 * events in a period. Team statistics for coaches and admins, own statistics for every player (STA-08). Read only.
 */
@ApplicationScoped
public class StatisticsService {

    private static final Comparator<OverviewDto.TeamName> BY_NAME = Comparator
            .comparing(OverviewDto.TeamName::name, String.CASE_INSENSITIVE_ORDER).thenComparing(OverviewDto.TeamName::id);

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    /** One event that counts, with the data the lists need. */
    private record Counted(StatisticsRules.CountedEvent event, EventType type, String title, Instant start) {
    }

    /** Team statistics (STA-01 to STA-05): players by line, with the team average. */
    public StatisticsDto.TeamStatistics team(CurrentUser user, UUID teamId, LocalDate from, LocalDate to, EventType type) {
        StatisticsRules.checkCanSeeTeam(user.allTeams());
        Season season = season(from);
        StatisticsRules.Period period = StatisticsRules.period(from, to, season.name, season.startDate, season.endDate);
        List<OverviewDto.TeamName> teams = teams(season);
        OverviewDto.TeamName team = teamId != null ? find(teams, teamId, season) : defaultTeam(user, season, teams);
        StatisticsDto.SeasonRef seasonRef = seasonRef(season);
        if (team == null) {
            return new StatisticsDto.TeamStatistics(null, teams, seasonRef, period.from(), period.to(), type, 0, 0,
                    List.of(), null);
        }
        List<Counted> events = events(team.id(), season, period, type);
        List<StatisticsRules.CountedEvent> counted = events.stream().map(Counted::event).toList();
        Map<UUID, Map<UUID, StatisticsRules.Fact>> facts = facts(counted, null);

        List<TeamMembership> players = TeamMembership.list("team.id = ?1 and season.id = ?2 and teamRole = ?3",
                team.id(), season.id, TeamRole.PLAYER);
        Map<UUID, List<StatisticsDto.Player>> perGroup = new LinkedHashMap<>();
        List<StatisticsDto.Player> noLine = new ArrayList<>();
        List<StatisticsRules.Numbers> all = new ArrayList<>();
        for (TeamMembership m : players) {
            StatisticsRules.Numbers numbers = StatisticsRules.numbers(
                    StatisticsRules.countedFor(counted, m.joinedOn), facts.getOrDefault(m.person.id, Map.of()));
            all.add(numbers);
            StatisticsDto.Player row = StatisticsDto.Player.of(PersonSummary.of(m.person), m.joinedOn, numbers);
            if (m.groups.isEmpty()) {
                noLine.add(row);
            } else {
                m.groups.forEach(g -> perGroup.computeIfAbsent(g.id, k -> new ArrayList<>()).add(row));
            }
        }
        List<StatisticsDto.Group> groups = new ArrayList<>();
        ch.stvbero.platform.core.team.Team entity = ch.stvbero.platform.core.team.Team.findById(team.id());
        for (Group g : Group.of(entity, season)) {
            List<StatisticsDto.Player> rows = perGroup.get(g.id);
            if (rows != null && !rows.isEmpty()) {
                groups.add(new StatisticsDto.Group(g.id, g.type == GroupType.GOALIES
                        ? OverviewDto.Type.GOALIES : OverviewDto.Type.LINE, g.name, sorted(rows)));
            }
        }
        if (!noLine.isEmpty()) {
            groups.add(new StatisticsDto.Group(null, OverviewDto.Type.NO_LINE, "Ohne Linie", sorted(noLine)));
        }
        int attendanceEvents = (int) counted.stream().filter(StatisticsRules.CountedEvent::attendanceRecorded).count();
        return new StatisticsDto.TeamStatistics(team, teams, seasonRef, period.from(), period.to(), type,
                counted.size(), attendanceEvents, groups, StatisticsDto.Average.of(StatisticsRules.average(all)));
    }

    /** Statistics of one player with her events (STA-06); only herself, coaches and admins (STA-08). */
    public StatisticsDto.PersonStatistics person(CurrentUser user, UUID personId, UUID teamId, LocalDate from,
                                                 LocalDate to, EventType type) {
        StatisticsRules.checkCanSee(user.allTeams(), user.personId(), personId);
        Person person = Person.<Person>findByIdOptional(personId)
                .orElseThrow(() -> DomainError.notFound("Diese Person gibt es nicht."));
        Season season = season(from);
        StatisticsRules.Period period = StatisticsRules.period(from, to, season.name, season.startDate, season.endDate);
        List<TeamMembership> memberships = TeamMembership.list("person.id = ?1 and season.id = ?2 and teamRole = ?3",
                personId, season.id, TeamRole.PLAYER);
        Map<UUID, OverviewDto.TeamName> names = new HashMap<>();
        teams(season).forEach(t -> names.put(t.id(), t));
        List<OverviewDto.TeamName> teams = memberships.stream().map(m -> names.get(m.team.id))
                .filter(t -> t != null).sorted(BY_NAME).toList();
        StatisticsDto.SeasonRef seasonRef = seasonRef(season);
        if (teams.isEmpty()) {
            return new StatisticsDto.PersonStatistics(PersonSummary.of(person), null, teams, seasonRef,
                    period.from(), period.to(), type, null, List.of());
        }
        OverviewDto.TeamName team = teamId == null ? teams.get(0) : teams.stream()
                .filter(t -> t.id().equals(teamId)).findFirst()
                .orElseThrow(() -> DomainError.notFound(person.firstName + " " + person.lastName
                        + " spielt in der Saison " + season.name + " nicht in diesem Team."));
        TeamMembership membership = memberships.stream().filter(m -> m.team.id.equals(team.id())).findFirst().orElseThrow();

        List<Counted> events = events(team.id(), season, period, type).stream()
                .filter(e -> !e.event().date().isBefore(membership.joinedOn)).toList();
        List<StatisticsRules.CountedEvent> counted = events.stream().map(Counted::event).toList();
        Map<UUID, StatisticsRules.Fact> facts = facts(counted, personId).getOrDefault(personId, Map.of());
        StatisticsRules.Numbers numbers = StatisticsRules.numbers(counted, facts);
        List<StatisticsDto.EventEntry> entries = new ArrayList<>();
        for (int i = events.size() - 1; i >= 0; i--) {
            Counted e = events.get(i);
            StatisticsRules.Fact fact = facts.get(e.event().id());
            entries.add(new StatisticsDto.EventEntry(e.event().id(), e.type(), e.title(),
                    OffsetDateTime.ofInstant(e.start(), BackendClock.ZONE),
                    fact == null || fact.response() == null ? ResponseStatus.PENDING : fact.response(),
                    fact != null && fact.lateCancellation(),
                    fact != null && e.event().attendanceRecorded() ? fact.attendance() : null));
        }
        return new StatisticsDto.PersonStatistics(PersonSummary.of(person), team, teams, seasonRef, period.from(),
                period.to(), type, StatisticsDto.Player.of(PersonSummary.of(person), membership.joinedOn, numbers),
                entries);
    }

    /** The season containing {@code from}, otherwise the current one (STA-02). */
    private static Season season(LocalDate from) {
        if (from == null) {
            return Season.currentOrFail();
        }
        Optional<Season> season = Season.find("startDate <= ?1 and endDate >= ?1", from).firstResultOptional();
        return season.orElseThrow(() -> DomainError.invalidField("from", StatisticsRules.NO_SEASON));
    }

    private static StatisticsDto.SeasonRef seasonRef(Season season) {
        return new StatisticsDto.SeasonRef(season.id, season.name, season.startDate, season.endDate);
    }

    /** All teams of the season by name (coaches and admins see all teams, F-12). */
    private static List<OverviewDto.TeamName> teams(Season season) {
        List<TeamSeason> teams = TeamSeason.list("season.id", season.id);
        return teams.stream().map(ts -> new OverviewDto.TeamName(ts.team.id, ts.name)).sorted(BY_NAME).toList();
    }

    private static OverviewDto.TeamName find(List<OverviewDto.TeamName> teams, UUID teamId, Season season) {
        return teams.stream().filter(t -> t.id().equals(teamId)).findFirst()
                .orElseThrow(() -> DomainError.notFound("Dieses Team gibt es in der Saison " + season.name + " nicht."));
    }

    /** Without a choice: the first team the person coaches, otherwise the first team by name. */
    private static OverviewDto.TeamName defaultTeam(CurrentUser user, Season season, List<OverviewDto.TeamName> teams) {
        List<TeamMembership> coached = TeamMembership.list("person.id = ?1 and season.id = ?2 and teamRole = ?3",
                user.personId(), season.id, TeamRole.COACH);
        return teams.stream().filter(t -> coached.stream().anyMatch(m -> m.team.id.equals(t.id()))).findFirst()
                .orElse(teams.isEmpty() ? null : teams.get(0));
    }

    /**
     * Events of the team that count (STA-07): in the season and the period, already started (backend clock), not
     * cancelled, optionally of one type; chronological.
     */
    private List<Counted> events(UUID teamId, Season season, StatisticsRules.Period period, EventType type) {
        Instant start = period.from().atStartOfDay(BackendClock.ZONE).toInstant();
        Instant end = period.to().plusDays(1).atStartOfDay(BackendClock.ZONE).toInstant();
        Instant now = clock.now();
        var query = em.createNativeQuery("""
                        select e.id, e.starts_at, e.type, e.title,
                               e.attendance_check and exists (select 1 from attendance_record r where r.event_id = e.id)
                        from event e join event_team et on et.event_id = e.id
                        where et.team_id = ?1 and e.season_id = ?2 and e.status = 'PLANNED'
                          and not exists (select 1 from event_optional_team o
                                          where o.event_id = e.id and o.team_id = et.team_id)
                          and e.starts_at >= ?3 and e.starts_at < ?4 and e.starts_at <= ?5
                        """ + (type == null ? "" : " and e.type = ?6 ") + "order by e.starts_at, e.id")
                .setParameter(1, teamId).setParameter(2, season.id).setParameter(3, start).setParameter(4, end)
                .setParameter(5, now);
        if (type != null) {
            query.setParameter(6, type.name());
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<Counted> events = new ArrayList<>();
        for (Object[] row : rows) {
            Instant startsAt = instant(row[1]);
            events.add(new Counted(new StatisticsRules.CountedEvent((UUID) row[0],
                    LocalDate.ofInstant(startsAt, BackendClock.ZONE), Boolean.TRUE.equals(row[4])),
                    EventType.valueOf((String) row[2]), (String) row[3], startsAt));
        }
        return events;
    }

    /** Person → event → response, late cancellation and attendance; {@code person} narrows it down to one. */
    private Map<UUID, Map<UUID, StatisticsRules.Fact>> facts(List<StatisticsRules.CountedEvent> events, UUID person) {
        Map<UUID, Map<UUID, StatisticsRules.Fact>> facts = new HashMap<>();
        if (events.isEmpty()) {
            return facts;
        }
        List<UUID> ids = events.stream().map(StatisticsRules.CountedEvent::id).toList();
        String onePerson = person == null ? "" : " and person_id = :person";
        var responses = em.createNativeQuery("select event_id, person_id, status, late_cancellation from response "
                + "where event_id in (:ids)" + onePerson).setParameter("ids", ids);
        var attendance = em.createNativeQuery("select event_id, person_id, status from attendance_entry "
                + "where event_id in (:ids)" + onePerson).setParameter("ids", ids);
        if (person != null) {
            responses.setParameter("person", person);
            attendance.setParameter("person", person);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> responseRows = responses.getResultList();
        for (Object[] row : responseRows) {
            facts.computeIfAbsent((UUID) row[1], k -> new HashMap<>()).put((UUID) row[0],
                    new StatisticsRules.Fact(ResponseStatus.valueOf((String) row[2]), Boolean.TRUE.equals(row[3]), null));
        }
        @SuppressWarnings("unchecked")
        List<Object[]> attendanceRows = attendance.getResultList();
        for (Object[] row : attendanceRows) {
            Map<UUID, StatisticsRules.Fact> perEvent = facts.computeIfAbsent((UUID) row[1], k -> new HashMap<>());
            StatisticsRules.Fact fact = perEvent.get((UUID) row[0]);
            AttendanceStatus status = AttendanceStatus.valueOf((String) row[2]);
            perEvent.put((UUID) row[0], fact == null ? new StatisticsRules.Fact(ResponseStatus.PENDING, false, status)
                    : new StatisticsRules.Fact(fact.response(), fact.lateCancellation(), status));
        }
        return facts;
    }

    private static List<StatisticsDto.Player> sorted(List<StatisticsDto.Player> rows) {
        return rows.stream().sorted(Comparator.comparing(StatisticsDto.Player::person, PersonSummary.ORDER)).toList();
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof OffsetDateTime o) {
            return o.toInstant();
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        throw new IllegalStateException("Unexpected time value " + value);
    }
}
