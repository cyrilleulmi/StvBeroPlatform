package ch.stvbero.platform.testsupport;

import ch.stvbero.platform.attendance.event.EventRules;
import ch.stvbero.platform.core.clock.BackendClock;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/**
 * Pinned events of both data sets (TER-26, E-114), so that a pinned event is visible on the start page right after
 * loading. A class of its own because {@link Seed} and {@link ClubSeed} are frozen in size (CodeSizeTest).
 */
@ApplicationScoped
@IfBuildProperty(name = "stv.test-support", stringValue = "true")
public class SeedPins {

    /** «Teamessen» of «Damen KF 1. Liga» in the club data set. */
    public static final UUID CLUB_TEAM_DINNER = UUID.nameUUIDFromBytes("teamessen-damen".getBytes());

    /** Petra Keller: coach of Team 1 in the test data set and of «Damen KF 1. Liga» in the club data set. */
    private static final int PETRA_TEST = 2;
    private static final int PETRA_CLUB = 4;
    private static final int DAMEN_1 = 3;

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    /** Test data set: event 06 «Teamessen» (Fri 11.12.2026, Teams 1 + 2 + 3), pinned by Petra on Sun 15.11. */
    @Transactional
    public void test() {
        em.createNativeQuery("update event set pinned_at = ?, pinned_by = ? where id = ?")
                .setParameter(1, LocalDateTime.of(2026, 11, 15, 20, 0).atZone(BackendClock.ZONE).toInstant())
                .setParameter(2, Seed.personId(PETRA_TEST)).setParameter(3, Seed.eventId(6))
                .executeUpdate();
    }

    /**
     * Club data set: a «Teamessen» of «Damen KF 1. Liga» on the Friday of next week, 19:00–22:00, pinned by its coach
     * Petra Keller a day ago.
     */
    @Transactional
    public void club() {
        LocalDate today = EventRules.date(clock.now());
        LocalDate day = today.plusWeeks(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
        em.createNativeQuery("""
                        insert into event (id, season_id, type, title, starts_at, ends_at, status, location_name,
                                           response_deadline_hours, pinned_at, pinned_by)
                        values (?, ?, 'OTHER', 'Teamessen', ?, ?, 'PLANNED', ?, ?, ?, ?)""")
                .setParameter(1, CLUB_TEAM_DINNER).setParameter(2, Seed.seasonId(2))
                .setParameter(3, EventRules.instant(day, LocalTime.of(19, 0)))
                .setParameter(4, EventRules.instant(day, LocalTime.of(22, 0)))
                .setParameter(5, "Gasthaus Rössli, Beromünster")
                .setParameter(6, EventRules.DEFAULT_RESPONSE_DEADLINE)
                .setParameter(7, clock.now().minusSeconds(24 * 3600))
                .setParameter(8, Seed.personId(PETRA_CLUB))
                .executeUpdate();
        em.createNativeQuery("insert into event_team (event_id, team_id) values (?, ?)")
                .setParameter(1, CLUB_TEAM_DINNER).setParameter(2, Seed.teamId(DAMEN_1))
                .executeUpdate();
    }
}
