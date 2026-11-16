package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.line.Group;
import ch.stvbero.platform.core.person.PersonSummary;
import ch.stvbero.platform.core.team.TeamRole;
import ch.stvbero.platform.core.team.GroupType;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.team.TeamMembership;
import ch.stvbero.platform.core.clock.BackendClock;
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
 * Aufgebot eines Spieltags (AUF-21 bis AUF-28): Auswahl nach Linien mit Antwortstatus, speichern und
 * veröffentlichen. Nur Coaches des Teams und Admins (RR-03), durchgesetzt hier im Backend (RR-07).
 */
@ApplicationScoped
public class CallUpService {

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    @Inject
    ResponseService responses;

    @Inject
    EventNotifications notifications;

    public CallUpDto get(CurrentUser user, UUID eventId) {
        Event t = EventService.find(eventId);
        checkCanEdit(user, t);
        CallUpRules.checkMatchDay(t.type);
        return dto(t, CallUp.findById(t.id));
    }

    /**
     * Auswahl speichern (AUF-21, AUF-22). Jede Spielerin des Teams lässt sich aufbieten, unabhängig von
     * ihrer Antwort; Coaches nicht (AUF-28). Ist das Aufgebot veröffentlicht, bleibt es das, und wer neu
     * dazukommt oder herausfällt, wird benachrichtigt (AUF-27, BEN-05).
     */
    @Transactional
    public CallUpDto set(CurrentUser user, UUID eventId, CallUpInput input) {
        Event t = EventService.find(eventId);
        checkCanEdit(user, t);
        CallUpRules.checkMatchDay(t.type);
        EventRules.checkChangeable(t.status, t.end, clock.now());
        t.season.checkWritable();
        Set<UUID> selection = new LinkedHashSet<>(input.personIds());
        CallUpRules.checkCanBeCalledUp(selection, players(t));
        CallUp a = locked(t.id);
        Set<UUID> previous = new LinkedHashSet<>(a.personIds);
        a.personIds.clear();
        a.personIds.addAll(selection);
        if (a.publishedAt != null) {
            notifications.callUpChanged(user, t, previous, selection);
        }
        return dto(t, a);
    }

    /**
     * Aufgebot veröffentlichen (AUF-23, AUF-24). Danach sehen alle Spielerinnen des Teams, ob sie aufgeboten
     * sind und werden benachrichtigt (BEN-03).
     */
    @Transactional
    public CallUpDto publish(CurrentUser user, UUID eventId) {
        Event t = EventService.find(eventId);
        checkCanEdit(user, t);
        CallUpRules.checkMatchDay(t.type);
        EventRules.checkChangeable(t.status, t.end, clock.now());
        t.season.checkWritable();
        CallUp a = locked(t.id);
        a.personIds.retainAll(players(t));
        CallUpRules.checkPublishable(a.status());
        a.publishedAt = clock.now();
        notifications.published(user, t, players(t), a.personIds);
        return dto(t, a);
    }

    private void checkCanEdit(CurrentUser user, Event t) {
        CallUpRules.checkCanEdit(user.admin(), EventService.teamRoles(user, t.teams, t.season));
    }

    /** Aufgebot des Termins, für die Transaktion gesperrt; zwei gleichzeitige Anfragen ergeben nie 500. */
    private CallUp locked(UUID eventId) {
        em.createNativeQuery("insert into call_up (event_id) values (?) on conflict do nothing")
                .setParameter(1, eventId).executeUpdate();
        return em.find(CallUp.class, eventId, LockModeType.PESSIMISTIC_WRITE);
    }

    /** Personen mit Funktion Spielerin im Team des Spieltags (in seiner Saison). */
    private Set<UUID> players(Event t) {
        Set<UUID> ids = new LinkedHashSet<>();
        responses.members(t).stream().filter(z -> z.teamRole == TeamRole.PLAYER).forEach(z -> ids.add(z.person.id));
        return ids;
    }

    /** Alle Spielerinnen nach Linie, dann Goalies, dann «Ohne Linie»; leere Gruppen fehlen (wie die Übersicht). */
    private CallUpDto dto(Event t, CallUp a) {
        Set<UUID> calledUp = a == null ? Set.of() : a.personIds;
        Map<UUID, ResponseService.Stored> stored = responses.responses(t.id);
        List<TeamMembership> players = responses.members(t).stream()
                .filter(z -> z.teamRole == TeamRole.PLAYER).toList();
        Map<UUID, PersonSummary> persons = new HashMap<>();
        players.forEach(z -> persons.put(z.person.id, PersonSummary.of(z.person)));

        Map<UUID, List<CallUpDto.Entry>> perGroup = new LinkedHashMap<>();
        List<CallUpDto.Entry> noLine = new ArrayList<>();
        int count = 0;
        for (TeamMembership z : players) {
            boolean yes = calledUp.contains(z.person.id);
            count += yes ? 1 : 0;
            CallUpDto.Entry e = new CallUpDto.Entry(persons.get(z.person.id),
                    ResponseService.response(stored.get(z.person.id), true, persons), yes);
            if (z.groups.isEmpty()) {
                noLine.add(e);
            } else {
                z.groups.forEach(g -> perGroup.computeIfAbsent(g.id, k -> new ArrayList<>()).add(e));
            }
        }
        List<CallUpDto.Group> groups = new ArrayList<>();
        for (Team team : t.teams) {
            for (Group g : Group.of(team, t.season)) {
                List<CallUpDto.Entry> entries = perGroup.get(g.id);
                if (entries != null && !entries.isEmpty()) {
                    groups.add(new CallUpDto.Group(g.id, g.type == GroupType.GOALIES
                            ? OverviewDto.Type.GOALIES : OverviewDto.Type.LINE, g.name, sorted(entries)));
                }
            }
        }
        if (!noLine.isEmpty()) {
            groups.add(new CallUpDto.Group(null, OverviewDto.Type.NO_LINE, ResponseService.NO_LINE,
                    sorted(noLine)));
        }
        boolean published = a != null && a.publishedAt != null;
        return new CallUpDto(t.id, CallUpRules.status(published, count),
                a == null ? null : EventReader.time(a.publishedAt), count, groups);
    }

    private static List<CallUpDto.Entry> sorted(List<CallUpDto.Entry> entries) {
        return entries.stream().sorted(Comparator.comparing(CallUpDto.Entry::person, PersonSummary.ORDER)).toList();
    }
}
