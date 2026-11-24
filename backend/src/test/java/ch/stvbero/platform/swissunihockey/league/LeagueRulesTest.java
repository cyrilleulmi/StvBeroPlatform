package ch.stvbero.platform.swissunihockey.league;

import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.swissunihockey.SuMatch;
import ch.stvbero.platform.swissunihockey.SuStanding;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** F-17: own team in the table, upcoming matches and latest results. */
class LeagueRulesTest {

    private static Instant at(int month, int day, int hour) {
        return LocalDateTime.of(2026, month, day, hour, 0).atZone(BackendClock.ZONE).toInstant();
    }

    private static SuMatch match(int id, Instant start, String home, String away, String result) {
        return new SuMatch(id, start, home, away, "Halle", null, null, null, null, result);
    }

    private static SuStanding row(int rank, Integer id, String team) {
        return new SuStanding(rank, id, team, 1, 1, 0, 0, 3, 1, 2);
    }

    @Test
    void criterion2OwnRowByTeamId() {
        List<SuStanding> table = List.of(row(1, 11, "Team 1"), row(2, 900001, "STV Beromünster I"));
        assertEquals(1, LeagueRules.ownRow(table, 900001, List.of(), "Team 1"),
                "The team ID wins over an equal name");
    }

    @Test
    void criterion2OwnRowByNameInTheMatches() {
        List<SuStanding> table = List.of(row(1, null, "UHC A"), row(2, null, "STV Beromünster  I"));
        List<SuMatch> matches = List.of(match(1, at(10, 1, 10), "STV Beromünster I", "UHC A", "1:0"),
                match(2, at(10, 8, 10), "UHC B", "STV Beromünster I", null));
        assertEquals(1, LeagueRules.ownRow(table, 900001, matches, "Herren 1"));
    }

    @Test
    void criterion2OwnRowByAppNameOrNone() {
        List<SuStanding> table = List.of(row(1, null, "UHC A"), row(2, null, "team 1"));
        assertEquals(1, LeagueRules.ownRow(table, 900001, List.of(), "Team 1"));
        assertEquals(-1, LeagueRules.ownRow(table, 900001, List.of(), "Team 9"));
    }

    @Test
    void criterion3UpcomingFromNowEarliestFirstAtMostFive() {
        Instant now = at(11, 16, 10);
        List<SuMatch> matches = List.of(
                match(1, at(11, 15, 10), "A", "B", null),
                match(2, at(11, 16, 9), "A", "B", null),   // still running (75 minutes)
                match(3, at(11, 16, 8), "A", "B", null),   // over
                match(4, at(12, 1, 10), "A", "B", null),
                match(5, at(11, 20, 10), "A", "B", null),
                match(6, at(11, 21, 10), "A", "B", null),
                match(7, at(11, 22, 10), "A", "B", null),
                match(8, at(11, 23, 10), "A", "B", null),
                match(9, at(11, 17, 10), "A", "B", "2:1"));
        assertEquals(List.of(2, 5, 6, 7, 8),
                LeagueRules.upcoming(matches, now).stream().map(SuMatch::suMatchId).toList());
    }

    @Test
    void criterion4LastFiveResultsLatestFirst() {
        List<SuMatch> matches = List.of(
                match(1, at(9, 1, 10), "A", "B", "1:0"),
                match(2, at(9, 8, 10), "A", "B", "1:1"),
                match(3, at(9, 15, 10), "A", "B", "2:0"),
                match(4, at(9, 22, 10), "A", "B", null),
                match(5, at(9, 29, 10), "A", "B", "0:3"),
                match(6, at(10, 6, 10), "A", "B", "4:4"),
                match(7, at(10, 13, 10), "A", "B", "5:2"));
        assertEquals(List.of(7, 6, 5, 3, 2),
                LeagueRules.results(matches).stream().map(SuMatch::suMatchId).toList());
    }
}
