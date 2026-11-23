package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.variant.Variants;
import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.line.Group;
import ch.stvbero.platform.core.person.Person;
import ch.stvbero.platform.core.person.PersonSummary;
import ch.stvbero.platform.core.team.TeamRole;
import ch.stvbero.platform.core.team.GroupType;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.team.TeamSeason;
import ch.stvbero.platform.core.team.TeamMembership;
import ch.stvbero.platform.core.clock.BackendClock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
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
 * Responses to events (ANM): own response, response on behalf and the overview by team and line.
 * Permissions according to RR-02, RR-03 and RR-06, enforced here in the backend (RR-07).
 */
@ApplicationScoped
public class ResponseService {

    static final String NO_LINE = "Ohne Linie";

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    @Inject
    EventNotifications notifications;

    @Inject
    ch.stvbero.platform.core.driver.DriverListService drivers;

    /** Stored response, as it is in the table. */
    record Stored(ResponseStatus status, String comment, Instant changedAt, UUID enteredBy,
                       boolean lateCancellation) {
    }

    // ------------------------------------------------------------------ Write

    /**
     * Set or change a response (ANM-01 to ANM-12). {@code personId == null} or the caller's own ID: for
     * oneself, otherwise on behalf (ANM-11, RR-06) with «eingetragen von» (ANM-12).
     */
    @Transactional
    public ResponseDto set(CurrentUser user, UUID eventId, UUID personId, ResponseInput input) {
        Event event = EventService.find(eventId);
        List<TeamMembership> members = members(event);
        Map<UUID, TeamRole> mine = teamRoles(members, user.personId());
        UUID respondent;
        UUID enteredBy;
        if (personId == null || personId.equals(user.personId())) {
            ResponseRules.checkCanRespondForSelf(!mine.isEmpty());
            respondent = user.personId();
            enteredBy = null;
        } else {
            EventRules.checkCanView(user.allTeams(), mine.values());
            Person.<Person>findByIdOptional(personId)
                    .orElseThrow(() -> DomainError.notFound("Diese Person gibt es nicht."));
            Map<UUID, TeamRole> theirs = teamRoles(members, personId);
            if (theirs.isEmpty() && (user.allTeams() || mine.containsValue(TeamRole.COACH))) {
                throw DomainError.notFound("Diese Person gehört zu keinem Team dieses Termins.");
            }
            ResponseRules.checkCanRespondOnBehalf(user.allTeams(), mine, theirs);
            respondent = personId;
            enteredBy = user.personId();
        }
        Instant now = clock.now();
        ResponseRules.checkOpenForResponses(event.status, event.end, now);
        event.season.checkWritable();
        ResponseStatus newStatus = input.status().status();
        ResponseRules.checkComment(newStatus, input.comment(), Variants.of(event).declineReasonRequired);

        Stored previous = locked(event.id, respondent);
        boolean lateCancellation = ResponseRules.lateCancellation(
                previous == null ? ResponseStatus.PENDING : previous.status(), previous != null && previous.lateCancellation(), newStatus,
                EventRules.responseDeadline(event.start, event.responseDeadlineHours), now);
        String comment = EventRules.blankToNull(input.comment());
        em.createNativeQuery("""
                        insert into response (event_id, person_id, status, comment, changed_at, entered_by, late_cancellation)
                        values (?, ?, ?, ?, ?, ?, ?)
                        on conflict (event_id, person_id) do update
                        set status = excluded.status, comment = excluded.comment, changed_at = excluded.changed_at,
                            entered_by = excluded.entered_by, late_cancellation = excluded.late_cancellation
                        """)
                .setParameter(1, event.id).setParameter(2, respondent).setParameter(3, newStatus.name()).setParameter(4, comment)
                .setParameter(5, now).setParameter(6, enteredBy).setParameter(7, lateCancellation)
                .executeUpdate();
        notifications.responseChanged(user, event, members, respondent,
                previous == null ? ResponseStatus.PENDING : previous.status(), newStatus, lateCancellation, comment);
        // «Ich reise individuell an» comes with «dabei» and goes with every other response (ANM-13)
        drivers.individualFromResponse(user, event, respondent, newStatus == ResponseStatus.ATTENDING,
                input.travelsIndividually());
        return new ResponseDto(newStatus, comment, EventReader.time(now),
                enteredBy == null ? null : PersonSummary.of(Person.findById(enteredBy)), lateCancellation,
                drivers.individual(event.id).contains(respondent) ? Boolean.TRUE : null);
    }

    // ------------------------------------------------------------------ Read

    /** Overview by team and group with coach block and counts (ANM-20 to ANM-32). */
    public OverviewDto overview(CurrentUser user, UUID eventId) {
        Event event = EventService.find(eventId);
        List<TeamMembership> members = members(event);
        Map<UUID, TeamRole> mine = teamRoles(members, user.personId());
        EventRules.checkCanView(user.allTeams(), mine.values());

        Map<UUID, Stored> responses = responses(event.id);
        Map<UUID, PersonSummary> persons = new HashMap<>();
        members.forEach(m -> persons.put(m.person.id, PersonSummary.of(m.person)));
        // Who travels on her own (ANM-13), visible to everyone who sees the responses
        Set<UUID> individual = event.type == EventType.MATCH_DAY ? drivers.individual(event.id) : Set.of();

        List<OverviewDto.TeamName> teams = teamNames(event);
        List<OverviewDto.TeamBlock> blocks = new ArrayList<>();
        List<OverviewDto.CoachEntry> coaches = new ArrayList<>();
        Set<UUID> allPlayers = new LinkedHashSet<>();
        Set<UUID> expectedPlayers = new LinkedHashSet<>();
        Set<UUID> invitedPlayers = new LinkedHashSet<>();
        // Warnings only for coaches and admins (missing for players), empty if they do not apply (F-16)
        List<WarningDto> warnings = WarningRules.visible(user.allTeams()) ? new ArrayList<>() : null;
        boolean withWarnings = warnings != null
                && WarningRules.applies(event.type, event.status, event.end, clock.now());
        for (OverviewDto.TeamName team : teams) {
            boolean commentsVisible = ResponseRules.commentVisible(user.allTeams(), false, mine.get(team.id()));
            List<TeamMembership> inTeam = members.stream().filter(m -> m.team.id.equals(team.id())).toList();
            List<TeamMembership> players = inTeam.stream().filter(m -> m.teamRole == TeamRole.PLAYER).toList();
            players.forEach(m -> allPlayers.add(m.person.id));

            Map<UUID, List<OverviewDto.Entry>> perGroup = new LinkedHashMap<>();
            List<OverviewDto.Entry> noLine = new ArrayList<>();
            for (TeamMembership m : players) {
                OverviewDto.Entry entry = new OverviewDto.Entry(persons.get(m.person.id),
                        response(responses.get(m.person.id), commentsVisible || m.person.id.equals(user.personId()), persons,
                                individual.contains(m.person.id)));
                if (m.groups.isEmpty()) {
                    noLine.add(entry);
                } else {
                    m.groups.forEach(g -> perGroup.computeIfAbsent(g.id, k -> new ArrayList<>()).add(entry));
                }
            }
            List<OverviewDto.Group> groups = new ArrayList<>();
            for (Group g : Group.of(Team.findById(team.id()), event.season)) {
                List<OverviewDto.Entry> entries = perGroup.get(g.id);
                if (entries != null && !entries.isEmpty()) {
                    groups.add(new OverviewDto.Group(g.id, g.type == GroupType.GOALIES
                            ? OverviewDto.Type.GOALIES : OverviewDto.Type.LINE, g.name, sorted(entries)));
                }
            }
            if (!noLine.isEmpty()) {
                groups.add(new OverviewDto.Group(null, OverviewDto.Type.NO_LINE, NO_LINE, sorted(noLine)));
            }
            boolean optional = event.optionalTeamIds.contains(team.id());
            List<UUID> playerIds = players.stream().map(m -> m.person.id).toList();
            // In a team invited without obligation only those who responded count (TER-15)
            blocks.add(new OverviewDto.TeamBlock(team, counts(optional ? responded(playerIds, responses) : playerIds,
                    responses), groups, optional));
            if (optional) {
                players.forEach(m -> invitedPlayers.add(m.person.id));
            } else {
                players.forEach(m -> expectedPlayers.add(m.person.id));
            }
            if (withWarnings && !optional) {
                Team entity = event.teams.stream().filter(t -> t.id.equals(team.id())).findFirst().orElseThrow();
                warnings.addAll(WarningRules.warnings(event.type, team, entity.minimums(),
                        warningPlayers(players, p -> isAttending(responses.get(p)))));
            }

            inTeam.stream().filter(m -> m.teamRole == TeamRole.COACH)
                    .map(m -> new OverviewDto.CoachEntry(persons.get(m.person.id), team,
                            response(responses.get(m.person.id), commentsVisible || m.person.id.equals(user.personId()), persons,
                                    individual.contains(m.person.id))))
                    .sorted(Comparator.comparing(OverviewDto.CoachEntry::person, PersonSummary.ORDER))
                    .forEach(coaches::add);
        }
        if (withWarnings && !teams.isEmpty()) {
            // Matches changed by swiss unihockey or no longer delivered (SUH-12)
            int[] su = EventReader.suWarnings(em, List.of(event.id)).getOrDefault(event.id, new int[2]);
            warnings.addAll(WarningRules.suWarnings(teams.get(0), su[0], su[1]));
        }
        Set<UUID> counted = new LinkedHashSet<>(expectedPlayers);
        counted.addAll(responded(invitedPlayers, responses));
        return new OverviewDto(event.id, counts(counted, responses), blocks, coaches, warnings);
    }

    /** Of these people, the ones who gave a response. */
    private static List<UUID> responded(java.util.Collection<UUID> persons, Map<UUID, Stored> responses) {
        return persons.stream().filter(p -> responses.containsKey(p)
                && responses.get(p).status() != ResponseStatus.PENDING).toList();
    }

    /** Players of a team for the warnings (F-16), with their groups and whether they count. */
    static List<WarningRules.Player> warningPlayers(List<TeamMembership> players,
                                                    java.util.function.Predicate<UUID> counted) {
        return players.stream().map(m -> new WarningRules.Player(m.person.id,
                m.groups.stream().map(Group::info).toList(), counted.test(m.person.id))).toList();
    }

    private static boolean isAttending(Stored stored) {
        return stored != null && stored.status() == ResponseStatus.ATTENDING;
    }

    // ------------------------------------------------------------------ Helpers

    /** Members of all participating teams in the season of the event, with person and groups. */
    List<TeamMembership> members(Event event) {
        List<UUID> teams = event.teams.stream().map(team -> team.id).toList();
        if (teams.isEmpty()) {
            return List.of();
        }
        return em.createQuery("""
                        select distinct z from TeamMembership z join fetch z.person left join fetch z.groups
                        where z.season.id = :season and z.team.id in :teams""", TeamMembership.class)
                .setParameter("season", event.season.id).setParameter("teams", teams).getResultList();
    }

    /** Team role of a person per participating team (empty: in no team of the event). */
    private static Map<UUID, TeamRole> teamRoles(List<TeamMembership> members, UUID person) {
        Map<UUID, TeamRole> teamRoles = new HashMap<>();
        members.stream().filter(m -> m.person.id.equals(person)).forEach(m -> teamRoles.put(m.team.id, m.teamRole));
        return teamRoles;
    }

    /** Teams of the event by name, with the name in the season of the event (SAI-02). */
    static List<OverviewDto.TeamName> teamNames(Event event) {
        Map<UUID, String> names = new HashMap<>();
        List<UUID> ids = event.teams.stream().map(team -> team.id).toList();
        if (!ids.isEmpty()) {
            TeamSeason.<TeamSeason>list("season.id = ?1 and team.id in ?2", event.season.id, ids)
                    .forEach(ts -> names.put(ts.team.id, ts.name));
        }
        return ids.stream().map(id -> new OverviewDto.TeamName(id, names.getOrDefault(id, "")))
                .sorted(Comparator.comparing(OverviewDto.TeamName::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(OverviewDto.TeamName::id))
                .toList();
    }

    Map<UUID, Stored> responses(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select person_id, status, comment, changed_at, entered_by, late_cancellation
                        from response where event_id = ?""")
                .setParameter(1, eventId).getResultList();
        Map<UUID, Stored> responses = new HashMap<>();
        for (Object[] row : rows) {
            responses.put((UUID) row[0], stored(row, 1));
        }
        return responses;
    }

    /** Previous response, locked for the duration of the transaction (concurrent changes, ANM-10). */
    private Stored locked(UUID eventId, UUID personId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select person_id, status, comment, changed_at, entered_by, late_cancellation
                        from response where event_id = ? and person_id = ? for update""")
                .setParameter(1, eventId).setParameter(2, personId).getResultList();
        return rows.isEmpty() ? null : stored(rows.get(0), 1);
    }

    private static Stored stored(Object[] row, int offset) {
        return new Stored(ResponseStatus.valueOf((String) row[offset]), (String) row[offset + 1], instant(row[offset + 2]),
                (UUID) row[offset + 3], Boolean.TRUE.equals(row[offset + 4]));
    }

    /** Response for display. Without permission the comment is left out (ANM-07). */
    static ResponseDto response(Stored stored, boolean withComment, Map<UUID, PersonSummary> persons) {
        return response(stored, withComment, persons, false);
    }

    /** As above; {@code individual}: the person travels to the match day on her own (ANM-13). */
    static ResponseDto response(Stored stored, boolean withComment, Map<UUID, PersonSummary> persons,
                                boolean individual) {
        if (stored == null) {
            return ResponseDto.PENDING;
        }
        PersonSummary enteredBy = null;
        if (stored.enteredBy() != null) {
            enteredBy = persons.computeIfAbsent(stored.enteredBy(),
                    id -> Person.<Person>findByIdOptional(id).map(PersonSummary::of).orElse(null));
        }
        return new ResponseDto(stored.status(), withComment ? stored.comment() : null, EventReader.time(stored.changedAt()),
                enteredBy, stored.lateCancellation(), individual ? Boolean.TRUE : null);
    }

    private static List<OverviewDto.Entry> sorted(List<OverviewDto.Entry> entries) {
        return entries.stream().sorted(Comparator.comparing(OverviewDto.Entry::person, PersonSummary.ORDER)).toList();
    }

    private static CountsDto counts(java.util.Collection<UUID> players, Map<UUID, Stored> responses) {
        return EventRules.count(players.stream()
                .map(p -> responses.containsKey(p) ? responses.get(p).status() : ResponseStatus.PENDING).toList());
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
