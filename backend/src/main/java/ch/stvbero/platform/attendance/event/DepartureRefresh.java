package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.auth.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Automatic departure (TML-09): a match day without a meeting time entered by hand gets the suggested departure
 * before it is shown. The route is calculated outside of the transaction of the request, so this is not part of
 * the {@link EventService}.
 */
@ApplicationScoped
public class DepartureRefresh {

    @Inject
    Instance<MatchDayTravel> travel;

    @Inject
    EventService events;

    @Inject
    EntityManager em;

    public EventDto apply(CurrentUser user, EventDto event) {
        if (event.type() != EventType.MATCH_DAY || event.status() != EventStatus.PLANNED
                || event.meetingPoint() == null || !event.variant().autoDeparture() || !travel.isResolvable()) {
            return event;
        }
        travel.get().autoDeparture(event.id());
        // The event of this request is cached; read it again as it is stored now
        em.clear();
        return events.get(user, event.id());
    }
}
