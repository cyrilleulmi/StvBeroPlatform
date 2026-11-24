package ch.stvbero.platform.swissunihockey.league;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * League data of a team (F-17), from the cache, never live from swiss unihockey (criterion 6).
 *
 * @param linked   whether the team is linked with swiss unihockey; otherwise all other fields are empty
 * @param loadedAt last successful load («Stand»), {@code null} if never loaded in this season
 * @param failedAt last failed attempt after {@code loadedAt} (SUH-09), {@code null} if the last attempt worked
 * @param upcoming «Kommende Spiele», the earliest first
 * @param results  «Letzte Resultate», the latest first, at most five
 */
@Schema(name = "League")
public record LeagueDto(UUID teamId, String teamName, boolean linked, String league, String group,
                        OffsetDateTime loadedAt, OffsetDateTime failedAt, List<Row> table,
                        List<LeagueMatch> upcoming, List<LeagueMatch> results) {

    /** A row of the table. {@code own} marks the own team (criterion 2). */
    @Schema(name = "LeagueRow")
    public record Row(int rank, String team, int matches, int wins, int draws, int losses, int goalsFor,
                      int goalsAgainst, int points, boolean own) {
    }

    /**
     * A match of the team. Home and away team as at swiss unihockey, {@code opponent} and {@code home} from the
     * view of the own team. {@code eventId} is the match day in the app, if the match belongs to one (criterion 3).
     *
     * @param result home:away, e.g. «5:3», or {@code null}
     */
    @Schema(name = "LeagueMatch")
    public record LeagueMatch(int suMatchId, OffsetDateTime startTime, String homeTeam, String awayTeam,
                              String opponent, boolean home, String hall, String location, String result,
                              UUID eventId) {
    }
}
