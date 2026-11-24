package ch.stvbero.platform.core.web;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Serves the Angular app for browser routes (E-91). In the container the frontend build lies in
 * {@code META-INF/resources}, so {@code /} delivers {@code index.html}. A deep link such as {@code /termine/…}
 * has no file of its own: it is rerouted to {@code /}, and the Angular router shows the page.
 *
 * <p>Not rerouted: the API ({@code /api}), the Quarkus endpoints ({@code /q}) and every path with a dot
 * (files such as bundles, icons, {@code sw.js}), so a missing file stays a 404.
 * Locally the Angular dev server serves the frontend; this route then only answers on port 8080.
 */
@ApplicationScoped
public class SpaRouting {

    static final String BROWSER_ROUTE = "^/(?!api(?:/|$)|q(?:/|$))[^.]+$";

    void install(@Observes Router router) {
        router.getWithRegex(BROWSER_ROUTE).handler(context -> context.reroute("/"));
    }
}
