package ch.stvbero.platform.core.swissunihockey;

import ch.stvbero.platform.core.event.NotificationRules;
import ch.stvbero.platform.core.event.EventRules;
import ch.stvbero.platform.core.clock.BackendClock;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Domain rules of the sync with swiss unihockey (SUH-02 to SUH-08). Without a database, so that unit tests can
 * check them; {@link SuSync} applies them.
 */
public final class SyncRules {

    /**
     * How long a match takes, to determine the end of an imported match day: end = last start time plus this
     * duration (assumption F-07, matches event 04 in the seed: 11:45 to 13:00).
     */
    public static final Duration MATCH_DURATION = Duration.ofMinutes(75);

    public static final String TITLE = "Spieltag";

    private static final String[] WEEKDAYS = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private SyncRules() {
    }

    // ------------------------------------------------------------------ Who are we?

    /**
     * Name of the own team in the matches. swiss unihockey delivers the matches of a team only with the names of
     * the home and away team. The own team is the one that appears in every match. If several appear in every
     * match (e.g. when there is only one match), the name of the team in the app decides, otherwise the home team
     * of the first match (assumption F-07, O-21).
     *
     * @return {@code null} if there are no matches
     */
    public static String ownTeam(List<SuMatch> matches, String teamName) {
        if (matches.isEmpty()) {
            return null;
        }
        Set<String> inAll = null;
        for (SuMatch match : matches) {
            Set<String> names = new LinkedHashSet<>(List.of(norm(match.homeTeam()), norm(match.awayTeam())));
            if (inAll == null) {
                inAll = names;
            } else {
                inAll.retainAll(names);
            }
        }
        if (inAll.size() == 1) {
            return inAll.iterator().next();
        }
        String own = norm(teamName == null ? "" : teamName);
        if (inAll.contains(own)) {
            return own;
        }
        return inAll.isEmpty() ? own : inAll.iterator().next();
    }

    /** Opponent and home/away from the view of the own team. */
    public record Side(String opponent, boolean home) {
    }

    public static Side side(SuMatch match, String ownTeam) {
        boolean away = ownTeam != null && norm(match.awayTeam()).equals(ownTeam)
                && !norm(match.homeTeam()).equals(ownTeam);
        return away ? new Side(truncate(match.homeTeam(), 80), false) : new Side(truncate(match.awayTeam(), 80), true);
    }

    // ------------------------------------------------------------------ Forming match days (SUH-04)

    /** Matches on the same day in the same hall belong to one match day (SUH-04). */
    public record Key(LocalDate day, String hall) {

        public static Key of(Instant startTime, String hall) {
            return new Key(EventRules.date(startTime), norm(hall == null ? "" : hall));
        }
    }

    /** Matches grouped by day and hall; groups and matches ordered by start time. */
    public static Map<Key, List<SuMatch>> groupByDayAndHall(Collection<SuMatch> matches) {
        Map<Key, List<SuMatch>> groups = new LinkedHashMap<>();
        matches.stream()
                .sorted(Comparator.comparing(SuMatch::startTime).thenComparing(SuMatch::suMatchId))
                .forEach(m -> groups.computeIfAbsent(Key.of(m.startTime(), m.hall()), k -> new ArrayList<>())
                        .add(m));
        return groups;
    }

    /** Start and end of an imported match day. */
    public record Period(Instant start, Instant end) {
    }

    /**
     * The start is the first start time (assumption F-03), the end the last one plus {@link #MATCH_DURATION}.
     */
    public static Period period(Collection<Instant> startTimes) {
        Instant first = startTimes.stream().min(Comparator.naturalOrder()).orElseThrow();
        Instant last = startTimes.stream().max(Comparator.naturalOrder()).orElseThrow();
        return new Period(first, last.plus(MATCH_DURATION));
    }

    /** Only matches that fall into the season are imported. */
    public static boolean inSeason(Instant startTime, LocalDate startDate, LocalDate endDate) {
        LocalDate day = EventRules.date(startTime);
        return !day.isBefore(startDate) && !day.isAfter(endDate);
    }

    // ------------------------------------------------------------------ Venue (SUH-07)

    /**
     * Google Maps link of the venue (SUH-07): the one delivered by swiss unihockey, otherwise from the
     * coordinates, otherwise a search for hall and town.
     */
    public static String mapsUrl(SuMatch match) {
        if (match.mapsUrl() != null && !match.mapsUrl().isBlank()) {
            return truncate(match.mapsUrl().strip(), 500);
        }
        String search = match.latitude() != null && match.longitude() != null
                ? match.latitude() + "," + match.longitude()
                : match.hall().strip() + (match.location() == null ? "" : " " + match.location().strip());
        return truncate("https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(search, StandardCharsets.UTF_8),
                500);
    }

    // ------------------------------------------------------------------ Notification (BEN-06)

    /** What matters about a match for the players (SUH-05): time, hall, opponent, home/away. */
    public record MatchState(String opponent, Instant startTime, boolean home, String hall) {
    }

    /** Did something change that the team is notified about (SUH-05, BEN-06)? The result does not count. */
    public static boolean significantlyChanged(MatchState old, MatchState incoming) {
        return !old.startTime().equals(incoming.startTime()) || !old.opponent().equals(incoming.opponent())
                || old.home() != incoming.home() || !norm(Objects.toString(old.hall(), "")).equals(norm(incoming.hall()));
    }

    /**
     * A change in words, e.g. «Spiel gegen Unihockey Beispielstadt neu um 12:15» or «Spiel um 09:30 neu
     * gegen UHC Drei, neu in Turnhalle Weid».
     */
    public static String change(MatchState old, MatchState incoming) {
        List<String> parts = new ArrayList<>();
        if (!old.startTime().equals(incoming.startTime())) {
            boolean otherDay = !EventRules.date(old.startTime()).equals(EventRules.date(incoming.startTime()));
            parts.add(otherDay ? "neu am " + dateTime(incoming.startTime()) : "neu um " + time(incoming.startTime()));
        }
        if (!norm(Objects.toString(old.hall(), "")).equals(norm(incoming.hall()))) {
            parts.add("neu in " + incoming.hall());
        }
        if (!old.opponent().equals(incoming.opponent())) {
            parts.add("neu gegen " + incoming.opponent());
        }
        if (old.home() != incoming.home()) {
            parts.add(incoming.home() ? "neu als Heimspiel" : "neu als Auswärtsspiel");
        }
        String match = old.opponent().equals(incoming.opponent())
                ? "Spiel gegen " + incoming.opponent()
                : "Spiel um " + time(old.startTime());
        return match + " " + String.join(", ", parts);
    }

    /** Notification text, e.g. «Spieltag So 22.11. wurde geändert: Spiel gegen Unihockey Beispielstadt neu um 12:15.» */
    public static String matchChangedText(String title, Instant oldStart, List<String> changes) {
        return NotificationRules.label(title, oldStart) + " wurde geändert: "
                + String.join("; ", changes) + ".";
    }

    // ------------------------------------------------------------------ Helpers

    static String norm(String name) {
        return name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static String truncate(String text, int maxLength) {
        String stripped = text.strip();
        return stripped.length() <= maxLength ? stripped : stripped.substring(0, maxLength);
    }

    private static String time(Instant instant) {
        return TIME.format(instant.atZone(BackendClock.ZONE));
    }

    private static String dateTime(Instant instant) {
        ZonedDateTime zoned = instant.atZone(BackendClock.ZONE);
        return WEEKDAYS[zoned.getDayOfWeek().getValue() - 1] + " " + DAY.format(zoned) + " " + TIME.format(zoned);
    }
}
