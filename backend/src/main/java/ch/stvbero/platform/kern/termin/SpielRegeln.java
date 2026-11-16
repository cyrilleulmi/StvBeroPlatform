package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.fehler.FachFehler;

import java.time.Instant;
import java.util.Objects;

/** Fachregeln zu Spielen eines Spieltags (AUF-14, AUF-15, SUH-06). Ohne Datenbank. */
public final class SpielRegeln {

    /** Resultat Heim:Gast, z.B. «5:3». */
    public static final String RESULTAT_MUSTER = "^\\d{1,2}:\\d{1,2}$";
    public static final String RESULTAT_TEXT = "Resultat bitte als Heim:Gast, z.B. 5:3.";
    public static final String AM_SPIELTAG = "Das Spiel muss am Tag des Spieltags stattfinden.";
    public static final String IMPORTIERT = "Gegner, Anspielzeit und Heim/Auswärts kommen von swiss unihockey und lassen sich nicht ändern.";
    public static final String NICHT_LOESCHBAR = "Spiele von swiss unihockey lassen sich nur löschen, wenn swiss unihockey sie nicht mehr liefert.";
    public static final String ABGESAGT = "Der Spieltag ist abgesagt. Spiele lassen sich nicht mehr ändern.";

    private SpielRegeln() {
    }

    /** Ein Spieltag findet an einem Tag statt (AUF-01): Spiele liegen am Datum seines Beginns. */
    public static void pruefeAmSpieltag(Instant anspielzeit, Instant beginnSpieltag) {
        if (!TerminRegeln.datum(anspielzeit).equals(TerminRegeln.datum(beginnSpieltag))) {
            throw FachFehler.ungueltigesFeld("anspielzeit", AM_SPIELTAG);
        }
    }

    /**
     * Bei Spielen aus swiss unihockey sind Gegner, Anspielzeit und Heim/Auswärts schreibgeschützt (409). Das
     * Resultat lässt sich erfassen (AUF-15); der Abgleich kann es später überschreiben.
     */
    public static void pruefeAenderbar(Quelle quelle, String gegnerAlt, Instant anspielzeitAlt, boolean heimAlt,
                                       String gegnerNeu, Instant anspielzeitNeu, boolean heimNeu) {
        if (quelle == Quelle.SWISS_UNIHOCKEY && (!Objects.equals(gegnerAlt, gegnerNeu)
                || !anspielzeitAlt.equals(anspielzeitNeu) || heimAlt != heimNeu)) {
            throw FachFehler.konflikt("Importiertes Spiel", IMPORTIERT, "SUH-06");
        }
    }

    /** Von Hand erfasste Spiele lassen sich löschen, importierte erst, wenn SU sie nicht mehr liefert (409). */
    public static void pruefeLoeschbar(Quelle quelle, boolean beiSuNichtMehrVorhanden) {
        if (quelle == Quelle.SWISS_UNIHOCKEY && !beiSuNichtMehrVorhanden) {
            throw FachFehler.konflikt("Importiertes Spiel", NICHT_LOESCHBAR, "SUH-06");
        }
    }

    /** Ein abgesagter Spieltag nimmt keine Änderungen an Spielen an (409, TER-06). */
    public static void pruefeNichtAbgesagt(TerminStatus status) {
        if (status == TerminStatus.ABGESAGT) {
            throw FachFehler.konflikt("Termin abgesagt", ABGESAGT, "TER-06");
        }
    }
}
