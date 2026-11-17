package ch.stvbero.platform.core.swissunihockey;

import java.util.List;

/**
 * League data of a team (F-17): league and group name, table and the matches of the team in the season.
 *
 * @param league e.g. «Herren Kleinfeld 3. Liga», or {@code null}
 * @param group  e.g. «Gruppe 12», or {@code null}
 */
public record SuLeague(String league, String group, List<SuStanding> table, List<SuMatch> matches) {

    public SuLeague {
        table = List.copyOf(table);
        matches = List.copyOf(matches);
    }
}
