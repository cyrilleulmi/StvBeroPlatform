package ch.stvbero.platform.core.swissunihockey;

import ch.stvbero.platform.core.clock.BackendClock;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates swiss unihockey in the profiles {@code dev} and {@code test} (docs/test-data.md, simulated
 * swiss unihockey data). After the reset it delivers exactly match 01 and 02 of event 04 for team ID
 * {@code 900001}. The test support endpoint {@code PUT /api/test/swiss-unihockey/matches} sets other data or an
 * outage.
 */
@ApplicationScoped
@IfBuildProperty(name = "stv.test-support", stringValue = "true")
public class SuSimulator implements SwissUnihockeySource {

    public static final int TEAM_1 = 900001;

    private static final String MAPS_SEEFELD =
            "https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf";

    /** Match 01 and 02 of event 04 (docs/test-data.md). */
    public static final List<SuMatch> SEED = List.of(
            new SuMatch(5000001, LocalDateTime.of(2026, 11, 22, 9, 30).atZone(BackendClock.ZONE).toInstant(),
                    "Team 1", "UHC Musterdorf", "Sporthalle Seefeld", "Musterdorf", MAPS_SEEFELD, null, null, null),
            new SuMatch(5000002, LocalDateTime.of(2026, 11, 22, 11, 45).atZone(BackendClock.ZONE).toInstant(),
                    "Unihockey Beispielstadt", "Team 1", "Sporthalle Seefeld", "Musterdorf", MAPS_SEEFELD, null, null,
                    null));

    private record Data(List<SuMatch> matches, boolean unavailable) {
    }

    private final Map<Integer, Data> teams = new ConcurrentHashMap<>();

    public SuSimulator() {
        reset();
    }

    /** State after the reset: team ID 900001 with match 01 and 02, no matches otherwise. */
    public final void reset() {
        teams.clear();
        teams.put(TEAM_1, new Data(SEED, false));
    }

    /** Sets the data for a team ID; {@code unavailable} simulates an outage (SUH-09). */
    public void set(int suTeamId, List<SuMatch> matches, boolean unavailable) {
        teams.put(suTeamId, new Data(List.copyOf(matches), unavailable));
    }

    /** The season does not matter in the simulator: it delivers whatever was set. */
    @Override
    public List<SuMatch> matches(int suTeamId, int season) {
        Data data = teams.getOrDefault(suTeamId, new Data(List.of(), false));
        if (data.unavailable()) {
            throw new SuUnavailable("swiss unihockey not reachable (simulated)");
        }
        return data.matches();
    }
}
