package ch.stvbero.platform.core.swissunihockey;

import java.util.List;

/**
 * Where the sync gets the matches from (SUH-01). In the {@code prod} profile the swiss unihockey API
 * ({@link SuApiSource}), in {@code dev} and {@code test} the {@link SuSimulator}.
 */
public interface SwissUnihockeySource {

    /**
     * All matches of a team in a season.
     *
     * @param suTeamId team ID at swiss unihockey
     * @param season   start year of the season, e.g. 2026 for 2026/27
     * @throws SuUnavailable if swiss unihockey does not answer or delivers something unexpected (SUH-09)
     */
    List<SuMatch> matches(int suTeamId, int season);

    /**
     * League data of a team in a season (F-17): league, group, table and the matches of the team.
     *
     * @throws SuUnavailable if swiss unihockey does not answer or delivers something unexpected (SUH-09)
     */
    SuLeague league(int suTeamId, int season);
}
