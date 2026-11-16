package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.team.TeamRole;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.team.TeamMembership;
import ch.stvbero.platform.core.clock.BackendClock;
import jakarta.enterprise.context.ApplicationScoped;
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
 * Termine erfassen, ändern, verschieben und absagen (TER). Erfassen und bearbeiten dürfen Coaches eines
 * beteiligten Teams und Admins (TER-11, RR-06), ansehen alle Mitglieder der beteiligten Teams.
 */
@ApplicationScoped
public class EventService {

    public static final String UPCOMING = "upcoming";
    public static final String PAST = "past";

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    @Inject
    EventNotifications notifications;

    /**
     * Termine der eigenen Teams (TER-13). Kommende aufsteigend ab «jetzt», vergangene absteigend.
     * Eigene Teams heisst: Mitglied eines beteiligten Teams in der Saison des Termins.
     */
    public List<EventSummaryDto> list(CurrentUser user, LocalDate startDate, LocalDate endDate, UUID teamId, EventType type,
                                     String view) {
        boolean past = PAST.equals(view);
        if (view != null && !past && !UPCOMING.equals(view)) {
            throw DomainError.invalidField("view", "Die Ansicht ist «kommend» oder «vergangen».");
        }
        Instant now = clock.now();
        StringBuilder jpql = new StringBuilder("""
                select distinct t from Event t left join fetch t.teams join fetch t.season
                where exists (select z.id from TeamMembership z
                              where z.person.id = :person and z.season = t.season and z.team member of t.teams)
                """);
        Map<String, Object> parameter = new HashMap<>();
        parameter.put("person", user.personId());
        parameter.put("now", now);
        jpql.append(past ? " and t.end <= :now" : " and t.end > :now");
        if (startDate != null) {
            jpql.append(" and t.start >= :startDate");
            parameter.put("startDate", startDate.atStartOfDay(BackendClock.ZONE).toInstant());
        }
        if (endDate != null) {
            jpql.append(" and t.start < :endDate");
            parameter.put("endDate", endDate.plusDays(1).atStartOfDay(BackendClock.ZONE).toInstant());
        }
        if (teamId != null) {
            jpql.append(" and exists (select x.id from Event t2 join t2.teams x where t2 = t and x.id = :team)");
            parameter.put("team", teamId);
        }
        if (type != null) {
            jpql.append(" and t.type = :type");
            parameter.put("type", type);
        }
        jpql.append(past ? " order by t.start desc, t.id" : " order by t.start, t.id");
        TypedQuery<Event> query = em.createQuery(jpql.toString(), Event.class);
        parameter.forEach(query::setParameter);
        List<Event> events = query.getResultList();
        EventReader reader = new EventReader(em, user, events);
        return events.stream().map(reader::summary).toList();
    }

    public EventDto get(CurrentUser user, UUID eventId) {
        Event event = find(eventId);
        EventReader reader = new EventReader(em, user, List.of(event));
        Set<TeamRole> mine = reader.myTeamRoles(event);
        EventRules.checkCanView(user.admin(), mine);
        return reader.full(event, canEdit(user, event, mine));
    }

    /** Einzeltermin erfassen, auch für mehrere Teams (TER-01, TER-04, TER-11). */
    @Transactional
    public EventDto create(CurrentUser user, EventInput input) {
        Instant start = input.start().toInstant();
        Instant end = input.end().toInstant();
        Set<Team> teams = teams(input.teamIds());
        Season season = seasonOn(EventRules.date(start), null);
        EventRules.checkCanCreate(user.admin(), teamRoles(user, teams, season));
        checkInput(input.type(), start, end, teams, season);
        Event t = Event.create();
        t.season = season;
        apply(t, input, teams);
        t.persist();
        return get(user, t.id);
    }

    /**
     * Termin ändern. Eine andere Beginnzeit ist eine Verschiebung (TER-06). Bei einem Serientermin gilt die
     * Änderung nur für diesen Termin; spätere Änderungen der Serie übernimmt er nicht mehr. Antworten bleiben.
     */
    @Transactional
    public EventDto update(CurrentUser user, UUID eventId, EventInput input) {
        Event t = find(eventId);
        EventRules.checkCanCreate(user.admin(), teamRoles(user, t.teams, t.season));
        EventRules.checkChangeable(t.status, t.end, clock.now());
        Instant start = input.start().toInstant();
        Instant end = input.end().toInstant();
        Set<Team> teams = teams(input.teamIds());
        Season season = seasonOn(EventRules.date(start), t.season);
        // Auch nach der Änderung muss die Person Coach eines beteiligten Teams sein.
        EventRules.checkCanCreate(user.admin(), teamRoles(user, teams, season));
        checkInput(input.type(), start, end, teams, season);
        List<Match> matches = t.type == EventType.MATCH_DAY ? Match.of(t.id) : List.of();
        EventRules.checkTypeChange(t.type, input.type(), !matches.isEmpty() || CallUp.findById(t.id) != null);
        MatchRules.checkReschedulable(matches.stream().anyMatch(s -> s.source == MatchSource.SWISS_UNIHOCKEY),
                t.start, start);
        Instant oldStart = t.start;
        String oldLabel = EventNotifications.label(t);
        List<UUID> previousMembers = EventNotifications.members(t);
        long days = ChronoUnit.DAYS.between(EventRules.date(t.start), EventRules.date(start));
        if (days != 0) {
            // Von Hand erfasste Spiele ziehen mit, wenn der Spieltag auf einen anderen Tag verschoben wird (AUF-01).
            matches.stream().filter(s -> s.source == MatchSource.MANUAL)
                    .forEach(s -> s.startTime = s.startTime.atZone(BackendClock.ZONE).plusDays(days).toInstant());
        }
        t.rescheduled = EventRules.rescheduled(t.rescheduled, t.start, start);
        t.season = season;
        apply(t, input, teams);
        if (t.series != null) {
            t.individuallyChanged = true;
        }
        if (NotificationRules.rescheduled(oldStart, t.start)) {
            notifications.rescheduled(user, t, oldLabel, previousMembers);
        }
        return get(user, t.id);
    }

    /**
     * Termin absagen (TER-06). Danach sind keine Änderungen und keine Antworten mehr möglich. Alle Mitglieder
     * der beteiligten Teams werden benachrichtigt (BEN-07).
     */
    @Transactional
    public EventDto cancel(CurrentUser user, UUID eventId, String reason) {
        Event t = find(eventId);
        EventRules.checkCanCreate(user.admin(), teamRoles(user, t.teams, t.season));
        EventRules.checkChangeable(t.status, t.end, clock.now());
        t.season.checkWritable();
        t.status = EventStatus.CANCELLED;
        t.cancellationReason = reason.strip();
        notifications.cancelled(user, t);
        return get(user, t.id);
    }

    // ------------------------------------------------------------------ gemeinsam mit SerieService

    static Event find(UUID id) {
        return Event.<Event>findByIdOptional(id)
                .orElseThrow(() -> DomainError.notFound("Diesen Termin gibt es nicht."));
    }

    /** Teams zu den IDs. Unbekannte IDs: 400. */
    static Set<Team> teams(Collection<UUID> ids) {
        Set<Team> teams = new LinkedHashSet<>();
        for (UUID id : new LinkedHashSet<>(ids)) {
            teams.add(Team.<Team>findByIdOptional(id)
                    .orElseThrow(() -> DomainError.invalidField("teamIds", "Dieses Team gibt es nicht.")));
        }
        return teams;
    }

    /** Funktionen der Person in diesen Teams in dieser Saison (leer, wenn sie in keinem Mitglied ist). */
    static Set<TeamRole> teamRoles(CurrentUser user, Collection<Team> teams, Season season) {
        if (teams.isEmpty()) {
            return Set.of();
        }
        Set<TeamRole> teamRoles = new LinkedHashSet<>();
        TeamMembership.<TeamMembership>list("person.id = ?1 and season.id = ?2 and team.id in ?3",
                user.personId(), season.id, teams.stream().map(t -> t.id).toList()).forEach(z -> teamRoles.add(z.teamRole));
        return teamRoles;
    }

    /** Die Saison, in die der Tag fällt; sonst die angegebene bzw. die aktuelle. */
    static Season seasonOn(LocalDate day, Season otherwise) {
        return Season.<Season>find("startDate <= ?1 and endDate >= ?1", day).firstResultOptional()
                .orElseGet(() -> otherwise != null ? otherwise : Season.currentOrFail());
    }

    /** Alle Teams gehören in der Saison zum Verein, die Saison ist nicht im Archiv (SAI-02). */
    static void checkTeamsInSeason(Collection<Team> teams, Season season) {
        for (Team team : teams) {
            if (!team.belongsTo(season)) {
                throw DomainError.invalidField("teamIds", "Ein Team gibt es in der Saison " + season.name + " nicht.");
            }
        }
        season.checkWritable();
    }

    private boolean canEdit(CurrentUser user, Event t, Set<TeamRole> mine) {
        return EventRules.canEdit(user.admin(), mine) && t.status == EventStatus.PLANNED
                && !t.isOver(clock.now()) && !t.season.isArchived();
    }

    private void checkInput(EventType type, Instant start, Instant end, Set<Team> teams, Season season) {
        EventRules.checkTime(start, end);
        EventRules.checkNotOver(end, clock.now());
        EventRules.checkTeams(type, teams.size());
        checkTeamsInSeason(teams, season);
    }

    private static void apply(Event t, EventInput e, Set<Team> teams) {
        t.type = e.type();
        t.title = e.title().strip();
        t.start = e.start().toInstant();
        t.end = e.end().toInstant();
        t.location = Location.of(e.location());
        t.notes = EventRules.blankToNull(e.notes());
        t.responseDeadlineHours = EventRules.responseDeadline(e.responseDeadlineHours());
        t.meetingPointTime = e.meetingPoint() == null ? null : e.meetingPoint().time().toInstant();
        t.meetingPointLocation = e.meetingPoint() == null ? null : e.meetingPoint().location().strip();
        t.teams.clear();
        t.teams.addAll(teams);
    }
}
