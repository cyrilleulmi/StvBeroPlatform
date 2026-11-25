package ch.stvbero.platform.driver;

import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.person.Person;
import ch.stvbero.platform.core.person.PersonSummary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * History of the driver plan (DRV-26): who entered, took out, swapped or added which trip, and when. Entries of a
 * match day carry the event and so show for every team of the match day; other trips carry their team.
 */
@ApplicationScoped
public class DriverPlanHistory {

    /** Entries shown on the season page at most. */
    static final int LIMIT = 100;

    /** What happened. */
    public enum Kind {
        CAR_ADDED, CAR_REMOVED, SWAPPED, TAKEN_OVER, SWAP_REQUESTED, SWAP_DECLINED, SWAP_WITHDRAWN, TRIP_ADDED,
        TRIP_REMOVED
    }

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    /** Records a change of a match day, in the caller's transaction. */
    public void ofMatchDay(UUID eventId, UUID seasonId, Kind kind, UUID actor, String text) {
        insert(null, seasonId, eventId, kind, actor, text);
    }

    /** Records a change of the team that belongs to no match day (other trips), in the caller's transaction. */
    public void ofTeam(UUID teamId, UUID seasonId, Kind kind, UUID actor, String text) {
        insert(teamId, seasonId, null, kind, actor, text);
    }

    /** For the seeds: «Eingetragen» of a car that the seed entered directly, by its driver at {@code at}. */
    public void seedCarAdded(UUID eventId, UUID driverId, java.time.Instant at) {
        ch.stvbero.platform.attendance.event.Event event = ch.stvbero.platform.attendance.event.Event.findById(eventId);
        Person driver = Person.findById(driverId);
        String text = DriverRules.historyCarAdded(driver.firstName + " " + driver.lastName,
                ch.stvbero.platform.attendance.event.NotificationRules.label(event.title, event.start));
        em.createNativeQuery("""
                        insert into driver_plan_entry (id, team_id, season_id, event_id, kind, actor_id, text, created_at)
                        values (?, null, ?, ?, 'CAR_ADDED', ?, ?, ?)""")
                .setParameter(1, UUID.randomUUID()).setParameter(2, event.season.id).setParameter(3, eventId)
                .setParameter(4, driverId).setParameter(5, text).setParameter(6, at)
                .executeUpdate();
    }

    private void insert(UUID teamId, UUID seasonId, UUID eventId, Kind kind, UUID actor, String text) {
        em.createNativeQuery("""
                        insert into driver_plan_entry (id, team_id, season_id, event_id, kind, actor_id, text, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?)""")
                .setParameter(1, UUID.randomUUID()).setParameter(2, teamId).setParameter(3, seasonId)
                .setParameter(4, eventId).setParameter(5, kind.name()).setParameter(6, actor)
                .setParameter(7, text.length() > 500 ? text.substring(0, 500) : text).setParameter(8, clock.now())
                .executeUpdate();
    }

    /** The entries of a team in a season, newest first: those of its match days and its own. */
    public List<DriverPlanDto.HistoryEntry> of(UUID teamId, UUID seasonId, Collection<UUID> matchDays) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select id, event_id, kind, actor_id, text, created_at from driver_plan_entry
                        where season_id = :season and (team_id = :team or event_id in (:events))
                        order by created_at desc, seq desc
                        limit :limit""")
                .setParameter("season", seasonId).setParameter("team", teamId)
                .setParameter("events", matchDays.isEmpty() ? List.of(UUID.randomUUID()) : matchDays)
                .setParameter("limit", LIMIT).getResultList();
        Map<UUID, PersonSummary> actors = new HashMap<>();
        return rows.stream().map(row -> {
            UUID actor = (UUID) row[3];
            PersonSummary by = actor == null ? null : actors.computeIfAbsent(actor, id -> {
                Person person = Person.findById(id);
                return person == null ? null : PersonSummary.of(person);
            });
            return new DriverPlanDto.HistoryEntry((UUID) row[0], (UUID) row[1], Kind.valueOf((String) row[2]),
                    (String) row[4], by, Routes.instant(row[5]).atZone(BackendClock.ZONE).toOffsetDateTime());
        }).toList();
    }
}
