package ch.stvbero.platform.attendance.calendar;

import ch.stvbero.platform.core.clock.BackendClock;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Writes an iCalendar feed according to RFC 5545 (CAL-02): CRLF line ends, lines folded after 75 octets,
 * text values escaped. Times are local times in Europe/Zurich with a VTIMEZONE, so that calendars show
 * the right time on both sides of a daylight saving change (CAL-05).
 */
final class ICalendarWriter {

    static final String PRODUCT = "-//STV Beromuenster//StvBeroPlatform//DE";
    static final String TZID = "Europe/Zurich";
    static final String UID_DOMAIN = "@stvbero";

    private static final int MAX_OCTETS = 75;
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ROOT);
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    /**
     * Europe/Zurich since 1981: daylight saving time from the last Sunday in March 02:00 to the last Sunday in
     * October 03:00. {@code ICalendarWriterTest} compares these rules with the Java time zone data.
     */
    static final List<String> VTIMEZONE = List.of(
            "BEGIN:VTIMEZONE",
            "TZID:" + TZID,
            "X-LIC-LOCATION:" + TZID,
            "BEGIN:DAYLIGHT",
            "TZOFFSETFROM:+0100",
            "TZOFFSETTO:+0200",
            "TZNAME:CEST",
            "DTSTART:19810329T020000",
            "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU",
            "END:DAYLIGHT",
            "BEGIN:STANDARD",
            "TZOFFSETFROM:+0200",
            "TZOFFSETTO:+0100",
            "TZNAME:CET",
            "DTSTART:19961027T030000",
            "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU",
            "END:STANDARD",
            "END:VTIMEZONE");

    private ICalendarWriter() {
    }

    /** The whole feed of one person. */
    static String write(String calendarName, Instant now, String appUrl, List<CalendarEntry> entries) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:" + PRODUCT);
        line(out, "CALSCALE:GREGORIAN");
        // No METHOD: a subscribed feed is no iTIP message (RFC 5546 would then require an ORGANIZER).
        line(out, "X-WR-CALNAME:" + text(calendarName));
        line(out, "X-WR-TIMEZONE:" + TZID);
        // Hint for calendar apps how often to fetch again. Many apps choose their own interval.
        line(out, "REFRESH-INTERVAL;VALUE=DURATION:PT1H");
        line(out, "X-PUBLISHED-TTL:PT1H");
        VTIMEZONE.forEach(l -> line(out, l));
        for (CalendarEntry entry : entries) {
            event(out, entry, now, appUrl);
        }
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    private static void event(StringBuilder out, CalendarEntry entry, Instant now, String appUrl) {
        line(out, "BEGIN:VEVENT");
        // The ID of the event: a rescheduled or changed event replaces the entry instead of adding one (CAL-04).
        line(out, "UID:" + entry.id() + UID_DOMAIN);
        line(out, "DTSTAMP:" + UTC.format(now));
        line(out, "DTSTART;TZID=" + TZID + ":" + local(entry.start()));
        line(out, "DTEND;TZID=" + TZID + ":" + local(entry.end()));
        line(out, "SUMMARY:" + text(CalendarRules.summary(entry.type(), entry.title(), entry.teams(), entry.cancelled())));
        String location = CalendarRules.location(entry.locationName(), entry.locationAddress());
        if (location != null) {
            line(out, "LOCATION:" + text(location));
        }
        if (entry.latitude() != null && entry.longitude() != null) {
            line(out, "GEO:" + entry.latitude() + ";" + entry.longitude());
        }
        line(out, "DESCRIPTION:" + text(CalendarRules.description(entry, appUrl)));
        line(out, "URL:" + uri(CalendarRules.eventLink(appUrl, entry.id())));
        line(out, "STATUS:" + (entry.cancelled() ? "CANCELLED" : "CONFIRMED"));
        line(out, "TRANSP:OPAQUE");
        line(out, "END:VEVENT");
    }

    /** Local time in Europe/Zurich, e.g. {@code 20261117T183000}. */
    static String local(Instant instant) {
        return LOCAL.format(instant.atZone(BackendClock.ZONE).toLocalDateTime());
    }

    /** Escapes a TEXT value (RFC 5545, 3.3.11). */
    static String text(String value) {
        StringBuilder out = new StringBuilder(value.length());
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case ';' -> out.append("\\;");
                case ',' -> out.append("\\,");
                case '\n' -> out.append("\\n");
                default -> {
                    if (c >= 0x20 || c == '\t') {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    /** A URI value must not contain line breaks or control characters. */
    private static String uri(String value) {
        return value.replaceAll("[\\x00-\\x20\\x7f]", "");
    }

    /** Appends one content line, folded after 75 octets without splitting a UTF-8 character (RFC 5545, 3.1). */
    static void line(StringBuilder out, String line) {
        int octets = 0;
        int limit = MAX_OCTETS;
        for (int i = 0; i < line.length(); ) {
            int codePoint = line.codePointAt(i);
            int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > limit) {
                out.append("\r\n ");
                octets = 1;
                limit = MAX_OCTETS;
            }
            out.appendCodePoint(codePoint);
            octets += size;
            i += Character.charCount(codePoint);
        }
        out.append("\r\n");
    }
}
