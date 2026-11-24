package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.auth.RequestGrant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.UriInfo;

import java.util.UUID;

/**
 * Requests on one event ({@code /api/events/{eventId}/…}) of a person with a special permission for this event run
 * with coach rights (RR-09). Asked by the auth filter of the core on every request.
 */
@ApplicationScoped
public class EventRequestGrant implements RequestGrant {

    @Inject
    EventGrants grants;

    @Override
    public CurrentUser apply(UriInfo uri, CurrentUser user) {
        String eventId = uri.getPathParameters().getFirst("eventId");
        String path = uri.getPath();
        if (eventId == null || !(path.startsWith("/") ? path.substring(1) : path).startsWith("api/events/")) {
            return user;
        }
        UUID id;
        try {
            id = UUID.fromString(eventId);
        } catch (IllegalArgumentException e) {
            return user;
        }
        return grants.has(id, user.personId()) ? user.withEventGrant() : user;
    }
}
