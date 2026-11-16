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
 * Fachregeln des Abgleichs mit swiss unihockey (SUH-02 bis SUH-08). Ohne Datenbank, damit sie als Unit-Test
 * prüfbar sind; {@link Abgleich} wendet sie an.
 */
public final class SyncRules {

    /**
     * Wie lange ein Spiel dauert, um das Ende eines importierten Spieltags zu bestimmen: Ende = letzte Anspielzeit
     * plus diese Dauer (Annahme F-07, passt zu Termin 04 im Seed: 11:45 bis 13:00).
     */
    public static final Duration MATCH_DURATION = Duration.ofMinutes(75);

    public static final String TITLE = "Spieltag";

    private static final String[] WEEKDAYS = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private SyncRules() {
    }

    // ------------------------------------------------------------------ Wer sind wir?

    /**
     * Name des eigenen Teams in den Spielen. swiss unihockey liefert die Spiele eines Teams nur mit den Namen von
     * Heim- und Gastteam. Das eigene Team ist das, das in jedem Spiel vorkommt. Kommen mehrere in jedem Spiel vor
     * (z.B. bei nur einem Spiel), entscheidet der Name des Teams in der App, sonst das Heimteam des ersten Spiels
     * (Annahme F-07, O-21).
     *
     * @return {@code null}, wenn es keine Spiele gibt
     */
    public static String ownTeam(List<SuMatch> matches, String teamName) {
        if (matches.isEmpty()) {
            return null;
        }
        Set<String> inAll = null;
        for (SuMatch s : matches) {
            Set<String> names = new LinkedHashSet<>(List.of(norm(s.homeTeam()), norm(s.awayTeam())));
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

    /** Gegner und Heim/Auswärts aus Sicht des eigenen Teams. */
    public record Side(String opponent, boolean home) {
    }

    public static Side side(SuMatch s, String ownTeam) {
        boolean away = ownTeam != null && norm(s.awayTeam()).equals(ownTeam)
                && !norm(s.homeTeam()).equals(ownTeam);
        return away ? new Side(truncate(s.homeTeam(), 80), false) : new Side(truncate(s.awayTeam(), 80), true);
    }

    // ------------------------------------------------------------------ Spieltage bilden (SUH-04)

    /** Spiele am selben Tag in derselben Halle gehören zu einem Spieltag (SUH-04). */
    public record Key(LocalDate day, String hall) {

        public static Key of(Instant startTime, String hall) {
            return new Key(EventRules.date(startTime), norm(hall == null ? "" : hall));
        }
    }

    /** Spiele nach Tag und Halle gruppiert, Gruppen und Spiele nach Anspielzeit. */
    public static Map<Key, List<SuMatch>> groupByDayAndHall(Collection<SuMatch> matches) {
        Map<Key, List<SuMatch>> groups = new LinkedHashMap<>();
        matches.stream()
                .sorted(Comparator.comparing(SuMatch::startTime).thenComparing(SuMatch::suMatchId))
                .forEach(s -> groups.computeIfAbsent(Key.of(s.startTime(), s.hall()), k -> new ArrayList<>())
                        .add(s));
        return groups;
    }

    /** Beginn und Ende eines importierten Spieltags. */
    public record Period(Instant start, Instant end) {
    }

    /**
     * Beginn ist die erste Anspielzeit (Annahme F-03), Ende die letzte plus {@link #SPIELDAUER}.
     */
    public static Period period(Collection<Instant> startTimes) {
        Instant first = startTimes.stream().min(Comparator.naturalOrder()).orElseThrow();
        Instant last = startTimes.stream().max(Comparator.naturalOrder()).orElseThrow();
        return new Period(first, last.plus(MATCH_DURATION));
    }

    /** Nur Spiele, die in die Saison fallen, werden übernommen. */
    public static boolean inSeason(Instant startTime, LocalDate startDate, LocalDate endDate) {
        LocalDate day = EventRules.date(startTime);
        return !day.isBefore(startDate) && !day.isAfter(endDate);
    }

    // ------------------------------------------------------------------ Spielort (SUH-07)

    /**
     * Google-Maps-Link des Spielorts (SUH-07): der von swiss unihockey gelieferte, sonst aus den Koordinaten,
     * sonst eine Suche nach Halle und Ort.
     */
    public static String mapsUrl(SuMatch s) {
        if (s.mapsUrl() != null && !s.mapsUrl().isBlank()) {
            return truncate(s.mapsUrl().strip(), 500);
        }
        String search = s.latitude() != null && s.longitude() != null
                ? s.latitude() + "," + s.longitude()
                : s.hall().strip() + (s.location() == null ? "" : " " + s.location().strip());
        return truncate("https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(search, StandardCharsets.UTF_8),
                500);
    }

    // ------------------------------------------------------------------ Benachrichtigung (BEN-06)

    /** Was an einem Spiel für die Spielerinnen zählt (SUH-05): Zeit, Halle, Gegner, Heim/Auswärts. */
    public record MatchState(String opponent, Instant startTime, boolean home, String hall) {
    }

    /** Hat sich etwas geändert, worüber das Team benachrichtigt wird (SUH-05, BEN-06)? Das Resultat zählt nicht. */
    public static boolean significantlyChanged(MatchState old, MatchState fresh) {
        return !old.startTime().equals(fresh.startTime()) || !old.opponent().equals(fresh.opponent())
                || old.home() != fresh.home() || !norm(Objects.toString(old.hall(), "")).equals(norm(fresh.hall()));
    }

    /**
     * Eine Änderung in Worten, z.B. «Spiel gegen Unihockey Beispielstadt neu um 12:15» oder «Spiel um 09:30 neu
     * gegen UHC Drei, neu in Turnhalle Weid».
     */
    public static String change(MatchState old, MatchState fresh) {
        List<String> parts = new ArrayList<>();
        if (!old.startTime().equals(fresh.startTime())) {
            boolean otherDay = !EventRules.date(old.startTime()).equals(EventRules.date(fresh.startTime()));
            parts.add(otherDay ? "neu am " + instant(fresh.startTime()) : "neu um " + time(fresh.startTime()));
        }
        if (!norm(Objects.toString(old.hall(), "")).equals(norm(fresh.hall()))) {
            parts.add("neu in " + fresh.hall());
        }
        if (!old.opponent().equals(fresh.opponent())) {
            parts.add("neu gegen " + fresh.opponent());
        }
        if (old.home() != fresh.home()) {
            parts.add(fresh.home() ? "neu als Heimspiel" : "neu als Auswärtsspiel");
        }
        String match = old.opponent().equals(fresh.opponent())
                ? "Spiel gegen " + fresh.opponent()
                : "Spiel um " + time(old.startTime());
        return match + " " + String.join(", ", parts);
    }

    /** «Spieltag So 22.11. wurde geändert: Spiel gegen Unihockey Beispielstadt neu um 12:15.» */
    public static String matchChangedText(String title, Instant oldStart, List<String> changes) {
        return NotificationRules.label(title, oldStart) + " wurde geändert: "
                + String.join("; ", changes) + ".";
    }

    // ------------------------------------------------------------------ Hilfen

    static String norm(String name) {
        return name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static String truncate(String text, int longitude) {
        String t = text.strip();
        return t.length() <= longitude ? t : t.substring(0, longitude);
    }

    private static String time(Instant instant) {
        return TIME.format(instant.atZone(BackendClock.ZONE));
    }

    private static String instant(Instant instant) {
        ZonedDateTime z = instant.atZone(BackendClock.ZONE);
        return WEEKDAYS[z.getDayOfWeek().getValue() - 1] + " " + TAG.format(z) + " " + TIME.format(z);
    }
}
