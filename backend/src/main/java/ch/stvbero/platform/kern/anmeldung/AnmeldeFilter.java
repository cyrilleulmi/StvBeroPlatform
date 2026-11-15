package ch.stvbero.platform.kern.anmeldung;

import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.fehler.Fehler;
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
public class AnmeldeFilter implements ContainerRequestFilter {

    @Context
    ResourceInfo resourceInfo;

    @Inject
    SitzungService sitzungen;

    @Inject
    Anmeldung anmeldung;

    @Override
    public void filter(ContainerRequestContext anfrage) {
        Cookie cookie = anfrage.getCookies().get(SitzungService.COOKIE);
        String token = cookie != null ? cookie.getValue() : null;
        sitzungen.finden(token).ifPresent(a -> anmeldung.setzen(a, token));

        if (anmeldung.optional().isEmpty() && !istOeffentlich()) {
            anfrage.abortWith(Response.status(401)
                    .type(Fehler.MEDIENTYP)
                    .entity(FachFehler.nichtAngemeldet().alsFehler())
                    .build());
        }
    }

    private boolean istOeffentlich() {
        Method methode = resourceInfo.getResourceMethod();
        Class<?> klasse = resourceInfo.getResourceClass();
        if (methode == null || klasse == null) {
            return true;
        }
        return methode.isAnnotationPresent(Oeffentlich.class) || klasse.isAnnotationPresent(Oeffentlich.class);
    }
}
