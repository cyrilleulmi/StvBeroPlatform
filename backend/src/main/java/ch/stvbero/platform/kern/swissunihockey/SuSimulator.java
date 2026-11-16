package ch.stvbero.platform.kern.swissunihockey;

import ch.stvbero.platform.kern.uhr.Uhr;
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
@IfBuildProperty(name = "stv.testhilfen", stringValue = "true")
public class SuSimulator implements SwissUnihockeyQuelle {

    public static final int TEAM_1 = 900001;

    private static final String MAPS_SEEFELD =
            "https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf";

    /** Spiel 01 und 02 von Termin 04 (docs/testdaten.md). */
    public static final List<SuSpiel> SEED = List.of(
            new SuSpiel(5000001, LocalDateTime.of(2026, 11, 22, 9, 30).atZone(Uhr.ZONE).toInstant(),
                    "Team 1", "UHC Musterdorf", "Sporthalle Seefeld", "Musterdorf", MAPS_SEEFELD, null, null, null),
            new SuSpiel(5000002, LocalDateTime.of(2026, 11, 22, 11, 45).atZone(Uhr.ZONE).toInstant(),
                    "Unihockey Beispielstadt", "Team 1", "Sporthalle Seefeld", "Musterdorf", MAPS_SEEFELD, null, null,
                    null));

    private record Daten(List<SuSpiel> spiele, boolean nichtErreichbar) {
    }

    private final Map<Integer, Daten> teams = new ConcurrentHashMap<>();

    public SuSimulator() {
        zuruecksetzen();
    }

    /** Zustand nach Reset: Team-ID 900001 mit Spiel 01 und 02, sonst keine Spiele. */
    public final void zuruecksetzen() {
        teams.clear();
        teams.put(TEAM_1, new Daten(SEED, false));
    }

    /** Daten für eine Team-ID vorgeben, mit {@code nichtErreichbar} einen Ausfall (SUH-09). */
    public void setzen(int suTeamId, List<SuSpiel> spiele, boolean nichtErreichbar) {
        teams.put(suTeamId, new Daten(List.copyOf(spiele), nichtErreichbar));
    }

    /** Die Saison spielt im Simulator keine Rolle: Er liefert, was vorgegeben ist. */
    @Override
    public List<SuSpiel> spiele(int suTeamId, int saison) {
        Daten d = teams.getOrDefault(suTeamId, new Daten(List.of(), false));
        if (d.nichtErreichbar()) {
            throw new SuNichtErreichbar("swiss unihockey nicht erreichbar (simuliert)");
        }
        return d.spiele();
    }
}
