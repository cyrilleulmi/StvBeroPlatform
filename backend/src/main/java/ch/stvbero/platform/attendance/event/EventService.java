package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.team.TeamRole;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.attendance.variant.EventVariant;
import ch.stvbero.platform.attendance.variant.VariantRules;
import ch.stvbero.platform.core.team.TeamMembership;
import ch.stvbero.platform.core.clock.BackendClock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Create, change, reschedule and cancel events (TER). Coaches (of any team, E-69) and admins may create and edit
 * (TER-11, RR-03, RR-06); all members of the participating teams may view.
 */
@ApplicationScoped
public class EventService {

    public static final String UPCOMING = "upcoming";
    public static final String PAST = "past";

    @Inject
    EntityManager em;

    /** The module that knows the league table, if there is one (AUF-34). */
    @Inject
    Instance<Standings.Source> league;

    @Inject
    BackendClock clock;

    @Inject
    EventNotifications notifications;

    @Inject
    ch.stvbero.platform.attendance.absence.AbsenceService absences;

    /**
     * Events of the caller's own teams (TER-13). Upcoming ones ascending from "now", past ones descending.
     * Own teams means: member of a participating team in the season of the event. With a team filter, admins
     * and coaches see the events of that team even without being a member (RR-03, E-69).
     */
    public List<EventSummaryDto> list(CurrentUser user, LocalDate from, LocalDate to, UUID teamId, EventType type,
                                     String view) {
        boolean past = PAST.equals(view);
        if (view != null && !past && !UPCOMING.equals(view)) {
            throw DomainError.invalidField("view", "Die Ansicht ist «kommend» oder «vergangen».");
        }
        Instant now = clock.now();
        StringBuilder jpql = new StringBuilder("""
                select distinct t from Event t left join fetch t.teams join fetch t.season
                where 1 = 1
                """);
        Map<String, Object> parameters = new HashMap<>();
        if (teamId == null || !user.allTeams()) {
            jpql.append("""
                     and exists (select z.id from TeamMembership z
                                 where z.person.id = :person and z.season = t.season and z.team member of t.teams)
                    """);
            parameters.put("person", user.personId());
        }
        parameters.put("now", now);
        jpql.append(past ? " and t.end <= :now" : " and t.end > :now");
        if (from != null) {
            jpql.append(" and t.start >= :startDate");
            parameters.put("startDate", from.atStartOfDay(BackendClock.ZONE).toInstant());
        }
        if (to != null) {
            jpql.append(" and t.start < :endDate");
            parameters.put("endDate", to.plusDays(1).atStartOfDay(BackendClock.ZONE).toInstant());
        }
        if (teamId != null) {
            jpql.append(" and exists (select x.id from Event t2 join t2.teams x where t2 = t and x.id = :team)");
            parameters.put("team", teamId);
        }
        if (type != null) {
            jpql.append(" and t.type = :type");
            parameters.put("type", type);
        }
        jpql.append(past ? " order by t.start desc, t.id" : " order by t.start, t.id");
        TypedQuery<Event> query = em.createQuery(jpql.toString(), Event.class);
        parameters.forEach(query::setParameter);
        List<Event> events = query.getResultList();
        EventReader reader = new EventReader(em, user, events, now, Standings.sourceOf(league));
        return events.stream().map(reader::summary).toList();
    }

    public EventDto get(CurrentUser user, UUID eventId) {
        Event event = find(eventId);
        EventReader reader = new EventReader(em, user, List.of(event), clock.now(), Standings.sourceOf(league));
        Set<TeamRole> mine = reader.myTeamRoles(event);
        EventRules.checkCanView(user.allTeams(), mine);
        return reader.full(event, canEdit(user, event, mine), clock.now());
    }

    /** Create a single event, also for several teams (TER-01, TER-04, TER-11). */
    @Transactional
    public EventDto create(CurrentUser user, EventInput input) {
        Instant start = input.start().toInstant();
        Instant end = input.end().toInstant();
        Set<Team> teams = teams(input.teamIds());
        Season season = seasonOn(EventRules.date(start), null);
        EventRules.checkCanCreate(user.allTeams(), teamRoles(user, teams, season));
        checkInput(input.type(), start, end, teams, season);
        Event event = Event.create();
        event.season = season;
        apply(event, input, teams);
        event.persist();
        // A practice in the time of an absence is declined by itself (ABS-04)
        absences.applyForEvents(java.util.List.of(event.id));
        return get(user, event.id);
    }

    /**
     * Change an event. A different start time is a rescheduling (TER-06). For a series event the change applies
     * only to this event; it no longer takes over later changes of the series. Responses are kept.
     */
    @Transactional
    public EventDto update(CurrentUser user, UUID eventId, EventInput input) {
        Event event = find(eventId);
        EventRules.checkCanCreate(user.allTeams(), teamRoles(user, event.teams, event.season));
        EventRules.checkChangeable(event.status, event.end, clock.now());
        Instant start = input.start().toInstant();
        Instant end = input.end().toInstant();
        Set<Team> teams = teams(input.teamIds());
        if (user.eventGrant()) {
            // A special permission does not reach the teams of the event (RR-11)
            Set<UUID> optional = new java.util.HashSet<>(input.optionalTeamIds() == null ? List.of() : input.optionalTeamIds());
            if (!teams.equals(event.teams) || !optional.equals(event.optionalTeamIds)) {
                throw DomainError.ruleViolation("teamIds", "Teams unverändert",
                        "Die Teams eines Termins ändern nur Coaches.", "RR-11");
            }
        }
        Season season = seasonOn(EventRules.date(start), event.season);
        // After the change, the person must still be coach of a participating team.
        EventRules.checkCanCreate(user.allTeams(), teamRoles(user, teams, season));
        checkInput(input.type(), start, end, teams, season);
        List<Match> matches = event.type == EventType.MATCH_DAY ? Match.of(event.id) : List.of();
        EventRules.checkTypeChange(event.type, input.type(), !matches.isEmpty() || CallUp.findById(event.id) != null);
        MatchRules.checkReschedulable(matches.stream().anyMatch(match -> match.source == MatchSource.SWISS_UNIHOCKEY),
                event.start, start);
        Instant oldStart = event.start;
        String oldLabel = EventNotifications.label(event);
        List<UUID> previousMembers = EventNotifications.members(event);
        long days = ChronoUnit.DAYS.between(EventRules.date(event.start), EventRules.date(start));
        if (days != 0) {
            // Matches created by hand move along when the match day is moved to another day (AUF-01).
            matches.stream().filter(match -> match.source == MatchSource.MANUAL)
                    .forEach(match -> match.startTime = match.startTime.atZone(BackendClock.ZONE).plusDays(days).toInstant());
        }
        event.rescheduled = EventRules.rescheduled(event.rescheduled, event.start, start);
        event.season = season;
        apply(event, input, teams);
        if (event.series != null) {
            event.individuallyChanged = true;
        }
        if (NotificationRules.rescheduled(oldStart, event.start)) {
            notifications.rescheduled(user, event, oldLabel, previousMembers);
        }
        absences.applyForEvents(java.util.List.of(event.id));
        return get(user, event.id);
    }

    /**
     * Cancel an event (TER-06). Afterwards no changes and no responses are possible. All members of the
     * participating teams are notified (BEN-07).
     */
    @Transactional
    public EventDto cancel(CurrentUser user, UUID eventId, String reason) {
        Event event = find(eventId);
        EventRules.checkCanCreate(user.allTeams(), teamRoles(user, event.teams, event.season));
        EventRules.checkChangeable(event.status, event.end, clock.now());
        event.season.checkWritable();
        event.status = EventStatus.CANCELLED;
        event.cancellationReason = reason.strip();
        notifications.cancelled(user, event);
        return get(user, event.id);
    }

    // ------------------------------------------------------------------ shared with SeriesService

    static Event find(UUID id) {
        return Event.<Event>findByIdOptional(id)
                .orElseThrow(() -> DomainError.notFound("Diesen Termin gibt es nicht."));
    }

    /** Teams for the IDs. Unknown IDs: 400. */
    static Set<Team> teams(Collection<UUID> ids) {
        Set<Team> teams = new LinkedHashSet<>();
        for (UUID id : new LinkedHashSet<>(ids)) {
            teams.add(Team.<Team>findByIdOptional(id)
                    .orElseThrow(() -> DomainError.invalidField("teamIds", "Dieses Team gibt es nicht.")));
        }
        return teams;
    }

    /** Team roles of the person in these teams in this season (empty if she is a member of none of them). */
    static Set<TeamRole> teamRoles(CurrentUser user, Collection<Team> teams, Season season) {
        if (teams.isEmpty()) {
            return Set.of();
        }
        Set<TeamRole> teamRoles = new LinkedHashSet<>();
        TeamMembership.<TeamMembership>list("person.id = ?1 and season.id = ?2 and team.id in ?3",
                        user.personId(), season.id, teams.stream().map(team -> team.id).toList())
                .forEach(m -> teamRoles.add(m.teamRole));
        return teamRoles;
    }

    /** The season the day falls into; otherwise the given one, or the current one. */
    static Season seasonOn(LocalDate day, Season otherwise) {
        return Season.<Season>find("startDate <= ?1 and endDate >= ?1", day).firstResultOptional()
                .orElseGet(() -> otherwise != null ? otherwise : Season.currentOrFail());
    }

    /** All teams belong to the club in the season, and the season is not archived (SAI-02). */
    static void checkTeamsInSeason(Collection<Team> teams, Season season) {
        for (Team team : teams) {
            if (!team.belongsTo(season)) {
                throw DomainError.invalidField("teamIds", "Ein Team gibt es in der Saison " + season.name + " nicht.");
            }
        }
        season.checkWritable();
    }

    private boolean canEdit(CurrentUser user, Event event, Set<TeamRole> mine) {
        return EventRules.canEdit(user.allTeams(), mine) && event.status == EventStatus.PLANNED
                && !event.isOver(clock.now()) && !event.season.isArchived();
    }

    /** The chosen variant fits the type and belongs to one of the teams (VAR-03); {@code null} stays {@code null}. */
    private static UUID variant(UUID variantId, EventType type, Set<Team> teams) {
        if (variantId == null) {
            return null;
        }
        EventVariant variant = EventVariant.<EventVariant>findByIdOptional(variantId)
                .orElseThrow(() -> DomainError.invalidField("variantId", "Diese Terminvariante gibt es nicht."));
        VariantRules.checkFits(variant.eventType, type, teams.stream().anyMatch(team -> team.id.equals(variant.teamId)));
        return variant.id;
    }

    private void checkInput(EventType type, Instant start, Instant end, Set<Team> teams, Season season) {
        EventRules.checkTime(start, end);
        EventRules.checkNotOver(end, clock.now());
        EventRules.checkTeams(type, teams.size());
        checkTeamsInSeason(teams, season);
    }

    private static void apply(Event event, EventInput input, Set<Team> teams) {
        event.type = input.type();
        event.title = input.title().strip();
        event.start = input.start().toInstant();
        event.end = input.end().toInstant();
        event.location = Location.of(input.location());
        event.notes = EventRules.blankToNull(input.notes());
        event.responseDeadlineHours = EventRules.responseDeadline(input.responseDeadlineHours());
        Instant meetingTime = input.meetingPoint() == null || input.meetingPoint().time() == null ? null
                : input.meetingPoint().time().toInstant();
        // A time that differs from the one set automatically was entered by hand and stays (TML-09)
        if (meetingTime == null || !meetingTime.equals(event.meetingPointTime)) {
            event.meetingPointTimeAuto = false;
        }
        event.meetingPointTime = meetingTime;
        event.meetingPointLocation = input.meetingPoint() == null ? null : input.meetingPoint().location().strip();
        event.variantId = variant(input.variantId(), input.type(), teams);
        if (input.attendanceCheck() != null) {
            event.attendanceCheck = input.attendanceCheck();
        }
        event.teams.clear();
        event.teams.addAll(teams);
        List<UUID> optional = Participation.check(teams.stream().map(team -> team.id).toList(), input.optionalTeamIds());
        event.optionalTeamIds.retainAll(optional);
        event.optionalTeamIds.addAll(optional);
    }
}
