package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.notification.Message;
import ch.stvbero.platform.notification.NotificationType;
import ch.stvbero.platform.notification.Notifications;
import ch.stvbero.platform.core.person.Person;
import ch.stvbero.platform.core.person.PersonSummary;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.team.TeamMembership;
import ch.stvbero.platform.core.team.TeamRole;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Special permissions per event («Sonderberechtigungen», RR-09 to RR-14). Coaches and admins let a person
 * manage a single event like a coach, or every event of a series. The rights themselves are not stored here: a
 * request on {@code /api/events/{eventId}/…} of a person with a grant runs with coach rights for that event
 * ({@link CurrentUser#withEventGrant()}, set by the auth filter).
 */
@ApplicationScoped
public class EventGrants {

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    @Inject
    Notifications notifications;

    /** Whether the person has a special permission for the event, directly or through its series (RR-09, RR-10). */
    @Transactional
    public boolean has(UUID eventId, UUID personId) {
        return !em.createNativeQuery("""
                        select 1 from event_grant where event_id = :event and person_id = :person
                        union all
                        select 1 from series_grant g join event e on e.series_id = g.series_id
                        where e.id = :event and g.person_id = :person""")
                .setParameter("event", eventId).setParameter("person", personId)
                .setMaxResults(1).getResultList().isEmpty();
    }

    /** People with a special permission for the event, directly or through its series. */
    public Set<UUID> holders(Event event) {
        Set<UUID> holders = new LinkedHashSet<>();
        holders.addAll(direct(event.id));
        if (event.series != null) {
            holders.addAll(ofSeries(event.series.id));
        }
        return holders;
    }

    // ------------------------------------------------------------------ Event

    /** Who has a special permission for the event; everyone who may see the event (RR-13). */
    @Transactional
    public EventGrantsDto ofEvent(CurrentUser user, UUID eventId) {
        Event event = EventService.find(eventId);
        checkCanView(user, event.teams, event.season);
        List<EventGrantsDto.Grant> grants = new ArrayList<>();
        direct(event.id).forEach(id -> grants.add(new EventGrantsDto.Grant(summary(id), false)));
        if (event.series != null) {
            List<UUID> direct = direct(event.id);
            ofSeries(event.series.id).stream().filter(id -> !direct.contains(id))
                    .forEach(id -> grants.add(new EventGrantsDto.Grant(summary(id), true)));
        }
        return dto(user, grants, event.teams, event.season);
    }

    /** Set the special permissions of one event: coaches and admins only (RR-11). */
    @Transactional
    public EventGrantsDto changeEvent(CurrentUser user, UUID eventId, EventGrantsInput input) {
        Event event = EventService.find(eventId);
        checkCanManage(user);
        EventRules.checkChangeable(event.status, event.end, clock.now());
        Set<UUID> wanted = checkCandidates(input.personIds(), event.teams, event.season);
        List<UUID> before = direct(event.id);
        em.createNativeQuery("delete from event_grant where event_id = ?").setParameter(1, event.id).executeUpdate();
        Instant now = clock.now();
        for (UUID personId : wanted) {
            em.createNativeQuery("""
                            insert into event_grant (event_id, person_id, granted_by, granted_at) values (?, ?, ?, ?)""")
                    .setParameter(1, event.id).setParameter(2, personId).setParameter(3, user.personId())
                    .setParameter(4, now).executeUpdate();
        }
        notifyNew(user, wanted, before, event.id, EventNotifications.label(event));
        return ofEvent(user, eventId);
    }

    // ------------------------------------------------------------------ Series

    /** Who has a special permission for all events of the series (RR-10). */
    @Transactional
    public EventGrantsDto ofSeries(CurrentUser user, UUID seriesId) {
        Series series = series(seriesId);
        checkCanView(user, series.teams, series.season);
        List<EventGrantsDto.Grant> grants = ofSeries(series.id).stream()
                .map(id -> new EventGrantsDto.Grant(summary(id), true)).toList();
        return dto(user, grants, series.teams, series.season);
    }

    /** Set the special permissions of a series; they apply to all its events, also later ones (RR-10, RR-11). */
    @Transactional
    public EventGrantsDto changeSeries(CurrentUser user, UUID seriesId, EventGrantsInput input) {
        Series series = series(seriesId);
        checkCanManage(user);
        Set<UUID> wanted = checkCandidates(input.personIds(), series.teams, series.season);
        List<UUID> before = ofSeries(series.id);
        em.createNativeQuery("delete from series_grant where series_id = ?").setParameter(1, series.id).executeUpdate();
        Instant now = clock.now();
        for (UUID personId : wanted) {
            em.createNativeQuery("""
                            insert into series_grant (series_id, person_id, granted_by, granted_at) values (?, ?, ?, ?)""")
                    .setParameter(1, series.id).setParameter(2, personId).setParameter(3, user.personId())
                    .setParameter(4, now).executeUpdate();
        }
        // The notification leads to the next event of the series
        UUID next = Event.<Event>find("series.id = ?1 and end > ?2 and status = ?3 order by start",
                        series.id, now, EventStatus.PLANNED).firstResultOptional().map(e -> e.id).orElse(null);
        notifyNew(user, wanted, before, next, "«" + series.title + "» (alle Termine der Serie)");
        return ofSeries(user, seriesId);
    }

    // ------------------------------------------------------------------ Helpers

    private void notifyNew(CurrentUser user, Collection<UUID> now, Collection<UUID> before, UUID eventId,
                           String label) {
        List<Message> messages = new ArrayList<>();
        now.stream().filter(id -> !before.contains(id) && !id.equals(user.personId()))
                .forEach(id -> messages.add(new Message(NotificationType.EVENT_GRANTED, id, eventId,
                        NotificationRules.grantedText(label))));
        notifications.send(messages);
    }

    private EventGrantsDto dto(CurrentUser user, List<EventGrantsDto.Grant> grants, Set<Team> teams, Season season) {
        boolean canManage = user.coachOrAdmin();
        List<PersonSummary> candidates = canManage ? candidates(teams, season) : null;
        List<EventGrantsDto.Grant> sorted = new ArrayList<>(grants);
        sorted.sort((a, b) -> PersonSummary.ORDER.compare(a.person(), b.person()));
        return new EventGrantsDto(sorted, canManage, candidates);
    }

    /** Members (any team role) of the teams in the season; only they can get a special permission (RR-12). */
    private static List<PersonSummary> candidates(Set<Team> teams, Season season) {
        Map<UUID, PersonSummary> people = new LinkedHashMap<>();
        if (!teams.isEmpty()) {
            TeamMembership.<TeamMembership>list("season.id = ?1 and team in ?2", season.id, teams)
                    .forEach(m -> people.putIfAbsent(m.person.id, PersonSummary.of(m.person)));
        }
        return people.values().stream().sorted(PersonSummary.ORDER).toList();
    }

    private static Set<UUID> checkCandidates(List<UUID> personIds, Set<Team> teams, Season season) {
        Set<UUID> allowed = new LinkedHashSet<>();
        candidates(teams, season).forEach(p -> allowed.add(p.id()));
        Set<UUID> wanted = new LinkedHashSet<>(personIds);
        if (!allowed.containsAll(wanted)) {
            throw DomainError.ruleViolation("personIds", "Nicht im Team",
                    "Sonderberechtigungen gibt es nur für Mitglieder der Teams dieses Termins.", "RR-12");
        }
        return wanted;
    }

    /** Members of the teams and coaches/admins see who has special permissions (RR-13). */
    private static void checkCanView(CurrentUser user, Set<Team> teams, Season season) {
        if (user.allTeams()) {
            return;
        }
        List<TeamRole> roles = teams.isEmpty() ? List.of() : TeamMembership.<TeamMembership>list(
                        "person.id = ?1 and season.id = ?2 and team in ?3", user.personId(), season.id, teams)
                .stream().map(m -> m.teamRole).toList();
        EventRules.checkCanView(false, roles);
    }

    /** Only coaches and admins in their own right give special permissions, not those who have one (RR-11). */
    private static void checkCanManage(CurrentUser user) {
        if (!user.coachOrAdmin()) {
            throw DomainError.forbidden();
        }
    }

    private List<UUID> direct(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<UUID> ids = em.createNativeQuery("select person_id from event_grant where event_id = ?")
                .setParameter(1, eventId).getResultList();
        return ids;
    }

    private List<UUID> ofSeries(UUID seriesId) {
        @SuppressWarnings("unchecked")
        List<UUID> ids = em.createNativeQuery("select person_id from series_grant where series_id = ?")
                .setParameter(1, seriesId).getResultList();
        return ids;
    }

    private static PersonSummary summary(UUID personId) {
        return PersonSummary.of(Person.findById(personId));
    }

    private static Series series(UUID id) {
        return Series.<Series>findByIdOptional(id)
                .orElseThrow(() -> DomainError.notFound("Diese Serie gibt es nicht."));
    }
}
