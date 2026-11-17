package ch.stvbero.platform.core.league;

import ch.stvbero.platform.core.swissunihockey.SuMatch;
import ch.stvbero.platform.core.swissunihockey.SuStanding;
import ch.stvbero.platform.core.swissunihockey.SyncRules;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Rules of the league page (F-17). Without a database, so that unit tests can check them. */
public final class LeagueRules {

    /** «Letzte Resultate»: the last five matches with a result (criterion 4). */
    public static final int RESULTS = 5;

    /** «Kommende Spiele»: the next five matches (assumption F-17). */
    public static final int UPCOMING = 5;

    private LeagueRules() {
    }

    /**
     * Index of the own team in the table, or -1. The team ID at swiss unihockey decides; if the table has no team
     * IDs, the name the team has in the matches (as for the sync, {@link SyncRules#ownTeam}), otherwise the name in
     * the app.
     */
    public static int ownRow(List<SuStanding> table, int suTeamId, List<SuMatch> matches, String teamName) {
        for (int i = 0; i < table.size(); i++) {
            if (Integer.valueOf(suTeamId).equals(table.get(i).suTeamId())) {
                return i;
            }
        }
        String own = SyncRules.ownTeam(matches, teamName);
        for (String name : own == null ? List.of(norm(teamName)) : List.of(own, norm(teamName))) {
            for (int i = 0; i < table.size(); i++) {
                if (table.get(i).suTeamId() == null && norm(table.get(i).team()).equals(name)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Matches without a result that have not ended yet, the earliest first, at most {@link #UPCOMING}. A match counts
     * as running until {@link SyncRules#MATCH_DURATION} after its start.
     */
    public static List<SuMatch> upcoming(List<SuMatch> matches, Instant now) {
        return matches.stream()
                .filter(m -> m.result() == null && m.startTime().plus(SyncRules.MATCH_DURATION).isAfter(now))
                .sorted(Comparator.comparing(SuMatch::startTime).thenComparing(SuMatch::suMatchId))
                .limit(UPCOMING)
                .toList();
    }

    /** Matches with a result, the latest first, at most {@link #RESULTS} (criterion 4). */
    public static List<SuMatch> results(List<SuMatch> matches) {
        return matches.stream()
                .filter(m -> m.result() != null)
                .sorted(Comparator.comparing(SuMatch::startTime).reversed()
                        .thenComparing(Comparator.comparing(SuMatch::suMatchId).reversed()))
                .limit(RESULTS)
                .toList();
    }

    static String norm(String name) {
        return name == null ? "" : name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
