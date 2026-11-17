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
 * Call-up of a match day (AUF-21 to AUF-28): selection by line with response status, save and publish.
 * Only coaches of the team and admins (RR-03), enforced here in the backend (RR-07).
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
        Event event = EventService.find(eventId);
        checkCanEdit(user, event);
        CallUpRules.checkMatchDay(event.type);
        return dto(event, CallUp.findById(event.id));
    }

    /**
     * Save the selection (AUF-21, AUF-22). Every player of the team can be called up, regardless of her
     * response; coaches cannot (AUF-28). If the call-up is published, it stays published, and whoever is
     * added or removed is notified (AUF-27, BEN-05).
     */
    @Transactional
    public CallUpDto set(CurrentUser user, UUID eventId, CallUpInput input) {
        Event event = EventService.find(eventId);
        checkCanEdit(user, event);
        CallUpRules.checkMatchDay(event.type);
        EventRules.checkChangeable(event.status, event.end, clock.now());
        event.season.checkWritable();
        Set<UUID> selection = new LinkedHashSet<>(input.personIds());
        CallUpRules.checkCanBeCalledUp(selection, players(event));
        CallUp callUp = locked(event.id);
        Set<UUID> previousSelection = new LinkedHashSet<>(callUp.personIds);
        callUp.personIds.clear();
        callUp.personIds.addAll(selection);
        if (callUp.publishedAt != null) {
            notifications.callUpChanged(user, event, previousSelection, selection);
        }
        return dto(event, callUp);
    }

    /**
     * Publish the call-up (AUF-23, AUF-24). Afterwards all players of the team see whether they are called up
     * and are notified (BEN-03).
     */
    @Transactional
    public CallUpDto publish(CurrentUser user, UUID eventId) {
        Event event = EventService.find(eventId);
        checkCanEdit(user, event);
        CallUpRules.checkMatchDay(event.type);
        EventRules.checkChangeable(event.status, event.end, clock.now());
        event.season.checkWritable();
        CallUp callUp = locked(event.id);
        callUp.personIds.retainAll(players(event));
        CallUpRules.checkPublishable(callUp.status());
        callUp.publishedAt = clock.now();
        notifications.published(user, event, players(event), callUp.personIds);
        return dto(event, callUp);
    }

    private void checkCanEdit(CurrentUser user, Event event) {
        CallUpRules.checkCanEdit(user.allTeams(), EventService.teamRoles(user, event.teams, event.season));
    }

    /** Call-up of the event, locked for the transaction; two concurrent requests never result in a 500. */
    private CallUp locked(UUID eventId) {
        em.createNativeQuery("insert into call_up (event_id) values (?) on conflict do nothing")
                .setParameter(1, eventId).executeUpdate();
        return em.find(CallUp.class, eventId, LockModeType.PESSIMISTIC_WRITE);
    }

    /** Persons with the team role player in the team of the match day (in its season). */
    private Set<UUID> players(Event event) {
        Set<UUID> ids = new LinkedHashSet<>();
        responses.members(event).stream().filter(m -> m.teamRole == TeamRole.PLAYER).forEach(m -> ids.add(m.person.id));
        return ids;
    }

    /** All players by line, then goalies, then «Ohne Linie»; empty groups are left out (like the overview). */
    private CallUpDto dto(Event event, CallUp callUp) {
        Set<UUID> calledUp = callUp == null ? Set.of() : callUp.personIds;
        Map<UUID, ResponseService.Stored> stored = responses.responses(event.id);
        List<TeamMembership> players = responses.members(event).stream()
                .filter(m -> m.teamRole == TeamRole.PLAYER).toList();
        Map<UUID, PersonSummary> persons = new HashMap<>();
        players.forEach(m -> persons.put(m.person.id, PersonSummary.of(m.person)));

        Map<UUID, List<CallUpDto.Entry>> perGroup = new LinkedHashMap<>();
        List<CallUpDto.Entry> noLine = new ArrayList<>();
        int count = 0;
        for (TeamMembership m : players) {
            boolean selected = calledUp.contains(m.person.id);
            count += selected ? 1 : 0;
            CallUpDto.Entry entry = new CallUpDto.Entry(persons.get(m.person.id),
                    ResponseService.response(stored.get(m.person.id), true, persons), selected);
            if (m.groups.isEmpty()) {
                noLine.add(entry);
            } else {
                m.groups.forEach(g -> perGroup.computeIfAbsent(g.id, k -> new ArrayList<>()).add(entry));
            }
        }
        List<CallUpDto.Group> groups = new ArrayList<>();
        for (Team team : event.teams) {
            for (Group g : Group.of(team, event.season)) {
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
        boolean published = callUp != null && callUp.publishedAt != null;
        return new CallUpDto(event.id, CallUpRules.status(published, count),
                callUp == null ? null : EventReader.time(callUp.publishedAt), count, groups);
    }

    private static List<CallUpDto.Entry> sorted(List<CallUpDto.Entry> entries) {
        return entries.stream().sorted(Comparator.comparing(CallUpDto.Entry::person, PersonSummary.ORDER)).toList();
    }
}
