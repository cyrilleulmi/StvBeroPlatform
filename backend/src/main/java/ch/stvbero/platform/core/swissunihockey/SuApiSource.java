package ch.stvbero.platform.core.swissunihockey;

import ch.stvbero.platform.core.event.EventRules;
import ch.stvbero.platform.core.clock.BackendClock;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * The real swiss unihockey API (profile {@code prod}). In {@code dev} and {@code test} the {@link SuSimulator}
 * replaces it; this bean does not exist there. Address: {@code stv.swiss-unihockey.url}.
 */
@ApplicationScoped
@UnlessBuildProperty(name = "stv.test-support", stringValue = "true")
public class SuApiSource implements SwissUnihockeySource {

    @ConfigProperty(name = "stv.swiss-unihockey.url")
    URI url;

    @ConfigProperty(name = "stv.swiss-unihockey.timeout", defaultValue = "PT20S")
    Duration timeout;

    @Inject
    BackendClock clock;

    private SuApi api;

    @PostConstruct
    void start() {
        api = new SuApi(url, timeout);
    }

    @Override
    public List<SuMatch> matches(int suTeamId, int season) {
        return api.matches(suTeamId, season, EventRules.date(clock.now()));
    }
}
