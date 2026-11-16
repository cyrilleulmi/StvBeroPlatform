package ch.stvbero.platform.core.swissunihockey;

import java.time.Instant;
import java.util.Objects;

/**
 * A match as swiss unihockey (or the simulator in tests) delivers it for a team. Neutral: which side is "us"
 * is only decided by the sync ({@link SyncRules#ownTeam}).
 *
 * @param hall     name of the hall, e.g. «Sporthalle Seefeld»
 * @param location town of the hall, e.g. «Musterdorf», or {@code null}
 * @param mapsUrl  Google Maps link, if delivered (SUH-07)
 * @param result   home:away, e.g. «5:3», or {@code null} as long as there is none (SUH-08)
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
