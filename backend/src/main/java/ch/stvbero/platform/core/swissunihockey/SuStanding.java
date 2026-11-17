package ch.stvbero.platform.core.swissunihockey;

import java.util.Objects;

/**
 * A row of the league table as swiss unihockey (or the simulator) delivers it (F-17).
 *
 * @param suTeamId team ID at swiss unihockey, if delivered; decides which row is the own team
 */
public record SuStanding(int rank, Integer suTeamId, String team, int matches, int wins, int draws, int losses,
                         int goalsFor, int goalsAgainst, int points) {

    public SuStanding {
        Objects.requireNonNull(team, "team");
    }
}
