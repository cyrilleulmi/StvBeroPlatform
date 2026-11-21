package ch.stvbero.platform.core.testsupport;

import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.driver.MatchDayRoutes;
import ch.stvbero.platform.core.driver.Place;
import ch.stvbero.platform.core.driver.Routes;
import ch.stvbero.platform.core.event.Event;
import ch.stvbero.platform.core.event.EventType;
import ch.stvbero.platform.core.event.Match;
import ch.stvbero.platform.core.event.MatchSource;
import ch.stvbero.platform.core.mode.Simulation;
import ch.stvbero.platform.core.swissunihockey.SuSync;
import ch.stvbero.platform.core.swissunihockey.SyncJob;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loads the matches of the linked teams of the club data set from swiss unihockey, in the background, so that
 * the app is usable right after the start (docs/test-data.md, club data set). Only in the profiles dev and test.
 * <p>
 * After loading it prepares what cannot be waited for with real data:
 * <ul>
 *   <li>«Damen KF 1. Liga»: one upcoming match is set to an earlier time and another opponent, one invented
 *       match is added, then the team is synced again. Result: a warning «von swiss unihockey geändert» with
 *       before and after, and a match «nicht mehr vorhanden» (SUH-12). The next match day also gets cars and
 *       past match days get drivers, for the driver plan (DRV-14, DRV-17).</li>
 *   <li>«Herren KF 3. Liga»: one upcoming match is set to an earlier time and <em>not</em> synced again. The
 *       next «Jetzt abgleichen» shows the change happening.</li>
 * </ul>
 * If swiss unihockey cannot be reached, nothing of this happens: the links stay, their last sync counts as
 * failed (SUH-13) and the app runs with the hand-entered data (SUH-09).
 */
@ApplicationScoped
@IfBuildProperty(name = "stv.test-support", stringValue = "true")
public class ClubLoader {

    private static final Logger LOG = Logger.getLogger(ClubLoader.class);

    /** Team whose changes are already synced: the warnings are there right away. */
    static final int WARNINGS_TEAM = 3;
    /** Team whose change shows with the next manual sync. */
    static final int PENDING_CHANGE_TEAM = 1;
    /** Match ID at swiss unihockey of the invented match that «no longer exists». */
    static final int INVENTED_MATCH = 990000001;

    @Inject
    SyncJob job;

    @Inject
    SuSync sync;

    @Inject
    Simulation simulation;

    @Inject
    BackendClock clock;

    @Inject
    EntityManager em;

    @Inject
    Routes routes;

    @Inject
    MatchDayRoutes matchDayRoutes;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "club-loader");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean loading = new AtomicBoolean();
    private volatile boolean stopped;

    /** Is the club data still being loaded from swiss unihockey? */
    public boolean loading() {
        return loading.get();
    }

    /** Starts loading in the background. A load of an earlier data set stops at its next step. */
    public void start() {
        int generation = simulation.generation();
        loading.set(true);
        executor.submit(() -> {
            try {
                load(generation);
            } catch (RuntimeException e) {
                LOG.errorf(e, "Loading the club data from swiss unihockey failed");
            } finally {
                if (generation == simulation.generation()) {
                    loading.set(false);
                }
            }
        });
    }

    void onStop(@Observes ShutdownEvent event) {
        stopped = true;
        executor.shutdownNow();
    }

    private boolean outdated(int generation) {
        return stopped || generation != simulation.generation() || simulation.on();
    }

    private void load(int generation) {
        List<UUID> synced = new ArrayList<>();
        for (ClubSeed.ClubTeam team : ClubSeed.TEAMS) {
            if (outdated(generation)) {
                return;
            }
            if (team.linked() && job.load(Seed.teamId(team.nr()))) {
                synced.add(Seed.teamId(team.nr()));
            }
        }
        LOG.infof("Club data: %d of %d linked teams loaded from swiss unihockey", synced.size(),
                ClubSeed.TEAMS.stream().filter(ClubSeed.ClubTeam::linked).count());
        if (outdated(generation)) {
            return;
        }
        UUID warningsTeam = Seed.teamId(WARNINGS_TEAM);
        if (synced.contains(warningsTeam)) {
            QuarkusTransaction.requiringNew().run(() -> changeUpcomingMatch(warningsTeam, true));
            try {
                sync.sync(warningsTeam);
            } catch (RuntimeException e) {
                LOG.warnf("Club data: second sync of the team with the warnings failed: %s", e.getMessage());
            }
            QuarkusTransaction.requiringNew().run(() -> drivers(warningsTeam));
        }
        UUID pendingTeam = Seed.teamId(PENDING_CHANGE_TEAM);
        if (synced.contains(pendingTeam) && !outdated(generation)) {
            QuarkusTransaction.requiringNew().run(() -> changeUpcomingMatch(pendingTeam, false));
        }
        // Routes of the next match days, so that driver plan and timeline open without waiting (DRV-05)
        for (UUID team : synced) {
            for (UUID eventId : QuarkusTransaction.requiringNew().call(() -> upcoming(team).stream().limit(4)
                    .map(event -> event.id).toList())) {
                if (outdated(generation)) {
                    return;
                }
                Optional<Place[]> places = QuarkusTransaction.requiringNew().call(() -> {
                    Event event = Event.findById(eventId);
                    return event != null && MatchDayRoutes.missing(event).isEmpty()
                            ? Optional.of(matchDayRoutes.places(event)) : Optional.<Place[]>empty();
                });
                places.ifPresent(p -> routes.ensure(eventId, p[0], p[1]));
            }
        }
    }

    /** Upcoming imported match days of a team in the current season, the next one first. */
    private List<Event> upcoming(UUID team) {
        return em.createQuery("""
                        select distinct e from Event e join e.teams t
                        where t.id = :team and e.type = :type and e.season.current = true and e.start > :now
                          and exists (select m.id from Match m where m.event = e and m.source = :source)
                        order by e.start""", Event.class)
                .setParameter("team", team).setParameter("type", EventType.MATCH_DAY)
                .setParameter("now", clock.now()).setParameter("source", MatchSource.SWISS_UNIHOCKEY)
                .getResultList();
    }

    /**
     * Makes the first match of the next match day differ from swiss unihockey: 30 minutes earlier and, with
     * {@code inventMatch}, another opponent plus an invented second match. The next sync puts it right and
     * reports what changed (SUH-05, SUH-12).
     */
    private void changeUpcomingMatch(UUID team, boolean inventMatch) {
        List<Event> upcoming = upcoming(team);
        if (upcoming.isEmpty()) {
            return;
        }
        Event matchDay = upcoming.get(0);
        List<Match> matches = Match.of(matchDay.id).stream()
                .filter(match -> match.source == MatchSource.SWISS_UNIHOCKEY).toList();
        Match first = matches.get(0);
        first.startTime = first.startTime.minusSeconds(30 * 60);
        if (inventMatch) {
            first.opponent = "UHC Beispiel (alter Gegner)";
            Match invented = Match.create(matchDay);
            invented.source = MatchSource.SWISS_UNIHOCKEY;
            invented.suMatchId = INVENTED_MATCH;
            invented.opponent = "Unihockey Erfunden";
            invented.home = false;
            invented.startTime = matches.get(matches.size() - 1).startTime.plusSeconds(110 * 60);
            invented.persist();
        }
        matchDay.start = first.startTime;
    }

    /**
     * Drivers for the team with the warnings: the next match day gets a full car, a car with free seats and
     * some players «dabei» without a seat; the last three match days get one car each (DRV-14, DRV-17).
     */
    private void drivers(UUID team) {
        Instant now = clock.now();
        List<UUID> players = playerIds(team);
        if (players.size() < 8) {
            return;
        }
        List<Event> upcoming = upcoming(team);
        if (!upcoming.isEmpty()) {
            Event next = upcoming.get(0);
            UUID full = car(next.id, players.get(0), 3, now);
            for (int i = 1; i <= 3; i++) {
                passenger(full, next.id, players.get(i), now);
            }
            car(next.id, players.get(4), 4, now);
            for (int i = 0; i < 8; i++) {
                em.createNativeQuery("""
                                insert into response (event_id, person_id, status, changed_at, late_cancellation)
                                values (?, ?, 'ATTENDING', ?, false) on conflict do nothing""")
                        .setParameter(1, next.id).setParameter(2, players.get(i)).setParameter(3, now)
                        .executeUpdate();
            }
        }
        List<Event> past = em.createQuery("""
                        select distinct e from Event e join e.teams t
                        where t.id = :team and e.type = :type and e.season.current = true and e.end <= :now
                        order by e.start desc""", Event.class)
                .setParameter("team", team).setParameter("type", EventType.MATCH_DAY).setParameter("now", now)
                .setMaxResults(3).getResultList();
        for (int i = 0; i < past.size(); i++) {
            Event event = past.get(i);
            UUID car = car(event.id, players.get(i == 2 ? 0 : i + 5), 4, event.start);
            passenger(car, event.id, players.get(i + 1), event.start);
            passenger(car, event.id, players.get(i + 2), event.start);
        }
    }

    private List<UUID> playerIds(UUID team) {
        @SuppressWarnings("unchecked")
        List<UUID> ids = em.createNativeQuery("""
                        select m.person_id from team_membership m join season s on s.id = m.season_id
                        join person p on p.id = m.person_id
                        where m.team_id = ? and s.is_current and m.team_role = 'PLAYER'
                        order by p.last_name, p.first_name""")
                .setParameter(1, team).getResultList();
        return ids;
    }

    private UUID car(UUID event, UUID driver, int seats, Instant at) {
        UUID id = UUID.randomUUID();
        em.createNativeQuery("insert into car (id, event_id, driver_id, seats, created_at) values (?, ?, ?, ?, ?)")
                .setParameter(1, id).setParameter(2, event).setParameter(3, driver).setParameter(4, seats)
                .setParameter(5, at).executeUpdate();
        return id;
    }

    private void passenger(UUID car, UUID event, UUID person, Instant at) {
        em.createNativeQuery("insert into car_passenger (car_id, event_id, person_id, added_at) values (?, ?, ?, ?)")
                .setParameter(1, car).setParameter(2, event).setParameter(3, person).setParameter(4, at)
                .executeUpdate();
    }
}
