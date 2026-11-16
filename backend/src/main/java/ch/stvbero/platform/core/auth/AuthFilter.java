package ch.stvbero.platform.core.auth;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.error.Problem;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.lang.reflect.Method;

/**
 * Liest das Sitzungs-Cookie und setzt die {@link Anmeldung}. Ressourcen ohne {@link Oeffentlich}
 * antworten ohne gültige Sitzung mit 401.
 */
@Provider
public class AuthFilter implements ContainerRequestFilter {

    @Context
    ResourceInfo resourceInfo;

    @Inject
    SessionService sessions;

    @Inject
    AuthContext auth;

    @Override
    public void filter(ContainerRequestContext request) {
        Cookie cookie = request.getCookies().get(SessionService.COOKIE);
        String token = cookie != null ? cookie.getValue() : null;
        sessions.find(token).ifPresent(a -> auth.set(a, token));

        if (auth.optional().isEmpty() && !isPublic()) {
            request.abortWith(Response.status(401)
                    .type(Problem.MEDIA_TYPE)
                    .entity(DomainError.notSignedIn().toProblem())
                    .build());
        }
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
