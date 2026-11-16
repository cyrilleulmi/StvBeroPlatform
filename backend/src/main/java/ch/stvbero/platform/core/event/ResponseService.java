package ch.stvbero.platform.core.event;

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
 * Antworten auf Termine (ANM): eigene Antwort, stellvertretende Antwort und die Übersicht nach Team und
 * Linie. Rechte nach RR-02, RR-03 und RR-06, durchgesetzt hier im Backend (RR-07).
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

    /** Gespeicherte Antwort, wie sie in der Tabelle steht. */
    record Stored(ResponseStatus status, String comment, Instant changedAt, UUID enteredBy,
                       boolean lateCancellation) {
    }

    // ------------------------------------------------------------------ Schreiben

    /**
     * Antwort setzen oder ändern (ANM-01 bis ANM-12). {@code personId == null} oder die eigene ID: für sich
     * selbst, sonst stellvertretend (ANM-11, RR-06) mit «eingetragen von» (ANM-12).
     */
    @Transactional
    public ResponseDto set(CurrentUser user, UUID eventId, UUID personId, ResponseInput input) {
        Event t = EventService.find(eventId);
        List<TeamMembership> members = members(t);
        Map<UUID, TeamRole> mine = teamRoles(members, user.personId());
        UUID respondent;
        UUID enteredBy;
        if (personId == null || personId.equals(user.personId())) {
            ResponseRules.checkCanRespondForSelf(!mine.isEmpty());
            respondent = user.personId();
            enteredBy = null;
        } else {
            EventRules.checkCanView(user.admin(), mine.values());
            Person.<Person>findByIdOptional(personId)
                    .orElseThrow(() -> DomainError.notFound("Diese Person gibt es nicht."));
            Map<UUID, TeamRole> her = teamRoles(members, personId);
            if (her.isEmpty() && (user.admin() || mine.containsValue(TeamRole.COACH))) {
                throw DomainError.notFound("Diese Person gehört zu keinem Team dieses Termins.");
            }
            ResponseRules.checkCanRespondOnBehalf(user.admin(), mine, her);
            respondent = personId;
            enteredBy = user.personId();
        }
        Instant now = clock.now();
        ResponseRules.checkOpenForResponses(t.status, t.end, now);
        t.season.checkWritable();
        ResponseStatus newValue = input.status().status();
        ResponseRules.checkComment(newValue, input.comment());

        Stored previous = locked(t.id, respondent);
        boolean lateCancellation = ResponseRules.lateCancellation(
                previous == null ? ResponseStatus.PENDING : previous.status(), previous != null && previous.lateCancellation(), newValue,
                EventRules.responseDeadline(t.start, t.responseDeadlineHours), now);
        String comment = EventRules.blankToNull(input.comment());
        em.createNativeQuery("""
                        insert into response (event_id, person_id, status, comment, changed_at, entered_by, late_cancellation)
                        values (?, ?, ?, ?, ?, ?, ?)
                        on conflict (event_id, person_id) do update
                        set status = excluded.status, comment = excluded.comment, changed_at = excluded.changed_at,
                            entered_by = excluded.entered_by, late_cancellation = excluded.late_cancellation
                        """)
                .setParameter(1, t.id).setParameter(2, respondent).setParameter(3, newValue.name()).setParameter(4, comment)
                .setParameter(5, now).setParameter(6, enteredBy).setParameter(7, lateCancellation)
                .executeUpdate();
        notifications.responseChanged(user, t, members, respondent,
                previous == null ? ResponseStatus.PENDING : previous.status(), newValue, lateCancellation);
        return new ResponseDto(newValue, comment, EventReader.time(now),
                enteredBy == null ? null : PersonSummary.of(Person.findById(enteredBy)), lateCancellation);
    }

    // ------------------------------------------------------------------ Lesen

    /** Übersicht nach Team und Gruppe mit Coach-Block und Zählern (ANM-20 bis ANM-32). */
    public OverviewDto overview(CurrentUser user, UUID eventId) {
        Event t = EventService.find(eventId);
        List<TeamMembership> members = members(t);
        Map<UUID, TeamRole> mine = teamRoles(members, user.personId());
        EventRules.checkCanView(user.admin(), mine.values());

        Map<UUID, Stored> responses = responses(t.id);
        Map<UUID, PersonSummary> persons = new HashMap<>();
        members.forEach(z -> persons.put(z.person.id, PersonSummary.of(z.person)));

        List<OverviewDto.TeamName> teams = teamNames(t);
        List<OverviewDto.TeamBlock> blocks = new ArrayList<>();
        List<OverviewDto.CoachEntry> coaches = new ArrayList<>();
        Set<UUID> allPlayers = new LinkedHashSet<>();
        for (OverviewDto.TeamName team : teams) {
            boolean commentsVisible = ResponseRules.commentVisible(user.admin(), false, mine.get(team.id()));
            List<TeamMembership> inTeam = members.stream().filter(z -> z.team.id.equals(team.id())).toList();
            List<TeamMembership> players = inTeam.stream().filter(z -> z.teamRole == TeamRole.PLAYER).toList();
            players.forEach(z -> allPlayers.add(z.person.id));

            Map<UUID, List<OverviewDto.Entry>> perGroup = new LinkedHashMap<>();
            List<OverviewDto.Entry> noLine = new ArrayList<>();
            for (TeamMembership z : players) {
                OverviewDto.Entry entry = new OverviewDto.Entry(persons.get(z.person.id),
                        response(responses.get(z.person.id), commentsVisible || z.person.id.equals(user.personId()), persons));
                if (z.groups.isEmpty()) {
                    noLine.add(entry);
                } else {
                    z.groups.forEach(g -> perGroup.computeIfAbsent(g.id, k -> new ArrayList<>()).add(entry));
                }
            }
            List<OverviewDto.Group> groups = new ArrayList<>();
            for (Group g : Group.of(Team.findById(team.id()), t.season)) {
                List<OverviewDto.Entry> entries = perGroup.get(g.id);
                if (entries != null && !entries.isEmpty()) {
                    groups.add(new OverviewDto.Group(g.id, g.type == GroupType.GOALIES
                            ? OverviewDto.Type.GOALIES : OverviewDto.Type.LINE, g.name, sorted(entries)));
                }
            }
            if (!noLine.isEmpty()) {
                groups.add(new OverviewDto.Group(null, OverviewDto.Type.NO_LINE, NO_LINE, sorted(noLine)));
            }
            blocks.add(new OverviewDto.TeamBlock(team, counts(players.stream().map(z -> z.person.id).toList(), responses), groups));

            inTeam.stream().filter(z -> z.teamRole == TeamRole.COACH)
                    .map(z -> new OverviewDto.CoachEntry(persons.get(z.person.id), team,
                            response(responses.get(z.person.id), commentsVisible || z.person.id.equals(user.personId()), persons)))
                    .sorted(Comparator.comparing(OverviewDto.CoachEntry::person, PersonSummary.ORDER))
                    .forEach(coaches::add);
        }
        return new OverviewDto(t.id, counts(allPlayers, responses), blocks, coaches);
    }

    // ------------------------------------------------------------------ Hilfen

    /** Mitglieder aller beteiligten Teams in der Saison des Termins, mit Person und Gruppen. */
    List<TeamMembership> members(Event t) {
        List<UUID> teams = t.teams.stream().map(team -> team.id).toList();
        if (teams.isEmpty()) {
            return List.of();
        }
        return em.createQuery("""
                        select distinct z from TeamMembership z join fetch z.person left join fetch z.groups
                        where z.season.id = :season and z.team.id in :teams""", TeamMembership.class)
                .setParameter("season", t.season.id).setParameter("teams", teams).getResultList();
    }

    /** Funktion einer Person pro beteiligtem Team (leer: in keinem Team des Termins). */
    private static Map<UUID, TeamRole> teamRoles(List<TeamMembership> members, UUID person) {
        Map<UUID, TeamRole> teamRoles = new HashMap<>();
        members.stream().filter(z -> z.person.id.equals(person)).forEach(z -> teamRoles.put(z.team.id, z.teamRole));
        return teamRoles;
    }

    /** Teams des Termins nach Name, mit dem Namen in der Saison des Termins (SAI-02). */
    private static List<OverviewDto.TeamName> teamNames(Event t) {
        Map<UUID, String> names = new HashMap<>();
        List<UUID> ids = t.teams.stream().map(team -> team.id).toList();
        if (!ids.isEmpty()) {
            TeamSeason.<TeamSeason>list("season.id = ?1 and team.id in ?2", t.season.id, ids)
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
        for (Object[] z : rows) {
            responses.put((UUID) z[0], stored(z, 1));
        }
        return responses;
    }

    /** Bisherige Antwort, für die Dauer der Transaktion gesperrt (gleichzeitige Wechsel, ANM-10). */
    private Stored locked(UUID eventId, UUID personId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select person_id, status, comment, changed_at, entered_by, late_cancellation
                        from response where event_id = ? and person_id = ? for update""")
                .setParameter(1, eventId).setParameter(2, personId).getResultList();
        return rows.isEmpty() ? null : stored(rows.get(0), 1);
    }

    private static Stored stored(Object[] z, int from) {
        return new Stored(ResponseStatus.valueOf((String) z[from]), (String) z[from + 1], instant(z[from + 2]),
                (UUID) z[from + 3], Boolean.TRUE.equals(z[from + 4]));
    }

    /** Antwort für die Anzeige. Ohne Berechtigung fehlt der Kommentar (ANM-07). */
    static ResponseDto response(Stored a, boolean withComment, Map<UUID, PersonSummary> persons) {
        if (a == null) {
            return ResponseDto.PENDING;
        }
        PersonSummary startDate = null;
        if (a.enteredBy() != null) {
            startDate = persons.computeIfAbsent(a.enteredBy(),
                    id -> Person.<Person>findByIdOptional(id).map(PersonSummary::of).orElse(null));
        }
        return new ResponseDto(a.status(), withComment ? a.comment() : null, EventReader.time(a.changedAt()),
                startDate, a.lateCancellation());
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
