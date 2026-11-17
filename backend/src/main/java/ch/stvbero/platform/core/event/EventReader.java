package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.line.Group;
import ch.stvbero.platform.core.line.LineRules;
import ch.stvbero.platform.core.person.Person;
import ch.stvbero.platform.core.person.PersonSummary;
import ch.stvbero.platform.core.team.TeamRole;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.team.TeamDto;
import ch.stvbero.platform.core.team.TeamSeason;
import ch.stvbero.platform.core.team.TeamMembership;
import ch.stvbero.platform.core.clock.BackendClock;
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

    EventReader(EntityManager em, CurrentUser user, Collection<Event> events, Instant now) {
        this.user = user;
        this.now = now;
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
        }
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
                counts(event), event.series == null ? null : event.series.id, warningCount(event));
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
        int count = 0;
        for (Team team : event.teams) {
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
    EventDto full(Event event, boolean canEdit, Instant now) {
        EventSummaryDto base = summary(event);
        boolean matchDay = event.type == EventType.MATCH_DAY;
        MeetingPointDto meetingPoint = event.meetingPointTime == null ? null
                : new MeetingPointDto(time(event.meetingPointTime), event.meetingPointLocation);
        List<MatchDto> matches = null;
        CallUpStatus callUpStatus = null;
        MyCallUp myCallUp = null;
        Set<TeamRole> mine = myTeamRoles(event);
        if (matchDay) {
            matches = Match.of(event.id).stream().map(Match::dto).toList();
            CallUp callUp = CallUp.findById(event.id);
            CallUpStatus status = callUp == null ? CallUpStatus.NONE : callUp.status();
            callUpStatus = CallUpRules.visibleStatus(status,
                    CallUpRules.canEdit(user.allTeams(), mine));
            if (mine.contains(TeamRole.PLAYER)) {
                myCallUp = CallUpRules.myCallUp(status,
                        callUp != null && callUp.personIds.contains(user.personId()));
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
                event.notes, event.responseDeadlineHours, meetingPoint, matches, callUpStatus, myCallUp, canEdit,
                event.attendanceCheck, attendanceRecorded, canRecordAttendance, myAttendance);
    }

    /** Teams by name, with the name in the season of the event (SAI-02) and the caller's own team role. */
    private List<TeamDto> teams(Event event) {
        List<TeamDto> teams = new ArrayList<>();
        for (Team team : event.teams) {
            TeamInSeason key = new TeamInSeason(team.id, event.season.id);
            TeamRole mine = members.getOrDefault(key, List.of()).stream()
                    .filter(m -> m.person().equals(user.personId())).map(Member::teamRole).findFirst().orElse(null);
            teams.add(new TeamDto(team.id, names.getOrDefault(key, ""), team.suTeamId, mine));
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
                stored.lateCancellation());
    }

    /** Total counts: each player of a participating team once, coaches do not count (PTF-07). */
    private CountsDto counts(Event event) {
        Set<UUID> players = new LinkedHashSet<>();
        for (Team team : event.teams) {
            members.getOrDefault(new TeamInSeason(team.id, event.season.id), List.of()).stream()
                    .filter(m -> m.teamRole() == TeamRole.PLAYER).forEach(m -> players.add(m.person()));
        }
        Map<UUID, StoredResponse> eventResponses = responses.getOrDefault(event.id, Map.of());
        return EventRules.count(players.stream()
                .map(p -> eventResponses.containsKey(p) ? eventResponses.get(p).status() : ResponseStatus.PENDING).toList());
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
