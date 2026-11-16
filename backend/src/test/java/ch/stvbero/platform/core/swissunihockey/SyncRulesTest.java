package ch.stvbero.platform.core.swissunihockey;

import ch.stvbero.platform.core.clock.BackendClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fachregeln des Abgleichs (SUH-04, SUH-05, SUH-07, BEN-06), ohne Datenbank. */
class SyncRulesTest {

    private static Instant at(int month, int day, int hour, int minute) {
        return LocalDateTime.of(2026, month, day, hour, minute).atZone(BackendClock.ZONE).toInstant();
    }

    private static SuMatch match(int id, Instant time, String home, String away, String hall) {
        return new SuMatch(id, time, home, away, hall, null, null, null, null, null);
    }

    @Test
    void ownTeamComesInEveryMatchBefore() {
        List<SuMatch> matches = List.of(
                match(1, at(11, 22, 9, 30), "STV Beromünster I", "UHC Musterdorf", "Halle"),
                match(2, at(11, 22, 11, 45), "Unihockey Beispielstadt", "STV Beromünster I", "Halle"));
        assertEquals("stv beromünster i", SyncRules.ownTeam(matches, "Team 1"));

        SyncRules.Side home = SyncRules.side(matches.get(0), "stv beromünster i");
        assertEquals("UHC Musterdorf", home.opponent());
        assertTrue(home.home());
        SyncRules.Side away = SyncRules.side(matches.get(1), "stv beromünster i");
        assertEquals("Unihockey Beispielstadt", away.opponent());
        assertFalse(away.home());
    }

    @Test
    void atOnlyAMatchDecidesTheNameInTheApp() {
        List<SuMatch> a = List.of(match(1, at(11, 22, 11, 45), "Unihockey Beispielstadt", "Team 1", "Halle"));
        assertEquals("team 1", SyncRules.ownTeam(a, "Team 1"));
        assertFalse(SyncRules.side(a.get(0), "team 1").home());
        assertNull(SyncRules.ownTeam(List.of(), "Team 1"));
    }

    @Test
    void suh04SameDayAndSameHallResultAMatchDay() {
        Map<SyncRules.Key, List<SuMatch>> groups = SyncRules.groupByDayAndHall(List.of(
                match(3, at(11, 22, 14, 0), "A", "B", "Turnhalle Weid"),
                match(2, at(11, 22, 11, 45), "A", "C", "Sporthalle  Seefeld"),
                match(1, at(11, 22, 9, 30), "A", "D", "sporthalle seefeld"),
                match(4, at(11, 29, 9, 30), "A", "D", "Sporthalle Seefeld")));

        assertEquals(3, groups.size());
        List<List<SuMatch>> values = List.copyOf(groups.values());
        assertEquals(List.of(1, 2), values.get(0).stream().map(SuMatch::suMatchId).toList(),
                "Gleiche Halle trotz Gross-/Kleinschreibung und Leerzeichen, nach Anspielzeit");
        assertEquals(List.of(3), values.get(1).stream().map(SuMatch::suMatchId).toList(), "Andere Halle");
        assertEquals(List.of(4), values.get(2).stream().map(SuMatch::suMatchId).toList(), "Anderer Tag");
        assertEquals(new SyncRules.Key(LocalDate.of(2026, 11, 22), "sporthalle seefeld"),
                groups.keySet().iterator().next());
    }

    @Test
    void periodFromFirstStartTimeToLastWithMatchDuration() {
        SyncRules.Period z = SyncRules.period(List.of(at(11, 22, 11, 45), at(11, 22, 9, 30)));
        assertEquals(at(11, 22, 9, 30), z.start());
        assertEquals(at(11, 22, 13, 0), z.end(), "Wie Termin 04 im Seed");
    }

    @Test
    void onlyMatchesTheSeason() {
        assertTrue(SyncRules.inSeason(at(11, 22, 9, 30), LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30)));
        assertFalse(SyncRules.inSeason(at(6, 20, 9, 30), LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30)));
    }

    @Test
    void suh07MapsLink() {
        SuMatch withLink = new SuMatch(1, at(11, 22, 9, 30), "A", "B", "Halle", "Ort", "https://maps.example/x", 47.0,
                8.0, null);
        assertEquals("https://maps.example/x", SyncRules.mapsUrl(withLink));
        SuMatch withCoordinates = new SuMatch(1, at(11, 22, 9, 30), "A", "B", "Halle", "Ort", null, 47.2141, 8.1662,
                null);
        assertEquals("https://www.google.com/maps/search/?api=1&query=47.2141%2C8.1662",
                SyncRules.mapsUrl(withCoordinates));
        SuMatch without = new SuMatch(1, at(11, 22, 9, 30), "A", "B", "Sporthalle Seefeld", "Musterdorf", null, null,
                null, null);
        assertEquals("https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf",
                SyncRules.mapsUrl(without));
    }

    @Test
    void suh05SignificantChanges() {
        SyncRules.MatchState old = new SyncRules.MatchState("Unihockey Beispielstadt", at(11, 22, 11, 45), false,
                "Sporthalle Seefeld");
        assertFalse(SyncRules.significantlyChanged(old, old));
        assertFalse(SyncRules.significantlyChanged(old, new SyncRules.MatchState("Unihockey Beispielstadt",
                at(11, 22, 11, 45), false, "sporthalle  seefeld")), "Schreibweise der Halle zählt nicht");

        SyncRules.MatchState later = new SyncRules.MatchState("Unihockey Beispielstadt", at(11, 22, 12, 15), false,
                "Sporthalle Seefeld");
        assertTrue(SyncRules.significantlyChanged(old, later));
        assertEquals("Spiel gegen Unihockey Beispielstadt neu um 12:15", SyncRules.change(old, later));

        SyncRules.MatchState otherDay = new SyncRules.MatchState("UHC Drei", at(11, 21, 16, 0), true,
                "Turnhalle Weid");
        assertEquals("Spiel um 11:45 neu am Sa 21.11. 16:00, neu in Turnhalle Weid, neu gegen UHC Drei, "
                + "neu als Heimspiel", SyncRules.change(old, otherDay));
    }

    @Test
    void ben06Text() {
        assertEquals("Spieltag So 22.11. wurde geändert: Spiel gegen Unihockey Beispielstadt neu um 12:15.",
                SyncRules.matchChangedText("Spieltag", at(11, 22, 9, 30),
                        List.of("Spiel gegen Unihockey Beispielstadt neu um 12:15")));
    }
}
