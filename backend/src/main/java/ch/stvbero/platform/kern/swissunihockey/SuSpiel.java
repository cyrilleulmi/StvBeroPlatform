package ch.stvbero.platform.kern.swissunihockey;

import java.time.Instant;
import java.util.Objects;

/**
 * Ein Spiel, wie es swiss unihockey (oder im Test der Simulator) für ein Team liefert. Fachlich neutral: Wer
 * «wir» sind, bestimmt erst der Abgleich ({@link AbgleichRegeln#eigenesTeam}).
 *
 * @param halle   Name der Halle, z.B. «Sporthalle Seefeld»
 * @param ort     Ortschaft der Halle, z.B. «Musterdorf», oder {@code null}
 * @param mapsUrl Google-Maps-Link, falls geliefert (SUH-07)
 * @param resultat Heim:Gast, z.B. «5:3», oder {@code null}, solange keines vorliegt (SUH-08)
 */
public record SuSpiel(int suSpielId, Instant anspielzeit, String heimteam, String gastteam, String halle, String ort,
                      String mapsUrl, Double breite, Double laenge, String resultat) {

    public SuSpiel {
        Objects.requireNonNull(anspielzeit, "anspielzeit");
        Objects.requireNonNull(heimteam, "heimteam");
        Objects.requireNonNull(gastteam, "gastteam");
        Objects.requireNonNull(halle, "halle");
    }
}
