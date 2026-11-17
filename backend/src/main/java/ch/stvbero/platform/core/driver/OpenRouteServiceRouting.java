package ch.stvbero.platform.core.driver;

import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * The real routing service, OpenRouteService (profile {@code prod}, DRV-06). In {@code dev} and {@code test} the
 * {@link RoutingSimulator} replaces it; this bean does not exist there. Key from the environment variable
 * {@code STV_ORS_API_KEY}; without a key every calculation counts as unavailable and coaches enter the
 * kilometres by hand (DRV-07).
 */
@ApplicationScoped
@UnlessBuildProperty(name = "stv.test-support", stringValue = "true")
public class OpenRouteServiceRouting implements Routing {

    @ConfigProperty(name = "stv.routing.url")
    URI url;

    @ConfigProperty(name = "stv.routing.api-key")
    Optional<String> key;

    @ConfigProperty(name = "stv.routing.timeout", defaultValue = "PT10S")
    Duration timeout;

    private OrsApi api;

    @PostConstruct
    void start() {
        api = new OrsApi(url, key.orElse(""), timeout);
    }

    @Override
    public Optional<Result> route(Place from, Place to) {
        return api.route(from, to);
    }
}
