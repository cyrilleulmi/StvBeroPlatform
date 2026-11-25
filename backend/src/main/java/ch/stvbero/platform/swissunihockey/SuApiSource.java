package ch.stvbero.platform.swissunihockey;

import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.attendance.event.EventRules;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The real swiss unihockey API. Address: {@code stv.swiss-unihockey.url} (default the free interface
 * {@code https://wc.swissunihockey.ch}, E-83), club: {@code stv.swiss-unihockey.club-id}. Used in production and
 * in {@code dev} with the club data set; while the app simulates, {@link SuSource} uses the {@link SuSimulator}
 * instead.
 */
@ApplicationScoped
@Typed(SuApiSource.class)
public class SuApiSource implements SwissUnihockeySource {

    @ConfigProperty(name = "stv.swiss-unihockey.url")
    URI url;

    @ConfigProperty(name = "stv.swiss-unihockey.timeout", defaultValue = "PT20S")
    Duration timeout;

    @ConfigProperty(name = "stv.swiss-unihockey.club-id")
    int clubId;

    /** Directory for cached responses (SUH-14); empty: no cache. */
    @ConfigProperty(name = "stv.swiss-unihockey.cache-dir")
    Optional<String> cacheDir;

    @ConfigProperty(name = "stv.swiss-unihockey.cache-ttl", defaultValue = "PT1H")
    Duration cacheTtl;

    @Inject
    BackendClock clock;

    private SuApi api;

    @PostConstruct
    void start() {
        api = new SuApi(url, timeout, cacheDir.filter(dir -> !dir.isBlank()).map(Path::of).orElse(null), cacheTtl);
    }

    @Override
    public List<SuMatch> matches(int suTeamId, int season) {
        return api.matches(suTeamId, season, EventRules.date(clock.now()));
    }

    @Override
    public SuLeague league(int suTeamId, int season) {
        return api.league(suTeamId, season, EventRules.date(clock.now()));
    }

    @Override
    public List<SuClubTeam> clubTeams(int season) {
        return api.clubTeams(clubId, season);
    }
}
