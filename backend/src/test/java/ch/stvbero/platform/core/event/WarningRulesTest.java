package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.line.LineRules;
import ch.stvbero.platform.core.team.GroupType;
import ch.stvbero.platform.core.team.WarningMinimums;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Domain rules for the warnings of events and call-ups (F-16). */
class WarningRulesTest {

    static final OverviewDto.TeamName TEAM = new OverviewDto.TeamName(UUID.randomUUID(), "Team 1");
    static final LineRules.GroupInfo LINE_1 = new LineRules.GroupInfo(UUID.randomUUID(), GroupType.LINE, "Linie 1", 1);
    static final LineRules.GroupInfo LINE_2 = new LineRules.GroupInfo(UUID.randomUUID(), GroupType.LINE, "Linie 2", 2);
    static final LineRules.GroupInfo GOALIES = new LineRules.GroupInfo(UUID.randomUUID(), GroupType.GOALIES, "Goalies", null);
    static final Instant NOW = Instant.parse("2026-11-16T05:00:00Z");

    private static WarningRules.Player player(LineRules.GroupInfo group, boolean counted) {
        return new WarningRules.Player(UUID.randomUUID(), group == null ? List.of() : List.of(group), counted);
    }

    /** {@code n} counted players in the group. */
    private static List<WarningRules.Player> players(LineRules.GroupInfo group, int n) {
        List<WarningRules.Player> players = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            players.add(player(group, true));
        }
        return players;
    }

    private static List<WarningRules.Player> full() {
        List<WarningRules.Player> all = new ArrayList<>();
        all.addAll(players(LINE_1, 5));
        all.addAll(players(LINE_2, 5));
        all.addAll(players(GOALIES, 1));
        return all;
    }

    @Test
    void criterion1NoGoalieAttending() {
        List<WarningRules.Player> all = new ArrayList<>(players(LINE_1, 5));
        all.addAll(players(LINE_2, 5));
        all.add(player(GOALIES, false));
        List<WarningDto> warnings = WarningRules.warnings(EventType.MATCH_DAY, TEAM, WarningMinimums.DEFAULTS, all);
        assertEquals(1, warnings.size());
        WarningDto w = warnings.get(0);
        assertEquals(WarningType.GOALIES, w.type());
        assertEquals(0, w.count());
        assertEquals(1, w.minimum());
        assertEquals(TEAM, w.team());
    }

    @Test
    void criterion1FewerFieldPlayersThanTheMinimumIncludingNoLine() {
        List<WarningRules.Player> all = new ArrayList<>(players(LINE_1, 3));
        all.addAll(players(LINE_2, 3));
        all.addAll(players(null, 3));
        all.addAll(players(GOALIES, 2));
        // Match day: 9 field players (goalies do not count) < 10
        List<WarningDto> warnings = WarningRules.warnings(EventType.MATCH_DAY, TEAM, WarningMinimums.DEFAULTS, all);
        assertEquals(1, warnings.size());
        assertEquals(WarningType.FIELD_PLAYERS, warnings.get(0).type());
        assertEquals(9, warnings.get(0).count());
        assertEquals(10, warnings.get(0).minimum());
        // Practice: minimum 6, so no warning
        assertTrue(WarningRules.warnings(EventType.PRACTICE, TEAM, WarningMinimums.DEFAULTS, all).isEmpty());
    }

    @Test
    void criterion1LineWithFewerPlayersThanTheLineMinimum() {
        List<WarningRules.Player> all = new ArrayList<>(players(LINE_1, 4));
        all.addAll(players(LINE_2, 2));
        all.add(player(LINE_2, false));
        all.addAll(players(GOALIES, 1));
        WarningMinimums minimums = new WarningMinimums(1, 0, 0, 3);
        List<WarningDto> warnings = WarningRules.warnings(EventType.PRACTICE, TEAM, minimums, all);
        assertEquals(1, warnings.size());
        WarningDto w = warnings.get(0);
        assertEquals(WarningType.LINE, w.type());
        assertEquals(LINE_2.id(), w.groupId());
        assertEquals("Linie 2", w.groupName());
        assertEquals(2, w.count());
        assertEquals(3, w.minimum());
    }

    @Test
    void lineNobodyComesToIsNoLineWarning() {
        List<WarningRules.Player> all = new ArrayList<>(players(LINE_1, 6));
        all.add(player(LINE_2, false));
        all.addAll(players(GOALIES, 1));
        assertTrue(WarningRules.warnings(EventType.PRACTICE, TEAM, WarningMinimums.DEFAULTS, all).isEmpty());
    }

    @Test
    void orderGoaliesFieldPlayersThenLinesByDisplayOrder() {
        List<WarningRules.Player> all = new ArrayList<>(players(LINE_2, 1));
        all.addAll(players(LINE_1, 2));
        List<WarningDto> warnings = WarningRules.warnings(EventType.PRACTICE, TEAM, WarningMinimums.DEFAULTS, all);
        assertEquals(List.of(WarningType.GOALIES, WarningType.FIELD_PLAYERS, WarningType.LINE, WarningType.LINE),
                warnings.stream().map(WarningDto::type).toList());
        assertEquals("Linie 1", warnings.get(2).groupName());
        assertEquals("Linie 2", warnings.get(3).groupName());
        assertEquals(3, warnings.get(1).count());
    }

    @Test
    void criterion2CountsWhoIsCountedNotWhoIsInTheTeam() {
        List<WarningRules.Player> all = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            all.add(player(i < 6 ? LINE_1 : LINE_2, false));
        }
        all.add(player(GOALIES, false));
        List<WarningDto> warnings = WarningRules.warnings(EventType.MATCH_DAY, TEAM, WarningMinimums.DEFAULTS, all);
        assertEquals(List.of(WarningType.GOALIES, WarningType.FIELD_PLAYERS),
                warnings.stream().map(WarningDto::type).toList());
        assertEquals(0, warnings.get(1).count());
    }

    @Test
    void criterion4NoWarningsWhenEverythingIsAboveTheMinimums() {
        assertTrue(WarningRules.warnings(EventType.MATCH_DAY, TEAM, WarningMinimums.DEFAULTS, full()).isEmpty());
    }

    @Test
    void criterion4MinimumZeroSwitchesTheWarningOff() {
        WarningMinimums off = new WarningMinimums(0, 0, 0, 0);
        assertTrue(WarningRules.warnings(EventType.MATCH_DAY, TEAM, off, players(LINE_1, 1)).isEmpty());
    }

    @Test
    void criterion4FieldPlayerMinimumPerEventType() {
        WarningMinimums m = new WarningMinimums(1, 12, 4, 3);
        assertEquals(12, WarningRules.fieldPlayerMinimum(EventType.MATCH_DAY, m));
        assertEquals(4, WarningRules.fieldPlayerMinimum(EventType.PRACTICE, m));
        assertEquals(new WarningMinimums(1, 10, 6, 3), WarningMinimums.DEFAULTS);
    }

    @Test
    void criterion5OnlyUpcomingNotCancelledPracticesAndMatchDays() {
        Instant later = NOW.plusSeconds(3600);
        Instant earlier = NOW.minusSeconds(3600);
        assertTrue(WarningRules.applies(EventType.PRACTICE, EventStatus.PLANNED, later, NOW));
        assertTrue(WarningRules.applies(EventType.MATCH_DAY, EventStatus.PLANNED, later, NOW));
        assertFalse(WarningRules.applies(EventType.PRACTICE, EventStatus.PLANNED, earlier, NOW));
        assertFalse(WarningRules.applies(EventType.PRACTICE, EventStatus.PLANNED, NOW, NOW));
        assertFalse(WarningRules.applies(EventType.PRACTICE, EventStatus.CANCELLED, later, NOW));
        assertFalse(WarningRules.applies(EventType.OTHER, EventStatus.PLANNED, later, NOW));
    }

    @Test
    void criterion6OnlyCoachesAndAdminsSeeWarnings() {
        assertTrue(WarningRules.visible(true));
        assertFalse(WarningRules.visible(false));
    }
}
