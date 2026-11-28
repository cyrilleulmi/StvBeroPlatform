package ch.stvbero.platform.testsupport;

import ch.stvbero.platform.core.clock.BackendClock;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Pre-trainings of the club data set (TER-29, TER-30, E-117) with their responses (ANM-14), set after the seed. A
 * class of its own because {@link ClubSeed} is frozen in size (CodeSizeTest). Each one is on the series and on every
 * event of the series.
 */
@ApplicationScoped
@IfBuildProperty(name = "stv.test-support", stringValue = "true")
public class SeedPreTrainings {

    /** Thursday practice of the women (20:20): «Joggen» at 20:00. */
    public static final int THURSDAY_SERIES = 4;
    /** Friday practice of the women (19:15): «Kraft &amp; Stabilität» at 19:00. */
    public static final int FRIDAY_SERIES = 11;

    @Inject
    EntityManager em;

    @Transactional
    public void club() {
        set(THURSDAY_SERIES, "Joggen", LocalTime.of(20, 0));
        set(FRIDAY_SERIES, "Kraft & Stabilität", LocalTime.of(19, 0));
    }

    private void set(int seriesNr, String title, LocalTime time) {
        UUID series = Seed.seriesId(seriesNr);
        em.createNativeQuery("update series set pre_training_title = ?, pre_training_time = ? where id = ?")
                .setParameter(1, title).setParameter(2, time).setParameter(3, series).executeUpdate();
        // The moment is the day of the event at that time of day in Zurich (summer and winter time)
        em.createNativeQuery("""
                        update event set pre_training_title = ?,
                            pre_training_starts_at = ((starts_at at time zone ?)::date + cast(? as time)) at time zone ?
                        where series_id = ?""")
                .setParameter(1, title).setParameter(2, BackendClock.ZONE.getId()).setParameter(3, time)
                .setParameter(4, BackendClock.ZONE.getId()).setParameter(5, series).executeUpdate();
        // Of those who are «dabei» most said whether they are at the pre-training too, some not yet (ANM-14).
        // Always the same for the same event and person.
        em.createNativeQuery("""
                        update response r set pre_training_status = case
                            when abs(hashtext(r.event_id::text || r.person_id::text)) % 10 < 6 then 'ATTENDING'
                            when abs(hashtext(r.event_id::text || r.person_id::text)) % 10 < 8 then 'DECLINED'
                            end
                        from event e
                        where e.id = r.event_id and e.series_id = ? and r.status = 'ATTENDING'""")
                .setParameter(1, series).executeUpdate();
    }
}
