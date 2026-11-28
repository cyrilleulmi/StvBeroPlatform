package ch.stvbero.platform.attendance.calendar;

import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.attendance.event.CallUp;
import ch.stvbero.platform.attendance.event.Event;
import ch.stvbero.platform.attendance.event.EventStatus;
import ch.stvbero.platform.attendance.event.EventType;
import ch.stvbero.platform.attendance.event.Match;
import ch.stvbero.platform.attendance.event.Participation;
import ch.stvbero.platform.attendance.event.ResponseStatus;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.team.TeamMembership;
import ch.stvbero.platform.core.team.TeamRole;
import ch.stvbero.platform.core.team.TeamSeason;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Calendar subscription (CAL): the personal address and the iCalendar feed behind it. The feed is reached with
 * the token alone, without login, and shows only what the person may see anyway (CAL-06, CAL-08).
 */
@ApplicationScoped
public class CalendarService {

    static final String CALENDAR_NAME = "STV Bero";

    private final SecureRandom random = new SecureRandom();

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    /** Address of the app, for the links to the events in the feed (CAL-03). */
    @ConfigProperty(name = "stv.app-url")
    String appUrl;

    /** The person's address; created on first use (CAL-01). */
    @Transactional
    public CalendarSubscriptionDto mine(UUID personId) {
        em.createNativeQuery("""
                        insert into calendar_subscription (person_id, token, created_at) values (?, ?, ?)
                        on conflict (person_id) do nothing""")
                .setParameter(1, personId).setParameter(2, CalendarRules.newToken(random))
                .setParameter(3, clock.now())
                .executeUpdate();
        return dto(personId);
    }

    /** New address; the old one no longer works from now on (CAL-07). */
    @Transactional
    public CalendarSubscriptionDto renew(UUID personId) {
        em.createNativeQuery("""
                        insert into calendar_subscription (person_id, token, created_at) values (?, ?, ?)
                        on conflict (person_id) do update set token = excluded.token, created_at = excluded.created_at""")
                .setParameter(1, personId).setParameter(2, CalendarRules.newToken(random))
                .setParameter(3, clock.now())
                .executeUpdate();
        return dto(personId);
    }

    /**
     * New filter of the feed; the address stays the same (CAL-10 to CAL-14). At least one event type is required.
     * Teams the person is not a member of are ignored.
     */
    @Transactional
    public CalendarSubscriptionDto changeFilter(UUID personId, CalendarFilterInput input) {
        if (input.types().isEmpty()) {
            throw DomainError.ruleViolation("types", "Terminart fehlt", "Wähle mindestens eine Terminart.", "CAL-10");
        }
        mine(personId);
        Set<EventType> types = EnumSet.copyOf(input.types());
        Set<CalendarResponse> responses = input.responses().isEmpty()
                ? EnumSet.noneOf(CalendarResponse.class) : EnumSet.copyOf(input.responses());
        em.createNativeQuery("""
                        update calendar_subscription set include_practice = ?, include_match_day = ?, include_other = ?,
                            include_pending = ?, include_maybe = ?, include_declined = ?, include_optional = ?
                        where person_id = ?""")
                .setParameter(1, types.contains(EventType.PRACTICE))
                .setParameter(2, types.contains(EventType.MATCH_DAY))
                .setParameter(3, types.contains(EventType.OTHER))
                .setParameter(4, responses.contains(CalendarResponse.PENDING))
                .setParameter(5, responses.contains(CalendarResponse.MAYBE))
                .setParameter(6, responses.contains(CalendarResponse.DECLINED))
                .setParameter(7, responses.contains(CalendarResponse.OPTIONAL_PENDING))
                .setParameter(8, personId)
                .executeUpdate();
        em.createNativeQuery("delete from calendar_excluded_team where person_id = ?")
                .setParameter(1, personId).executeUpdate();
        Set<UUID> known = new HashSet<>(memberTeamIds(personId));
        for (UUID teamId : new LinkedHashSet<>(input.excludedTeamIds())) {
            if (known.contains(teamId)) {
                em.createNativeQuery("insert into calendar_excluded_team (person_id, team_id) values (?, ?)")
                        .setParameter(1, personId).setParameter(2, teamId).executeUpdate();
            }
        }
        return dto(personId);
    }

    private CalendarSubscriptionDto dto(UUID personId) {
        CalendarFilter filter = filter(personId);
        List<CalendarFilterDto.Team> teams = new ArrayList<>();
        Season.current().ifPresent(season -> TeamMembership.<TeamMembership>list(
                        "person.id = ?1 and season.id = ?2", personId, season.id).stream()
                .map(m -> m.team.id).distinct()
                .map(teamId -> new CalendarFilterDto.Team(teamId, teamName(teamId, season.id),
                        !filter.excludedTeamIds().contains(teamId)))
                .sorted(Comparator.comparing(CalendarFilterDto.Team::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(teams::add));
        // Excluded teams the person no longer belongs to in the current season stay excluded, but are not shown.
        return new CalendarSubscriptionDto(CalendarRules.feedPath(token(personId)),
                new CalendarFilterDto(
                        filter.types().stream().sorted().toList(),
                        filter.responses().stream().sorted().toList(),
                        teams));
    }

    private String teamName(UUID teamId, UUID seasonId) {
        return TeamSeason.<TeamSeason>find("team.id = ?1 and season.id = ?2", teamId, seasonId).firstResultOptional()
                .map(ts -> ts.name).orElse("");
    }

    /** Teams the person belongs to in any season (excluding a team applies to all seasons). */
    private List<UUID> memberTeamIds(UUID personId) {
        return TeamMembership.<TeamMembership>list("person.id", personId).stream().map(m -> m.team.id).distinct()
                .toList();
    }

    /** The stored filter of the person's address (CAL-14: defaults to everything). */
    CalendarFilter filter(UUID personId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select include_practice, include_match_day, include_other,
                               include_pending, include_maybe, include_declined, include_optional
                        from calendar_subscription where person_id = ?""")
                .setParameter(1, personId).getResultList();
        if (rows.isEmpty()) {
            return CalendarFilter.all();
        }
        Object[] row = rows.getFirst();
        Set<EventType> types = EnumSet.noneOf(EventType.class);
        if ((Boolean) row[0]) types.add(EventType.PRACTICE);
        if ((Boolean) row[1]) types.add(EventType.MATCH_DAY);
        if ((Boolean) row[2]) types.add(EventType.OTHER);
        Set<CalendarResponse> responses = EnumSet.noneOf(CalendarResponse.class);
        if ((Boolean) row[3]) responses.add(CalendarResponse.PENDING);
        if ((Boolean) row[4]) responses.add(CalendarResponse.MAYBE);
        if ((Boolean) row[5]) responses.add(CalendarResponse.DECLINED);
        if ((Boolean) row[6]) responses.add(CalendarResponse.OPTIONAL_PENDING);
        @SuppressWarnings("unchecked")
        List<UUID> excluded = em.createNativeQuery("select team_id from calendar_excluded_team where person_id = ?")
                .setParameter(1, personId).getResultList();
        return new CalendarFilter(types, responses, Set.copyOf(excluded));
    }

    /** The feed for a token; empty if the token is unknown (CAL-06, CAL-07). */
    @Transactional
    public Optional<String> feed(String token) {
        if (!CalendarRules.wellFormed(token)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        List<UUID> persons = em.createNativeQuery("select person_id from calendar_subscription where token = ?")
                .setParameter(1, token).getResultList();
        if (persons.isEmpty()) {
            return Optional.empty();
        }
        Instant now = clock.now();
        return Optional.of(ICalendarWriter.write(CALENDAR_NAME, now, appUrl, entries(persons.getFirst(), now)));
    }

    private String token(UUID personId) {
        return (String) em.createNativeQuery("select token from calendar_subscription where person_id = ?")
                .setParameter(1, personId).getSingleResult();
    }

    /**
     * Events of the person's teams (member in the season of the event, as in the event list, TER-13) that end
     * after «now» minus 30 days, cancelled ones included (CAL-02, CAL-04), and pass the person's filter
     * (CAL-10 to CAL-15). Chronological.
     */
    List<CalendarEntry> entries(UUID personId, Instant now) {
        return entries(personId, now, filter(personId));
    }

    List<CalendarEntry> entries(UUID personId, Instant now, CalendarFilter filter) {
        List<Event> candidates = em.createQuery("""
                        select distinct t from Event t left join fetch t.teams join fetch t.season
                        where exists (select z.id from TeamMembership z
                                      where z.person.id = :person and z.season = t.season and z.team member of t.teams)
                          and t.end > :from
                        order by t.start, t.id""", Event.class)
                .setParameter("person", personId).setParameter("from", CalendarRules.from(now))
                .getResultList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<UUID> seasons = new LinkedHashSet<>();
        candidates.forEach(e -> seasons.add(e.season.id));
        Map<String, String> names = new HashMap<>();
        TeamSeason.<TeamSeason>list("season.id in ?1", seasons)
                .forEach(ts -> names.put(ts.team.id + ":" + ts.season.id, ts.name));
        List<UUID> ids = candidates.stream().map(e -> e.id).toList();

        // Only the person's own response, nothing of others (CAL-08)
        Map<UUID, Object[]> responses = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "select event_id, status, comment from response where person_id = :person and event_id in (:ids)")
                .setParameter("person", personId).setParameter("ids", ids).getResultList();
        rows.forEach(row -> responses.put((UUID) row[0], row));

        // The person's team roles per season and team, for participation and call-up (CAL-11, CAL-12)
        Map<String, TeamRole> roles = new HashMap<>();
        TeamMembership.<TeamMembership>list("person.id = ?1 and season.id in ?2", personId, seasons)
                .forEach(m -> roles.put(m.team.id + ":" + m.season.id, m.teamRole));
        Map<UUID, CallUp> callUps = new HashMap<>();
        List<UUID> matchDayIds = candidates.stream().filter(e -> e.type == EventType.MATCH_DAY).map(e -> e.id).toList();
        if (!matchDayIds.isEmpty()) {
            CallUp.<CallUp>list("eventId in ?1 and publishedAt is not null", matchDayIds)
                    .forEach(c -> callUps.put(c.eventId, c));
        }
        List<Event> events = candidates.stream().filter(event -> {
            List<UUID> myTeams = event.teams.stream().map(t -> t.id)
                    .filter(id -> roles.containsKey(id + ":" + event.season.id)).toList();
            Object[] response = responses.get(event.id);
            ResponseStatus status = response == null ? null : ResponseStatus.valueOf((String) response[1]);
            Boolean calledUp = null;
            CallUp callUp = callUps.get(event.id);
            boolean player = myTeams.stream()
                    .anyMatch(id -> roles.get(id + ":" + event.season.id) == TeamRole.PLAYER);
            if (callUp != null && player) {
                calledUp = callUp.personIds.contains(personId);
            }
            CalendarResponse participation = CalendarFilter.participation(status,
                    Participation.of(myTeams, event.optionalTeamIds), calledUp);
            return filter.includes(event.type, myTeams, participation);
        }).toList();
        if (events.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<CalendarEntry.MatchLine>> matches = new HashMap<>();
        List<UUID> matchDays = events.stream().filter(e -> e.type == EventType.MATCH_DAY).map(e -> e.id).toList();
        if (!matchDays.isEmpty()) {
            Match.<Match>list("event.id in ?1 order by startTime, id", matchDays)
                    .forEach(m -> matches.computeIfAbsent(m.event.id, key -> new ArrayList<>())
                            .add(new CalendarEntry.MatchLine(m.startTime, m.opponent, m.home, m.result)));
        }

        List<CalendarEntry> entries = new ArrayList<>();
        for (Event event : events) {
            List<String> teams = event.teams.stream()
                    .map((Team team) -> names.getOrDefault(team.id + ":" + event.season.id, ""))
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList();
            Object[] response = responses.get(event.id);
            ResponseStatus status = response == null ? ResponseStatus.PENDING : ResponseStatus.valueOf((String) response[1]);
            String comment = response == null ? null : (String) response[2];
            entries.add(new CalendarEntry(event.id, event.type, event.title, event.start, event.end,
                    event.status == EventStatus.CANCELLED, event.cancellationReason, teams,
                    event.location == null ? null : event.location.name,
                    event.location == null ? null : event.location.address,
                    event.location == null ? null : event.location.mapsUrl,
                    event.location == null ? null : event.location.latitude,
                    event.location == null ? null : event.location.longitude,
                    event.meetingPointTime, event.meetingPointLocation, event.notes,
                    matches.getOrDefault(event.id, List.of()), status, comment,
                    event.preTrainingTitle == null ? null
                            : new CalendarEntry.PreTrainingLine(event.preTrainingTitle, event.preTrainingStart)));
        }
        entries.sort(Comparator.comparing(CalendarEntry::begin).thenComparing(CalendarEntry::id));
        return entries;
    }
}
