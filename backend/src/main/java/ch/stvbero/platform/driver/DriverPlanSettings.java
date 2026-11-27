package ch.stvbero.platform.driver;

import ch.stvbero.platform.attendance.event.Event;
import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.error.DomainError;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * Settings of the driver plan of a season that coaches and admins change: how many people are expected (DRV-29)
 * and which match days are left out of the driver list (DRV-35). Each change returns the plan.
 */
@ApplicationScoped
public class DriverPlanSettings {

    @Inject
    DriverPlanService plan;

    /** Sets how many people are expected to travel to a match day of the team; {@code null}: everyone (DRV-29). */
    public DriverPlanDto setExpected(CurrentUser user, UUID teamId, UUID seasonId, Integer expected) {
        QuarkusTransaction.requiringNew().run(() -> {
            DriverPlanService.Context c = plan.managing(user, teamId, seasonId);
            c.team().in(c.season()).orElseThrow().expectedTravellers = expected;
        });
        return plan.get(user, teamId, seasonId);
    }

    /** Sets the number for one match day; {@code null}: the number of the team (DRV-29). */
    public DriverPlanDto setMatchDayExpected(CurrentUser user, UUID teamId, UUID seasonId, UUID eventId,
                                             Integer expected) {
        QuarkusTransaction.requiringNew()
                .run(() -> matchDay(user, teamId, seasonId, eventId).expectedTravellers = expected);
        return plan.get(user, teamId, seasonId);
    }

    /**
     * Leaves a match day out of the driver list of the season ({@code true}) or takes it in again (DRV-35). Its cars
     * stay as they are, but count no trips, and «Fair verteilen», «Wer ist dran» and «Alle entfernen» skip it. Also
     * for match days that are over or cancelled.
     */
    public DriverPlanDto setIgnored(CurrentUser user, UUID teamId, UUID seasonId, UUID eventId, boolean ignored) {
        QuarkusTransaction.requiringNew()
                .run(() -> matchDay(user, teamId, seasonId, eventId).ignoredInDriverPlan = ignored);
        return plan.get(user, teamId, seasonId);
    }

    /** A match day of the team in the season, for coaches and admins. */
    private Event matchDay(CurrentUser user, UUID teamId, UUID seasonId, UUID eventId) {
        return plan.matchDays(plan.managing(user, teamId, seasonId)).stream().filter(e -> e.id.equals(eventId))
                .findFirst().orElseThrow(() -> DomainError.notFound("Diesen Spieltag gibt es in diesem Team nicht."));
    }
}
