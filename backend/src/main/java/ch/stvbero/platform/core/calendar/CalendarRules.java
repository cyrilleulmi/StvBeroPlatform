package ch.stvbero.platform.core.calendar;

import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.event.EventType;
import ch.stvbero.platform.core.event.ResponseStatus;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Rules of the calendar subscription (CAL), without database: token, period and the texts of an entry. */
public final class CalendarRules {

    /** Past events stay in the feed for 30 days (CAL-02). */
    public static final Duration PAST = Duration.ofDays(30);

    /** 32 random bytes, Base64 URL-safe without padding: 43 characters (CAL-06). */
    static final int TOKEN_BYTES = 32;

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    /** Path of the feed. The frontend puts the address of the app in front of it. */
    static final String FEED_PATH = "/api/calendar/%s.ics";

    private CalendarRules() {
    }

    /** New random token with 256 bits (CAL-06). */
    static String newToken(SecureRandom random) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Only well-formed tokens are looked up at all. Anything else is simply not found (404). */
    static boolean wellFormed(String token) {
        return token != null && TOKEN.matcher(token).matches();
    }

    static String feedPath(String token) {
        return FEED_PATH.formatted(token);
    }

    /** Events that ended within the last 30 days or later are in the feed (CAL-02). */
    static Instant from(Instant now) {
        return now.minus(PAST);
    }

    /** Link to the event in the app (CAL-03). */
    static String eventLink(String appUrl, UUID eventId) {
        String base = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
        return base + "/termine/" + eventId;
    }

    /**
     * Title of the entry: type and teams (CAL-03), e.g. «Training · Team 1 + Team 2». If the title of the event
     * does not name the type, the type comes first: «Spieltag: Cup · Team 1». Events of the type «Anlass» keep
     * their title («Teamessen · Team 1»). Cancelled events start with «Abgesagt:», for calendars that ignore
     * {@code STATUS:CANCELLED} (CAL-04).
     */
    static String summary(EventType type, String title, List<String> teams, boolean cancelled) {
        String label = typeLabel(type);
        String name = type == EventType.OTHER || title.toLowerCase(Locale.ROOT).contains(label.toLowerCase(Locale.ROOT))
                ? title : label + ": " + title;
        String text = teams.isEmpty() ? name : name + " · " + String.join(" + ", teams);
        return cancelled ? "Abgesagt: " + text : text;
    }

    static String typeLabel(EventType type) {
        return switch (type) {
            case PRACTICE -> "Training";
            case MATCH_DAY -> "Spieltag";
            case OTHER -> "Anlass";
        };
    }

    static String responseLabel(ResponseStatus status) {
        return switch (status) {
            case ATTENDING -> "dabei";
            case DECLINED -> "nicht dabei";
            case MAYBE -> "vielleicht";
            case PENDING -> "offen";
        };
    }

    /** Location as one line: «Sporthalle Seefeld, Musterdorf». */
    static String location(String name, String address) {
        if (name == null) {
            return null;
        }
        return address == null ? name : name + ", " + address;
    }

    /**
     * Description of the entry (CAL-03): cancellation, meeting point, matches, notes, the person's own response
     * and the link into the app. Contains nothing of other people (CAL-08).
     */
    static String description(CalendarEntry entry, String appUrl) {
        List<String> lines = new ArrayList<>();
        if (entry.cancelled()) {
            lines.add(entry.cancellationReason() == null ? "Abgesagt." : "Abgesagt: " + entry.cancellationReason());
        }
        if (entry.meetingPointTime() != null) {
            String time = TIME.format(entry.meetingPointTime().atZone(BackendClock.ZONE));
            lines.add("Treffpunkt: " + time
                    + (entry.meetingPointLocation() == null ? "" : ", " + entry.meetingPointLocation()));
        }
        if (!entry.matches().isEmpty()) {
            lines.add("Spiele:");
            String own = entry.teams().isEmpty() ? "Wir" : entry.teams().getFirst();
            for (CalendarEntry.MatchLine match : entry.matches()) {
                String pairing = match.home() ? own + " – " + match.opponent() : match.opponent() + " – " + own;
                lines.add(TIME.format(match.start().atZone(BackendClock.ZONE)) + " " + pairing
                        + (match.result() == null ? "" : " " + match.result()));
            }
        }
        if (entry.notes() != null) {
            lines.add("Notizen: " + entry.notes());
        }
        if (entry.mapsUrl() != null) {
            lines.add("Karte: " + entry.mapsUrl());
        }
        lines.add("Deine Antwort: " + responseLabel(entry.myResponse())
                + (entry.myComment() == null ? "" : " («" + entry.myComment() + "»)"));
        lines.add("In der App: " + eventLink(appUrl, entry.id()));
        return String.join("\n", lines);
    }
}
