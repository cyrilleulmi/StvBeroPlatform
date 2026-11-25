package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.line.Group;
import ch.stvbero.platform.core.line.LineRules;
import ch.stvbero.platform.core.person.Person;
import ch.stvbero.platform.core.person.PersonSummary;
import ch.stvbero.platform.core.team.TeamRole;
import ch.stvbero.platform.core.team.SuLink;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.team.TeamDto;
import ch.stvbero.platform.core.team.TeamSeason;
import ch.stvbero.platform.core.team.TeamMembership;
import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.attendance.variant.EventVariant;
import ch.stvbero.platform.attendance.variant.Variants;
import jakarta.persistence.EntityManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns events into API responses. Loads team names, team memberships and responses for all events at once,
 * so that the event list does not query the database once per event.
 */
final class EventReader {

    private record TeamInSeason(UUID team, UUID season) {
    }

    private record Member(UUID person, TeamRole teamRole, List<LineRules.GroupInfo> groups) {
    }

    private record StoredResponse(ResponseStatus status, String comment, Instant changedAt,
                                       UUID enteredBy, boolean lateCancellation) {
    }

    private final CurrentUser user;
    private final Instant now;
    private final Map<TeamInSeason, String> names = new HashMap<>();
    private final Map<TeamInSeason, List<Member>> members = new HashMap<>();
    private final Map<UUID, Map<UUID, StoredResponse>> responses = new HashMap<>();
    /** Per event: matches changed by swiss unihockey and not confirmed, matches no longer delivered (SUH-12). */
    private final Map<UUID, int[]> suWarnings = new HashMap<>();
    /** Team ID at swiss unihockey of the primary link, per season and team (SUH-11). */
    private final Map<UUID, Map<UUID, Integer>> suTeamIds = new HashMap<>();
    /** Per event: the teams invited without obligation (TER-15). */
    private final Map<UUID, Set<UUID>> optionalTeams = new HashMap<>();
    /** Per event: who travels on her own (ANM-13). */
    private final Map<UUID, Set<UUID>> individual = new HashMap<>();
    /** Finds the variant of every event (VAR-03). */
    private final Variants variants;
    /** Ranks of the league table for the matches of a match day (AUF-34). */
    private final Standings.Source league;
    /** Events the caller has a special permission for, directly or through the series (RR-09, RR-10). */
    private final Set<UUID> granted = new java.util.HashSet<>();
    /** Per season whether it is archived: asked once per season, not once per event (SAI-02). */
    private final Map<UUID, Boolean> archived = new HashMap<>();

    EventReader(EntityManager em, CurrentUser user, Collection<Event> events, Instant now, Standings.Source league) {
        this.user = user;
        this.now = now;
        this.league = league;
        this.variants = Variants.of(events);
        Set<UUID> seasons = new LinkedHashSet<>();
        events.forEach(event -> seasons.add(event.season.id));
        if (!seasons.isEmpty()) {
            TeamSeason.<TeamSeason>list("season.id in ?1", seasons)
                    .forEach(ts -> names.put(new TeamInSeason(ts.team.id, ts.season.id), ts.name));
            // With the groups, for the warnings in the event list (F-16)
            em.createQuery("select distinct m from TeamMembership m left join fetch m.groups where m.season.id in :seasons",
                            TeamMembership.class).setParameter("seasons", seasons).getResultList()
                    .forEach(m -> members
                            .computeIfAbsent(new TeamInSeason(m.team.id, m.season.id), key -> new ArrayList<>())
                            .add(new Member(m.person.id, m.teamRole, m.groups.stream().map(Group::info).toList())));
        }
        List<UUID> ids = events.stream().map(event -> event.id).toList();
        if (!ids.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("""
                            select event_id, person_id, status, comment, changed_at, entered_by, late_cancellation
                            from response where event_id in (:ids)
                            """)
                    .setParameter("ids", ids).getResultList();
            for (Object[] row : rows) {
                responses.computeIfAbsent((UUID) row[0], key -> new HashMap<>()).put((UUID) row[1],
                        new StoredResponse(ResponseStatus.valueOf((String) row[2]), (String) row[3], instant(row[4]),
                                (UUID) row[5], Boolean.TRUE.equals(row[6])));
            }
            if (WarningRules.visible(user.allTeams())) {
                suWarnings.putAll(suWarnings(em, ids));
            }
            @SuppressWarnings("unchecked")
            List<Object[]> optional = em.createNativeQuery(
                            "select event_id, team_id from event_optional_team where event_id in (:ids)")
                    .setParameter("ids", ids).getResultList();
            optional.forEach(row -> optionalTeams.computeIfAbsent((UUID) row[0], key -> new java.util.HashSet<>())
                    .add((UUID) row[1]));
            @SuppressWarnings("unchecked")
            List<Object[]> travel = em.createNativeQuery(
                            "select event_id, person_id from individual_travel where event_id in (:ids) and person_id = :me")
                    .setParameter("ids", ids).setParameter("me", user.personId()).getResultList();
            travel.forEach(row -> individual.computeIfAbsent((UUID) row[0], key -> new java.util.HashSet<>())
                    .add((UUID) row[1]));
            if (!user.allTeams()) {
                granted.addAll(granted(em, ids, user.personId()));
            }
        }
    }

    /** The events among {@code ids} the person has a special permission for, in one query (RR-09, RR-10). */
    private static List<UUID> granted(EntityManager em, List<UUID> ids, UUID person) {
        @SuppressWarnings("unchecked")
        List<UUID> rows = em.createNativeQuery("""
                        select event_id from event_grant where event_id in (:ids) and person_id = :person
                        union
                        select e.id from event e join series_grant g on g.series_id = e.series_id
                        where e.id in (:ids) and g.person_id = :person""")
                .setParameter("ids", ids).setParameter("person", person).getResultList();
        return rows;
    }

    /**
     * The caller may edit the event (TER-06, TER-11): coach or admin, or a special permission for it (RR-09); only
     * while it is planned, not over and its season not archived. One rule for the event list and the event page.
     */
    boolean canEdit(Event event) {
        boolean rights = EventRules.canEdit(user.allTeams(), myTeamRoles(event)) || granted.contains(event.id);
        return rights && event.status == EventStatus.PLANNED && !event.isOver(now)
                && !archived.computeIfAbsent(event.season.id, id -> event.season.isArchived());
    }

    /** Team roles of the logged-in person in the teams of the event (in the season of the event). */
    Set<TeamRole> myTeamRoles(Event event) {
        Set<TeamRole> teamRoles = new LinkedHashSet<>();
        for (Team team : event.teams) {
            for (Member m : members.getOrDefault(new TeamInSeason(team.id, event.season.id), List.of())) {
                if (m.person().equals(user.personId())) {
                    teamRoles.add(m.teamRole());
                }
            }
        }
        return teamRoles;
    }

    EventSummaryDto summary(Event event) {
        return new EventSummaryDto(event.id, event.type, event.title, time(event.start), time(event.end),
                event.status, event.cancellationReason, event.rescheduled, teams(event),
                event.location == null ? null : event.location.dto(),
                time(EventRules.responseDeadline(event.start, event.responseDeadlineHours)), myResponse(event),
                counts(event), event.series == null ? null : event.series.id, warningCount(event),
                variants.resolve(event).dto(), optional(event).stream().sorted().toList(), myParticipation(event),
                canEdit(event));
    }

    private Set<UUID> optional(Event event) {
        return optionalTeams.getOrDefault(event.id, Set.of());
    }

    /** EXPECTED or OPTIONAL for a member of a team of the event, otherwise {@code null} (TER-15). */
    private Participation myParticipation(Event event) {
        List<UUID> mine = new ArrayList<>();
        for (Team team : event.teams) {
            if (members.getOrDefault(new TeamInSeason(team.id, event.season.id), List.of()).stream()
                    .anyMatch(m -> m.person().equals(user.personId()))) {
                mine.add(team.id);
            }
        }
        return Participation.of(mine, optional(event));
    }

    EventVariant variant(Event event) {
        return variants.resolve(event);
    }

    /**
     * Number of warnings of the event from the responses, as in the overview (F-16, criterion 8). Only for coaches
     * and admins; {@code null} for everyone else.
     */
    private Integer warningCount(Event event) {
        if (!WarningRules.visible(user.allTeams())) {
            return null;
        }
        if (!WarningRules.applies(event.type, event.status, event.end, now)) {
            return 0;
        }
        Map<UUID, StoredResponse> eventResponses = responses.getOrDefault(event.id, Map.of());
        int[] su = suWarnings.getOrDefault(event.id, new int[2]);
        int count = WarningRules.suWarnings(null, su[0], su[1]).size();
        for (Team team : event.teams) {
            if (optional(event).contains(team.id)) {
                // A team invited without obligation has no minimums to meet (TER-15)
                continue;
            }
            List<WarningRules.Player> players = members.getOrDefault(new TeamInSeason(team.id, event.season.id), List.of())
                    .stream().filter(m -> m.teamRole() == TeamRole.PLAYER)
                    .map(m -> new WarningRules.Player(m.person(), m.groups(), eventResponses.containsKey(m.person())
                            && eventResponses.get(m.person()).status() == ResponseStatus.ATTENDING))
                    .toList();
            count += WarningRules.warnings(event.type, new OverviewDto.TeamName(team.id, ""), team.minimums(), players)
                    .size();
        }
        return count;
    }

    /**
     * Event with all details. For match days also the matches by start time, the call-up status (before
     * publication only for coaches and admins, AUF-23) and the player's own call-up status (AUF-24).
     */
    EventDto full(Event event, Instant now) {
        EventSummaryDto base = summary(event);
        boolean matchDay = event.type == EventType.MATCH_DAY;
        // The place can be known before the time: imported match days start with the usual meeting point of the
        // team (AUF-32) and no time yet.
        MeetingPointDto meetingPoint = event.meetingPointLocation == null ? null
                : new MeetingPointDto(event.meetingPointTime == null ? null : time(event.meetingPointTime),
                event.meetingPointLocation);
        List<MatchDto> matches = null;
        CallUpStatus callUpStatus = null;
        MyCallUp myCallUp = null;
        Set<TeamRole> mine = myTeamRoles(event);
        StandingDto standing = null;
        if (matchDay) {
            List<Match> stored = Match.of(event.id);
            Standings standings = league.of(event);
            standing = standings.own();
            matches = stored.stream().map(match -> match.dto(standings.rankOf(match.opponent))).toList();
            // Without a call-up in the variant the match day works with the responses alone (VAR-06)
            if (variants.resolve(event).callUp) {
                CallUp callUp = CallUp.findById(event.id);
                CallUpStatus status = callUp == null ? CallUpStatus.NONE : callUp.status();
                callUpStatus = CallUpRules.visibleStatus(status,
                        CallUpRules.canEdit(user.allTeams(), mine));
                if (mine.contains(TeamRole.PLAYER)) {
                    myCallUp = CallUpRules.myCallUp(status,
                            callUp != null && callUp.personIds.contains(user.personId()));
                }
            }
        }
        // Attendance check (PRS): coaches and admins see whether something is recorded and may record from the
        // start on; players see only their own recorded value (PRS-06).
        boolean coachOrAdmin = AttendanceRules.canRecord(user.allTeams());
        AttendanceRecord attendance = event.attendanceCheck || coachOrAdmin ? AttendanceRecord.findById(event.id) : null;
        Boolean attendanceRecorded = coachOrAdmin ? attendance != null : null;
        boolean canRecordAttendance = coachOrAdmin && !event.season.isArchived()
                && AttendanceRules.recordable(event.attendanceCheck, event.status, event.start, now);
        AttendanceStatus myAttendance = !mine.contains(TeamRole.PLAYER) || attendance == null ? null
                : AttendanceRules.mine(event.attendanceCheck, attendance.entries.get(user.personId()));
        return new EventDto(base.id(), base.type(), base.title(), base.start(), base.end(), base.status(),
                base.cancellationReason(), base.rescheduled(), base.teams(), base.location(), base.responseDeadline(),
                base.myResponse(), base.counts(), base.seriesId(), base.warningCount(),
                event.notes, event.responseDeadlineHours, meetingPoint, matches, callUpStatus, myCallUp, base.canEdit(),
                event.attendanceCheck, attendanceRecorded, canRecordAttendance, myAttendance, base.variant(),
                standing, base.optionalTeamIds(), base.myParticipation(), user.eventGrant() ? Boolean.TRUE : null);
    }

    /**
     * Per event the number of matches with an unconfirmed change by swiss unihockey and of matches it no longer
     * delivers (SUH-12): {@code [changed, missing]}.
     */
    static Map<UUID, int[]> suWarnings(EntityManager em, Collection<UUID> eventIds) {
        Map<UUID, int[]> counts = new HashMap<>();
        if (eventIds.isEmpty()) {
            return counts;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select event_id, count(*) filter (where su_changed_at is not null),
                               count(*) filter (where missing_at_su)
                        from match where event_id in (:ids) and (su_changed_at is not null or missing_at_su)
                        group by event_id""")
                .setParameter("ids", eventIds).getResultList();
        for (Object[] row : rows) {
            counts.put((UUID) row[0], new int[] {((Number) row[1]).intValue(), ((Number) row[2]).intValue()});
        }
        return counts;
    }

    /** Teams by name, with the name in the season of the event (SAI-02) and the caller's own team role. */
    private List<TeamDto> teams(Event event) {
        List<TeamDto> teams = new ArrayList<>();
        for (Team team : event.teams) {
            TeamInSeason key = new TeamInSeason(team.id, event.season.id);
            TeamRole mine = members.getOrDefault(key, List.of()).stream()
                    .filter(m -> m.person().equals(user.personId())).map(Member::teamRole).findFirst().orElse(null);
            Integer suTeamId = suTeamIds.computeIfAbsent(event.season.id, SuLink::primaryIds).get(team.id);
            teams.add(new TeamDto(team.id, names.getOrDefault(key, ""), suTeamId, mine));
        }
        teams.sort(Comparator.comparing(TeamDto::name, String.CASE_INSENSITIVE_ORDER).thenComparing(TeamDto::id));
        return teams;
    }

    private ResponseDto myResponse(Event event) {
        StoredResponse stored = responses.getOrDefault(event.id, Map.of()).get(user.personId());
        if (stored == null) {
            return ResponseDto.PENDING;
        }
        PersonSummary enteredBy = stored.enteredBy() == null ? null
                : Person.<Person>findByIdOptional(stored.enteredBy()).map(PersonSummary::of).orElse(null);
        return new ResponseDto(stored.status(), stored.comment(), time(stored.changedAt()), enteredBy,
                stored.lateCancellation(),
                individual.getOrDefault(event.id, Set.of()).contains(user.personId()) ? Boolean.TRUE : null);
    }

    /** Total counts: each player of a participating team once, coaches do not count (PTF-07). */
    private CountsDto counts(Event event) {
        Set<UUID> expected = new LinkedHashSet<>();
        Set<UUID> invited = new LinkedHashSet<>();
        for (Team team : event.teams) {
            Set<UUID> target = optional(event).contains(team.id) ? invited : expected;
            members.getOrDefault(new TeamInSeason(team.id, event.season.id), List.of()).stream()
                    .filter(m -> m.teamRole() == TeamRole.PLAYER).forEach(m -> target.add(m.person()));
        }
        Map<UUID, StoredResponse> eventResponses = responses.getOrDefault(event.id, Map.of());
        List<ResponseStatus> statuses = new ArrayList<>();
        expected.forEach(p -> statuses.add(eventResponses.containsKey(p) ? eventResponses.get(p).status()
                : ResponseStatus.PENDING));
        // Players of teams invited without obligation count once they respond, never as «offen» (TER-15)
        invited.stream().filter(p -> !expected.contains(p) && eventResponses.containsKey(p))
                .map(p -> eventResponses.get(p).status()).filter(status -> status != ResponseStatus.PENDING)
                .forEach(statuses::add);
        return EventRules.count(statuses);
    }

    static OffsetDateTime time(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, BackendClock.ZONE);
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
        return null;
    }
}
