package ch.stvbero.platform.driver;

import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.driver.TimelineRules.Item;
import ch.stvbero.platform.driver.TimelineRules.Kind;
import ch.stvbero.platform.driver.TimelineRules.MatchSlot;
import ch.stvbero.platform.driver.TimelineRules.Plan;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TML-01 to TML-06: the timeline of a match day, without a database. */
class TimelineRulesTest {

    private static final Duration MATCH = Duration.ofMinutes(50);
    private static final Duration LEAD = Duration.ofMinutes(70);
    private static final Duration DRIVE = Duration.ofMinutes(26);
    private static final UUID FIRST = UUID.fromString("50000000-0000-4000-8000-000000000001");
    private static final UUID SECOND = UUID.fromString("50000000-0000-4000-8000-000000000002");

    private static Instant at(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 22, hour, minute).atZone(BackendClock.ZONE).toInstant();
    }

    private static final List<MatchSlot> TWO_MATCHES = List.of(
            new MatchSlot(SECOND, at(11, 45), "Unihockey Beispielstadt", false),
            new MatchSlot(FIRST, at(9, 30), "UHC Musterdorf", true));

    private static List<Kind> kinds(Plan plan) {
        return plan.items().stream().map(Item::kind).toList();
    }

    @Test
    void tml01FromDepartureToReturn() {
        Plan plan = TimelineRules.plan(TWO_MATCHES, MATCH, LEAD, at(7, 50), DRIVE);

        assertEquals(List.of(Kind.DEPARTURE, Kind.ARRIVAL, Kind.WARM_UP, Kind.MATCH, Kind.BREAK, Kind.MATCH,
                Kind.COOL_DOWN, Kind.RETURN), kinds(plan));
        List<Item> items = plan.items();
        assertEquals(new Item(Kind.DEPARTURE, at(7, 50), null, 26, null, null, null), items.get(0));
        assertEquals(new Item(Kind.ARRIVAL, at(8, 16), null, null, null, null, null), items.get(1));
        // Time before the first match
        assertEquals(new Item(Kind.WARM_UP, at(8, 16), at(9, 30), 74, null, null, null), items.get(2));
        // Matches in order of time, each for the match duration of the team (TML-02)
        assertEquals(new Item(Kind.MATCH, at(9, 30), at(10, 20), 50, FIRST, "UHC Musterdorf", true), items.get(3));
        // Time between the matches
        assertEquals(new Item(Kind.BREAK, at(10, 20), at(11, 45), 85, null, null, null), items.get(4));
        assertEquals(new Item(Kind.MATCH, at(11, 45), at(12, 35), 50, SECOND, "Unihockey Beispielstadt", false),
                items.get(5));
        // 20 minutes to cool down and shower after the last match (TML-05)
        assertEquals(new Item(Kind.COOL_DOWN, at(12, 35), at(12, 55), 20, null, null, null), items.get(6));
        // Approximate return: after the cool down, the driving time back (TML-06)
        assertEquals(new Item(Kind.RETURN, at(13, 21), null, 26, null, null, null), items.get(7));
        assertEquals(at(7, 50), plan.departure());
        assertFalse(plan.departureSuggested());
        assertFalse(plan.arrivesLate());
    }

    @Test
    void tml04SuggestedDepartureIsFirstMatchMinusLeadMinusDriveRoundedDown() {
        Plan plan = TimelineRules.plan(TWO_MATCHES, MATCH, LEAD, null, DRIVE);

        // 09:30 - 70 min = 08:20 at the venue; minus 26 min = 07:54; rounded down to 07:50
        assertEquals(at(8, 20), plan.arriveBy());
        assertEquals(at(7, 50), plan.suggestedDeparture());
        assertEquals(at(7, 50), plan.departure());
        assertTrue(plan.departureSuggested(), "no meeting time yet: the timeline shows the suggestion");
        assertEquals(Kind.DEPARTURE, plan.items().get(0).kind());
    }

    @Test
    void tml03MeetingTimeThatLeavesLessThanTheLeadCountsAsLate() {
        Plan plan = TimelineRules.plan(TWO_MATCHES, MATCH, LEAD, at(8, 0), DRIVE);

        // 08:00 + 26 min = 08:26, the team wants to be there at 08:20
        assertTrue(plan.arrivesLate());
        assertEquals(at(7, 50), plan.suggestedDeparture(), "the suggestion stays visible next to the meeting time");
        assertEquals(64, plan.items().get(2).minutes());
    }

    @Test
    void arrivalAfterTheFirstStartHasNoTimeBeforeTheMatch() {
        Plan plan = TimelineRules.plan(TWO_MATCHES, MATCH, LEAD, at(9, 20), DRIVE);

        assertEquals(List.of(Kind.DEPARTURE, Kind.ARRIVAL, Kind.MATCH, Kind.BREAK, Kind.MATCH, Kind.COOL_DOWN,
                Kind.RETURN), kinds(plan));
        assertTrue(plan.arrivesLate());
    }

    @Test
    void withoutDrivingTimeThereIsNoArrivalReturnOrSuggestion() {
        Plan withMeetingTime = TimelineRules.plan(TWO_MATCHES, MATCH, LEAD, at(8, 0), null);
        assertEquals(List.of(Kind.DEPARTURE, Kind.MATCH, Kind.BREAK, Kind.MATCH, Kind.COOL_DOWN),
                kinds(withMeetingTime));
        assertNull(withMeetingTime.items().get(0).minutes());
        assertNull(withMeetingTime.suggestedDeparture());
        assertFalse(withMeetingTime.arrivesLate());

        Plan withoutAnything = TimelineRules.plan(TWO_MATCHES, MATCH, LEAD, null, null);
        assertEquals(List.of(Kind.MATCH, Kind.BREAK, Kind.MATCH, Kind.COOL_DOWN), kinds(withoutAnything));
        assertNull(withoutAnything.departure());
        assertFalse(withoutAnything.departureSuggested());
        assertEquals(at(8, 20), withoutAnything.arriveBy());
    }

    @Test
    void matchesBackToBackHaveNoBreakAndOneMatchHasNone() {
        List<MatchSlot> backToBack = List.of(new MatchSlot(FIRST, at(9, 30), "A", true),
                new MatchSlot(SECOND, at(10, 20), "B", true));
        assertEquals(List.of(Kind.MATCH, Kind.MATCH, Kind.COOL_DOWN),
                kinds(TimelineRules.plan(backToBack, MATCH, LEAD, null, null)));

        Plan one = TimelineRules.plan(List.of(new MatchSlot(FIRST, at(14, 0), "A", true)), MATCH, LEAD, null,
                Duration.ofMinutes(16));
        assertEquals(List.of(Kind.DEPARTURE, Kind.ARRIVAL, Kind.WARM_UP, Kind.MATCH, Kind.COOL_DOWN, Kind.RETURN),
                kinds(one));
        // 14:00 - 70 - 16 = 12:34, rounded down to 12:30
        assertEquals(LocalDateTime.of(2026, 11, 22, 12, 30).atZone(BackendClock.ZONE).toInstant(), one.departure());
    }

    @Test
    void withoutMatchesTheTimelineIsEmpty() {
        Plan plan = TimelineRules.plan(List.of(), MATCH, LEAD, at(8, 0), DRIVE);

        assertTrue(plan.items().isEmpty());
        assertNull(plan.suggestedDeparture());
        assertNull(plan.arriveBy());
    }

    @Test
    void roundsDownToFiveMinutesAndTakesTheLongestSettingOfSeveralTeams() {
        assertEquals(at(7, 50), TimelineRules.roundDown(at(7, 54)));
        assertEquals(at(7, 55), TimelineRules.roundDown(at(7, 55)));
        assertEquals(60, TimelineRules.longest(List.of(50, 60), 50));
        assertEquals(50, TimelineRules.longest(List.of(), 50));
    }
}
