package ch.stvbero.platform.core.auth;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.error.Problem;
import ch.stvbero.platform.core.event.EventGrants;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reads the session cookie and fills the {@link AuthContext}. Resources without {@link Public}
 * answer with 401 when there is no valid session.
 */
@Provider
public class AuthFilter implements ContainerRequestFilter {

    @Context
    ResourceInfo resourceInfo;

    @Inject
    SessionService sessions;

    @Inject
    AuthContext auth;

    @Inject
    EventGrants grants;

    @Override
    public void filter(ContainerRequestContext request) {
        Cookie cookie = request.getCookies().get(SessionService.COOKIE);
        String token = cookie != null ? cookie.getValue() : null;
        sessions.find(token).ifPresent(user -> auth.set(withGrant(request, user), token));

        if (auth.optional().isEmpty() && !isPublic()) {
            request.abortWith(Response.status(401)
                    .type(Problem.MEDIA_TYPE)
                    .entity(DomainError.notSignedIn().toProblem())
                    .build());
        }
    }

    /**
     * Requests on one event (`/api/events/{eventId}/…`) of a person with a special permission for this event run
     * with coach rights (RR-09). Everything else keeps the person's own rights.
     */
    private CurrentUser withGrant(ContainerRequestContext request, CurrentUser user) {
        if (user.allTeams()) {
            return user;
        }
        String eventId = request.getUriInfo().getPathParameters().getFirst("eventId");
        String path = request.getUriInfo().getPath();
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

    private boolean isPublic() {
        Method method = resourceInfo.getResourceMethod();
        Class<?> type = resourceInfo.getResourceClass();
        if (method == null || type == null) {
            return true;
        }
        return method.isAnnotationPresent(Public.class) || type.isAnnotationPresent(Public.class);
    }
}
