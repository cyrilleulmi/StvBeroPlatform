package ch.stvbero.platform.core.driver;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Domain rules for the timeline of a match day (TML): from departure to the approximate return. Without a
 * database, so that unit tests can check them.
 */
public final class TimelineRules {

    /** Cool down and shower after the last match (TML-05). */
    public static final Duration COOL_DOWN = Duration.ofMinutes(20);

    /** A suggested departure is rounded down to this (TML-04): «07:35», not «07:38». */
    static final int ROUND_TO_SECONDS = 5 * 60;

    private TimelineRules() {
    }

    /** What a line of the timeline is. */
    @Schema(name = "TimelineKind")
    public enum Kind {
        /** The team leaves the meeting point. {@code minutes}: driving time, if known. */
        DEPARTURE,
        /** The team arrives at the venue. */
        ARRIVAL,
        /** Time at the venue before the first match. */
        WARM_UP,
        /** A match, from its start for the match duration of the team. */
        MATCH,
        /** Time between two matches. */
        BREAK,
        /** Cool down and shower after the last match. */
        COOL_DOWN,
        /** Approximate return at the meeting point. {@code minutes}: driving time. */
        RETURN
    }

    /** A match of the match day. */
    public record MatchSlot(UUID id, Instant start, String opponent, boolean home) {
    }

    /**
     * A line of the timeline. {@code end} and {@code minutes} only for spans; the match fields only for
     * {@link Kind#MATCH}.
     */
    public record Item(Kind kind, Instant start, Instant end, Integer minutes, UUID matchId, String opponent,
                       Boolean home) {

        static Item point(Kind kind, Instant at, Integer minutes) {
            return new Item(kind, at, null, minutes, null, null, null);
        }

        static Item span(Kind kind, Instant start, Instant end) {
            return new Item(kind, start, end, (int) Duration.between(start, end).toMinutes(), null, null, null);
        }
    }

    /**
     * The timeline.
     *
     * @param departure          when the team leaves: the meeting time, otherwise the suggestion; {@code null}
     *                           without either
     * @param departureSuggested the departure is the suggestion, no meeting time is set yet
     * @param suggestedDeparture arrival target minus driving time, rounded down to five minutes; {@code null}
     *                           without a driving time
     * @param arriveBy           when the team wants to be at the venue: first match minus the arrival lead
     * @param arrivesLate        with the set meeting time the team arrives later than it wants to
     */
    public record Plan(Instant departure, boolean departureSuggested, Instant suggestedDeparture, Instant arriveBy,
                       boolean arrivesLate, List<Item> items) {
    }

    /**
     * Builds the timeline (TML-01 to TML-06).
     *
     * @param matches       the matches of the match day, in any order
     * @param matchDuration how long a match takes (TML-02)
     * @param arrivalLead   how long before the first match the team wants to arrive (TML-03)
     * @param meetingTime   the meeting time of the match day, or {@code null} if it is still open
     * @param drive         driving time from the meeting point to the venue, or {@code null} if unknown
     */
    public static Plan plan(List<MatchSlot> matches, Duration matchDuration, Duration arrivalLead,
                            Instant meetingTime, Duration drive) {
        List<MatchSlot> ordered = matches.stream()
                .sorted(Comparator.comparing(MatchSlot::start).thenComparing(MatchSlot::id)).toList();
        if (ordered.isEmpty()) {
            return new Plan(meetingTime, false, null, null, false, List.of());
        }
        Instant first = ordered.get(0).start();
        Instant arriveBy = first.minus(arrivalLead);
        Instant suggested = drive == null ? null : roundDown(arriveBy.minus(drive));
        Instant departure = meetingTime != null ? meetingTime : suggested;
        List<Item> items = new ArrayList<>();
        boolean arrivesLate = false;
        if (departure != null) {
            items.add(Item.point(Kind.DEPARTURE, departure, drive == null ? null : (int) drive.toMinutes()));
            if (drive != null) {
                Instant arrival = departure.plus(drive);
                items.add(Item.point(Kind.ARRIVAL, arrival, null));
                if (arrival.isBefore(first)) {
                    items.add(Item.span(Kind.WARM_UP, arrival, first));
                }
                arrivesLate = arrival.isAfter(arriveBy);
            }
        }
        Instant lastEnd = first;
        for (int i = 0; i < ordered.size(); i++) {
            MatchSlot match = ordered.get(i);
            Instant end = match.start().plus(matchDuration);
            items.add(new Item(Kind.MATCH, match.start(), end, (int) matchDuration.toMinutes(), match.id(),
                    match.opponent(), match.home()));
            if (i + 1 < ordered.size() && ordered.get(i + 1).start().isAfter(end)) {
                items.add(Item.span(Kind.BREAK, end, ordered.get(i + 1).start()));
            }
            lastEnd = end.isAfter(lastEnd) ? end : lastEnd;
        }
        Instant ready = lastEnd.plus(COOL_DOWN);
        items.add(Item.span(Kind.COOL_DOWN, lastEnd, ready));
        if (drive != null) {
            items.add(Item.point(Kind.RETURN, ready.plus(drive), (int) drive.toMinutes()));
        }
        return new Plan(departure, meetingTime == null && suggested != null, suggested, arriveBy, arrivesLate, items);
    }

    /** Down to the previous five minutes: 07:38 becomes 07:35. */
    static Instant roundDown(Instant instant) {
        long seconds = instant.getEpochSecond();
        return Instant.ofEpochSecond(seconds - Math.floorMod(seconds, ROUND_TO_SECONDS));
    }

    /** The settings of a match day with several teams: the longest match duration and the longest lead. */
    public static int longest(List<Integer> minutes, int fallback) {
        return minutes.stream().mapToInt(Integer::intValue).max().orElse(fallback);
    }
}
