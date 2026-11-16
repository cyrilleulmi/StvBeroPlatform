package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.auth.CurrentUser;
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
 * Macht aus Terminen die Antworten der API. Lädt Teamnamen, Teamzugehörigkeiten und Antworten für alle
 * Termine auf einmal, damit die Terminliste nicht pro Termin die Datenbank fragt.
 */
final class EventReader {

    private record TeamInSeason(UUID team, UUID season) {
    }

    private record Member(UUID person, TeamRole teamRole) {
    }

    private record StoredResponse(ResponseStatus status, String comment, Instant changedAt,
                                       UUID enteredBy, boolean lateCancellation) {
    }

    private final CurrentUser user;
    private final Map<TeamInSeason, String> names = new HashMap<>();
    private final Map<TeamInSeason, List<Member>> members = new HashMap<>();
    private final Map<UUID, Map<UUID, StoredResponse>> responses = new HashMap<>();

    EventReader(EntityManager em, CurrentUser user, Collection<Event> events) {
        this.user = user;
        Set<UUID> seasons = new LinkedHashSet<>();
        events.forEach(t -> seasons.add(t.season.id));
        if (!seasons.isEmpty()) {
            TeamSeason.<TeamSeason>list("season.id in ?1", seasons)
                    .forEach(ts -> names.put(new TeamInSeason(ts.team.id, ts.season.id), ts.name));
            TeamMembership.<TeamMembership>list("season.id in ?1", seasons).forEach(z -> members
                    .computeIfAbsent(new TeamInSeason(z.team.id, z.season.id), k -> new ArrayList<>())
                    .add(new Member(z.person.id, z.teamRole)));
        }
        List<UUID> ids = events.stream().map(t -> t.id).toList();
        if (!ids.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("""
                            select event_id, person_id, status, comment, changed_at, entered_by, late_cancellation
                            from response where event_id in (:ids)
                            """)
                    .setParameter("ids", ids).getResultList();
            for (Object[] z : rows) {
                responses.computeIfAbsent((UUID) z[0], k -> new HashMap<>()).put((UUID) z[1],
                        new StoredResponse(ResponseStatus.valueOf((String) z[2]), (String) z[3], instant(z[4]),
                                (UUID) z[5], Boolean.TRUE.equals(z[6])));
            }
        }
    }

    /** Funktionen der angemeldeten Person in den Teams des Termins (in der Saison des Termins). */
    Set<TeamRole> myTeamRoles(Event t) {
        Set<TeamRole> teamRoles = new LinkedHashSet<>();
        for (Team team : t.teams) {
            for (Member m : members.getOrDefault(new TeamInSeason(team.id, t.season.id), List.of())) {
                if (m.person().equals(user.personId())) {
                    teamRoles.add(m.teamRole());
                }
            }
        }
        return teamRoles;
    }

    EventSummaryDto summary(Event t) {
        return new EventSummaryDto(t.id, t.type, t.title, time(t.start), time(t.end), t.status, t.cancellationReason,
                t.rescheduled, teams(t), t.location == null ? null : t.location.dto(),
                time(EventRules.responseDeadline(t.start, t.responseDeadlineHours)), myResponse(t), counts(t),
                t.series == null ? null : t.series.id);
    }

    /**
     * Termin mit allen Angaben. Bei Spieltagen mit Spielen nach Anspielzeit, Stand des Aufgebots (vor der
     * Veröffentlichung nur für Coaches und Admins, AUF-23) und eigenem Aufgebotsstatus für Spielerinnen (AUF-24).
     */
    EventDto full(Event t, boolean canEdit) {
        EventSummaryDto k = summary(t);
        boolean matchDay = t.type == EventType.MATCH_DAY;
        MeetingPointDto meetingPoint = t.meetingPointTime == null ? null : new MeetingPointDto(time(t.meetingPointTime), t.meetingPointLocation);
        List<MatchDto> matches = null;
        CallUpStatus callUpStatus = null;
        MyCallUp myCallUp = null;
        if (matchDay) {
            matches = Match.of(t.id).stream().map(Match::dto).toList();
            CallUp callUp = CallUp.findById(t.id);
            Set<TeamRole> mine = myTeamRoles(t);
            CallUpStatus status = callUp == null ? CallUpStatus.NONE : callUp.status();
            callUpStatus = CallUpRules.visibleStatus(status,
                    CallUpRules.canEdit(user.admin(), mine));
            if (mine.contains(TeamRole.PLAYER)) {
                myCallUp = CallUpRules.myCallUp(status,
                        callUp != null && callUp.personIds.contains(user.personId()));
            }
        }
        return new EventDto(k.id(), k.type(), k.title(), k.start(), k.end(), k.status(), k.cancellationReason(),
                k.rescheduled(), k.teams(), k.location(), k.responseDeadline(), k.myResponse(), k.counts(), k.seriesId(),
                t.notes, t.responseDeadlineHours, meetingPoint, matches, callUpStatus, myCallUp, canEdit);
    }

    /** Teams nach Name, mit dem Namen in der Saison des Termins (SAI-02) und der eigenen Funktion. */
    private List<TeamDto> teams(Event t) {
        List<TeamDto> teams = new ArrayList<>();
        for (Team team : t.teams) {
            TeamInSeason k = new TeamInSeason(team.id, t.season.id);
            TeamRole mine = members.getOrDefault(k, List.of()).stream()
                    .filter(m -> m.person().equals(user.personId())).map(Member::teamRole).findFirst().orElse(null);
            teams.add(new TeamDto(team.id, names.getOrDefault(k, ""), team.suTeamId, mine));
        }
        teams.sort(Comparator.comparing(TeamDto::name, String.CASE_INSENSITIVE_ORDER).thenComparing(TeamDto::id));
        return teams;
    }

    private ResponseDto myResponse(Event t) {
        StoredResponse a = responses.getOrDefault(t.id, Map.of()).get(user.personId());
        if (a == null) {
            return ResponseDto.PENDING;
        }
        PersonSummary startDate = a.enteredBy() == null ? null
                : Person.<Person>findByIdOptional(a.enteredBy()).map(PersonSummary::of).orElse(null);
        return new ResponseDto(a.status(), a.comment(), time(a.changedAt()), startDate, a.lateCancellation());
    }

    /** Gesamtzähler: jede Spielerin eines beteiligten Teams einmal, Coaches zählen nicht (PTF-07). */
    private CountsDto counts(Event t) {
        Set<UUID> players = new LinkedHashSet<>();
        for (Team team : t.teams) {
            members.getOrDefault(new TeamInSeason(team.id, t.season.id), List.of()).stream()
                    .filter(m -> m.teamRole() == TeamRole.PLAYER).forEach(m -> players.add(m.person()));
        }
        Map<UUID, StoredResponse> toTheEvent = responses.getOrDefault(t.id, Map.of());
        return EventRules.count(players.stream()
                .map(p -> toTheEvent.containsKey(p) ? toTheEvent.get(p).status() : ResponseStatus.PENDING).toList());
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
