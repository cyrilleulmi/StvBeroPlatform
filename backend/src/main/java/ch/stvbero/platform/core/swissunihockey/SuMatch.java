package ch.stvbero.platform.core.swissunihockey;

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
public record SuMatch(int suMatchId, Instant startTime, String homeTeam, String awayTeam, String hall, String location,
                      String mapsUrl, Double latitude, Double longitude, String result) {

    public SuMatch {
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(homeTeam, "homeTeam");
        Objects.requireNonNull(awayTeam, "awayTeam");
        Objects.requireNonNull(hall, "hall");
    }
}
