package ch.stvbero.platform.driver;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * The real routing service (DRV-06, DRV-15). The first one that is configured is used:
 * <ol>
 *     <li>Google Maps, if {@code STV_GOOGLE_MAPS_API_KEY} is set ({@link GoogleRoutesApi}),</li>
 *     <li>OpenRouteService, if {@code STV_ORS_API_KEY} is set ({@link OrsApi}),</li>
 *     <li>otherwise OpenStreetMap without a key: Nominatim and OSRM ({@link OsmApi}).</li>
 * </ol>
 * So a route is calculated without any configuration. While the app simulates, {@link RoutingSwitch} uses the
 * {@link RoutingSimulator} instead.
 */
@ApplicationScoped
@Typed(LiveRouting.class)
public class LiveRouting implements Routing {

    private static final Logger LOG = Logger.getLogger(LiveRouting.class);

    /** Which service calculates the routes. */
    public enum Service {
        GOOGLE, OPEN_ROUTE_SERVICE, OPEN_STREET_MAP
    }

    @ConfigProperty(name = "stv.routing.google.url")
    URI googleUrl;

    @ConfigProperty(name = "stv.routing.google.api-key")
    Optional<String> googleKey;

    @ConfigProperty(name = "stv.routing.url")
    URI orsUrl;

    @ConfigProperty(name = "stv.routing.api-key")
    Optional<String> orsKey;

    @ConfigProperty(name = "stv.routing.osm.geocoder-url")
    URI geocoderUrl;

    @ConfigProperty(name = "stv.routing.osm.router-url")
    URI routerUrl;

    @ConfigProperty(name = "stv.routing.user-agent", defaultValue = "club-platform")
    String userAgent;

    @ConfigProperty(name = "stv.routing.timeout", defaultValue = "PT10S")
    Duration timeout;

    private Service service;
    private java.util.function.BiFunction<Place, Place, Optional<Result>> delegate;

    @PostConstruct
    void start() {
        service = service(googleKey.orElse(""), orsKey.orElse(""));
        delegate = switch (service) {
            case GOOGLE -> new GoogleRoutesApi(googleUrl, googleKey.orElseThrow(), timeout)::route;
            case OPEN_ROUTE_SERVICE -> new OrsApi(orsUrl, orsKey.orElseThrow(), timeout)::route;
            case OPEN_STREET_MAP -> new OsmApi(geocoderUrl, routerUrl, userAgent, timeout)::route;
        };
        LOG.infof("Routes are calculated by %s", service);
    }

    /** Google with a Google key, OpenRouteService with its key, otherwise OpenStreetMap (DRV-15). */
    static Service service(String googleKey, String orsKey) {
        if (!googleKey.isBlank()) {
            return Service.GOOGLE;
        }
        return orsKey.isBlank() ? Service.OPEN_STREET_MAP : Service.OPEN_ROUTE_SERVICE;
    }

    public Service service() {
        return service;
    }

    @Override
    public Optional<Result> route(Place from, Place to) {
        return delegate.apply(from, to);
    }

    @Override
    public RouteProvider provider() {
        return switch (service) {
            case GOOGLE -> RouteProvider.GOOGLE_MAPS;
            case OPEN_ROUTE_SERVICE -> RouteProvider.OPEN_ROUTE_SERVICE;
            case OPEN_STREET_MAP -> RouteProvider.OPEN_STREET_MAP;
        };
    }
}
