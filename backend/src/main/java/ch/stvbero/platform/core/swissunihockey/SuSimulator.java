package ch.stvbero.platform.core.swissunihockey;

import ch.stvbero.platform.core.clock.BackendClock;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simuliert swiss unihockey in den Profilen {@code dev} und {@code test} (docs/testdaten.md, «Simulierte
 * swiss-unihockey-Daten»). Nach dem Reset liefert er für Team-ID {@code 900001} genau Spiel 01 und 02 von
 * Termin 04. Die Test-Hilfe {@code PUT /api/test/swiss-unihockey/spiele} gibt andere Daten oder einen Ausfall vor.
 */
@ApplicationScoped
@IfBuildProperty(name = "stv.test-support", stringValue = "true")
public class SuSimulator implements SwissUnihockeySource {

    public static final int TEAM_1 = 900001;

    private static final String MAPS_SEEFELD =
            "https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf";

    /** Spiel 01 und 02 von Termin 04 (docs/testdaten.md). */
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

    /** Zustand nach Reset: Team-ID 900001 mit Spiel 01 und 02, sonst keine Spiele. */
    public final void reset() {
        teams.clear();
        teams.put(TEAM_1, new Data(SEED, false));
    }

    /** Daten für eine Team-ID vorgeben, mit {@code nichtErreichbar} einen Ausfall (SUH-09). */
    public void set(int suTeamId, List<SuMatch> matches, boolean unavailable) {
        teams.put(suTeamId, new Data(List.copyOf(matches), unavailable));
    }

    /** Die Saison spielt im Simulator keine Rolle: Er liefert, was vorgegeben ist. */
    @Override
    public List<SuMatch> matches(int suTeamId, int season) {
        Data d = teams.getOrDefault(suTeamId, new Data(List.of(), false));
        if (d.unavailable()) {
            throw new SuUnavailable("swiss unihockey nicht erreichbar (simuliert)");
        }
        return d.matches();
    }
}
