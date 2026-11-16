package ch.stvbero.platform.kern.swissunihockey;

import java.util.List;

/**
 * Woher der Abgleich die Spiele bezieht (SUH-01). Im Profil {@code prod} die Schnittstelle von swiss unihockey
 * ({@link SuApiQuelle}), in {@code dev} und {@code test} der {@link SuSimulator}.
 */
public interface SwissUnihockeyQuelle {

    /**
     * Alle Spiele eines Teams in einer Saison.
     *
     * @param suTeamId Team-ID bei swiss unihockey
     * @param saison   Startjahr der Saison, z.B. 2026 für 2026/27
     * @throws SuNichtErreichbar wenn swiss unihockey nicht antwortet oder Unerwartetes liefert (SUH-09)
     */
    List<SuSpiel> spiele(int suTeamId, int saison);
}
