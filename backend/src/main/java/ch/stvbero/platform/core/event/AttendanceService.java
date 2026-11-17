package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.line.Group;
import ch.stvbero.platform.core.person.Person;
import ch.stvbero.platform.core.person.PersonSummary;
import ch.stvbero.platform.core.team.GroupType;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.team.TeamMembership;
import ch.stvbero.platform.core.team.TeamRole;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Attendance check of an event (PRS): list by line with response and attendance, save and correct. Only coaches
 * and admins (PRS-10), enforced here in the backend (RR-07). Responses stay untouched.
 */
@ApplicationScoped
public class AttendanceService {

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    @Inject
    ResponseService responses;

    public AttendanceDto get(CurrentUser user, UUID eventId) {
        Event event = EventService.find(eventId);
        AttendanceRules.checkCanRecord(user.allTeams());
        AttendanceRules.checkRecordable(event.attendanceCheck, event.status, event.start, clock.now());
        return dto(event, AttendanceRecord.findById(event.id));
    }

    /**
     * Save the attendance (PRS-03, PRS-04). The given players take the new status, the others keep theirs; who
     * saved and when is stored. Only players of the involved teams (PRS-05).
     */
    @Transactional
    public AttendanceDto record(CurrentUser user, UUID eventId, AttendanceInput input) {
        Event event = EventService.find(eventId);
        AttendanceRules.checkCanRecord(user.allTeams());
        AttendanceRules.checkRecordable(event.attendanceCheck, event.status, event.start, clock.now());
        event.season.checkWritable();
        List<UUID> persons = input.entries().stream().map(AttendanceInput.Entry::personId).toList();
        AttendanceRules.checkUnique(persons);
        AttendanceRules.checkOnlyPlayers(persons, players(event));
        AttendanceRecord record = locked(event.id);
        input.entries().forEach(entry -> record.entries.put(entry.personId(), entry.status()));
        record.recordedAt = clock.now();
        record.recordedBy = user.personId();
        return dto(event, record);
    }

    /** Whether attendance has been recorded for the event (PRS-08), without loading it. */
    static boolean recorded(UUID eventId) {
        return AttendanceRecord.count("eventId", eventId) > 0;
    }

    /** Attendance record of the event, locked for the transaction; two concurrent saves never result in a 500. */
    private AttendanceRecord locked(UUID eventId) {
        em.createNativeQuery("insert into attendance_record (event_id, recorded_at) values (?, now()) on conflict do nothing")
                .setParameter(1, eventId).executeUpdate();
        return em.find(AttendanceRecord.class, eventId, LockModeType.PESSIMISTIC_WRITE);
    }

    /** Persons with the team role player in the involved teams (in the season of the event). */
    private Set<UUID> players(Event event) {
        Set<UUID> ids = new LinkedHashSet<>();
        responses.members(event).stream().filter(m -> m.teamRole == TeamRole.PLAYER).forEach(m -> ids.add(m.person.id));
        return ids;
    }

    /** All players by line, then goalies, then «Ohne Linie»; empty groups are left out (like the call-up). */
    private AttendanceDto dto(Event event, AttendanceRecord record) {
        Map<UUID, AttendanceStatus> recorded = record == null ? Map.of() : record.entries;
        Map<UUID, ResponseService.Stored> stored = responses.responses(event.id);
        List<TeamMembership> players = responses.members(event).stream()
                .filter(m -> m.teamRole == TeamRole.PLAYER).toList();
        Map<UUID, PersonSummary> persons = new HashMap<>();
        players.forEach(m -> persons.put(m.person.id, PersonSummary.of(m.person)));

        Map<UUID, List<AttendanceDto.Entry>> perGroup = new LinkedHashMap<>();
        List<AttendanceDto.Entry> noLine = new ArrayList<>();
        for (TeamMembership m : players) {
            ResponseService.Stored response = stored.get(m.person.id);
            AttendanceStatus saved = recorded.get(m.person.id);
            AttendanceDto.Entry entry = new AttendanceDto.Entry(persons.get(m.person.id),
                    ResponseService.response(response, true, persons),
                    AttendanceRules.shown(saved, response == null ? ResponseStatus.PENDING : response.status()),
                    saved != null);
            if (m.groups.isEmpty()) {
                noLine.add(entry);
            } else {
                m.groups.forEach(g -> perGroup.computeIfAbsent(g.id, k -> new ArrayList<>()).add(entry));
            }
        }
        List<AttendanceDto.Group> groups = new ArrayList<>();
        for (Team team : event.teams) {
            for (Group g : Group.of(team, event.season)) {
                List<AttendanceDto.Entry> entries = perGroup.get(g.id);
                if (entries != null && !entries.isEmpty()) {
                    groups.add(new AttendanceDto.Group(g.id, g.type == GroupType.GOALIES
                            ? OverviewDto.Type.GOALIES : OverviewDto.Type.LINE, g.name, sorted(entries)));
                }
            }
        }
        if (!noLine.isEmpty()) {
            groups.add(new AttendanceDto.Group(null, OverviewDto.Type.NO_LINE, ResponseService.NO_LINE, sorted(noLine)));
        }
        boolean saved = record != null;
        PersonSummary recordedBy = record == null || record.recordedBy == null ? null
                : Person.<Person>findByIdOptional(record.recordedBy).map(PersonSummary::of).orElse(null);
        return new AttendanceDto(event.id, saved, saved ? EventReader.time(record.recordedAt) : null, recordedBy, groups);
    }

    private static List<AttendanceDto.Entry> sorted(List<AttendanceDto.Entry> entries) {
        return entries.stream().sorted(Comparator.comparing(AttendanceDto.Entry::person, PersonSummary.ORDER)).toList();
    }
}
