package ch.stvbero.platform.attendance.calendar;

import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.attendance.event.EventType;
import ch.stvbero.platform.attendance.event.ResponseStatus;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F-18: iCalendar format (CAL-02, CAL-05) and the texts of an entry (CAL-03, CAL-04, CAL-08), without database. */
class ICalendarWriterTest {

    private static final String APP = "https://app.example.ch";
    private static final UUID ID = UUID.fromString("40000000-0000-4000-8000-000000000004");

    private static Instant zurich(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(BackendClock.ZONE).toInstant();
    }

    private static CalendarEntry entry(boolean cancelled, ResponseStatus response, String comment,
                                       List<CalendarEntry.MatchLine> matches) {
        return new CalendarEntry(ID, EventType.MATCH_DAY, "Spieltag", zurich(2026, 11, 22, 9, 30),
                zurich(2026, 11, 22, 13, 0), cancelled, cancelled ? "Halle besetzt" : null, List.of("Team 1"),
                "Sporthalle Seefeld", "Musterdorf", "https://maps.example/?q=a,b", 47.1, 8.2,
                zurich(2026, 11, 22, 8, 0), "Parkplatz Sporthalle Nord", "Weisse Trikots mitnehmen", matches,
                response, comment);
    }

    // ------------------------------------------------------------------ CAL-02: format

    @Test
    void cal02TextValuesAreEscaped() {
        assertEquals("a\\, b\\; c\\\\ d\\ne", ICalendarWriter.text("a, b; c\\ d\r\ne"));
        assertEquals("ohne Steuerzeichen", ICalendarWriter.text("ohne\u0007 Steuerzeichen"));
    }

    @Test
    void cal02LongLinesAreFoldedAfter75OctetsWithoutSplittingCharacters() {
        StringBuilder out = new StringBuilder();
        String value = "DESCRIPTION:" + "Grüessech mitenand, ".repeat(12);
        ICalendarWriter.line(out, value);
        String folded = out.toString();
        assertTrue(folded.endsWith("\r\n"));
        String[] lines = folded.substring(0, folded.length() - 2).split("\r\n", -1);
        assertTrue(lines.length > 3);
        for (int i = 0; i < lines.length; i++) {
            assertTrue(lines[i].getBytes(StandardCharsets.UTF_8).length <= 75, "line " + i + " too long");
            if (i > 0) {
                assertTrue(lines[i].startsWith(" "), "continuation line starts with a space");
            }
        }
        assertEquals(value, folded.replace("\r\n ", "").replace("\r\n", ""));
    }

    @Test
    void cal02FeedHasCalendarFrameAndCrlf() {
        String feed = ICalendarWriter.write("STV Bero", zurich(2026, 11, 16, 6, 0), APP,
                List.of(entry(false, ResponseStatus.ATTENDING, null, List.of())));
        assertTrue(feed.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:"));
        assertTrue(feed.endsWith("END:VEVENT\r\nEND:VCALENDAR\r\n"));
        assertFalse(feed.replace("\r\n", "").contains("\n"), "only CRLF line ends");
        assertTrue(feed.contains("\r\nDTSTAMP:20261116T050000Z\r\n"));
        assertTrue(feed.contains("\r\nUID:40000000-0000-4000-8000-000000000004@stvbero\r\n"));
        assertTrue(feed.contains("\r\nSTATUS:CONFIRMED\r\n"));
    }

    // ------------------------------------------------------------------ CAL-05: time zone

    @Test
    void cal05LocalTimesOnBothSidesOfDaylightSavingChanges() {
        // Summer time (CEST, UTC+2) and winter time (CET, UTC+1)
        assertEquals("20261020T183000", ICalendarWriter.local(Instant.parse("2026-10-20T16:30:00Z")));
        assertEquals("20261027T183000", ICalendarWriter.local(Instant.parse("2026-10-27T17:30:00Z")));
        assertEquals("20270323T183000", ICalendarWriter.local(Instant.parse("2027-03-23T17:30:00Z")));
        assertEquals("20270330T183000", ICalendarWriter.local(Instant.parse("2027-03-30T16:30:00Z")));
    }

    /** The VTIMEZONE rules (last Sunday in March 02:00, last Sunday in October 03:00) match the Java zone data. */
    @Test
    void cal05TimeZoneRulesMatchEuropeZurich() {
        assertTrue(ICalendarWriter.VTIMEZONE.contains("RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU"));
        assertTrue(ICalendarWriter.VTIMEZONE.contains("RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU"));
        List<ZoneOffsetTransition> actual = new ArrayList<>();
        List<ZoneOffsetTransition> expected = new ArrayList<>();
        for (int year = 2025; year <= 2040; year++) {
            ZoneOffsetTransition spring = BackendClock.ZONE.getRules()
                    .nextTransition(LocalDate.of(year, 1, 1).atStartOfDay(BackendClock.ZONE).toInstant());
            ZoneOffsetTransition autumn = BackendClock.ZONE.getRules().nextTransition(spring.getInstant());
            actual.add(spring);
            actual.add(autumn);
            LocalDate lastSundayMarch = LocalDate.of(year, 3, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.SUNDAY));
            LocalDate lastSundayOctober = LocalDate.of(year, 10, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.SUNDAY));
            expected.add(ZoneOffsetTransition.of(lastSundayMarch.atTime(2, 0), ZoneOffset.ofHours(1), ZoneOffset.ofHours(2)));
            expected.add(ZoneOffsetTransition.of(lastSundayOctober.atTime(3, 0), ZoneOffset.ofHours(2), ZoneOffset.ofHours(1)));
        }
        assertEquals(expected, actual);
    }

    // ------------------------------------------------------------------ CAL-03, CAL-04: texts

    @Test
    void cal03SummaryNamesTypeAndTeams() {
        assertEquals("Training · Team 1 + Team 2",
                CalendarRules.summary(EventType.PRACTICE, "Training", List.of("Team 1", "Team 2"), false));
        assertEquals("Spieltag: Cup · Team 1", CalendarRules.summary(EventType.MATCH_DAY, "Cup", List.of("Team 1"), false));
        assertEquals("Teamessen · Team 1", CalendarRules.summary(EventType.OTHER, "Teamessen", List.of("Team 1"), false));
        assertEquals("Abgesagt: Training · Team 1",
                CalendarRules.summary(EventType.PRACTICE, "Training", List.of("Team 1"), true));
    }

    @Test
    void cal03DescriptionWithMeetingPointMatchesNotesResponseAndLink() {
        String description = CalendarRules.description(entry(false, ResponseStatus.MAYBE, "Muss evtl. arbeiten", List.of(
                new CalendarEntry.MatchLine(zurich(2026, 11, 22, 9, 30), "UHC Musterdorf", true, "5:3"),
                new CalendarEntry.MatchLine(zurich(2026, 11, 22, 11, 45), "Unihockey Beispielstadt", false, null))), APP);
        assertEquals("""
                Treffpunkt: 08:00, Parkplatz Sporthalle Nord
                Spiele:
                09:30 Team 1 – UHC Musterdorf 5:3
                11:45 Unihockey Beispielstadt – Team 1
                Notizen: Weisse Trikots mitnehmen
                Karte: https://maps.example/?q=a,b
                Deine Antwort: vielleicht («Muss evtl. arbeiten»)
                In der App: https://app.example.ch/termine/40000000-0000-4000-8000-000000000004""", description);
    }

    @Test
    void cal04CancelledEventIsMarked() {
        String feed = ICalendarWriter.write("STV Bero", zurich(2026, 11, 16, 6, 0), APP + "/",
                List.of(entry(true, ResponseStatus.PENDING, null, List.of())));
        assertTrue(feed.contains("\r\nSTATUS:CANCELLED\r\n"));
        assertTrue(feed.contains("\r\nSUMMARY:Abgesagt: Spieltag · Team 1\r\n"));
        String unfolded = feed.replace("\r\n ", "");
        assertTrue(unfolded.contains("DESCRIPTION:Abgesagt: Halle besetzt\\nTreffpunkt"));
        assertTrue(unfolded.contains("Deine Antwort: offen\\nIn der App: https://app.example.ch/termine/" + ID));
    }

    // ------------------------------------------------------------------ CAL-06: token

    @Test
    void cal06TokenIsLongRandomAndUrlSafe() {
        SecureRandom random = new SecureRandom();
        String a = CalendarRules.newToken(random);
        String b = CalendarRules.newToken(random);
        assertEquals(43, a.length());
        assertTrue(a.matches("[A-Za-z0-9_-]+"));
        assertNotEquals(a, b);
        assertTrue(CalendarRules.wellFormed(a));
        assertFalse(CalendarRules.wellFormed("kurz"));
        assertFalse(CalendarRules.wellFormed(null));
        assertFalse(CalendarRules.wellFormed(a.substring(1) + "%"));
    }

    // ------------------------------------------------------------------ TER-31: pre-training

    @Test
    void ter31EntryBeginsWithThePreTrainingAndNamesBothTimes() {
        CalendarEntry plain = new CalendarEntry(ID, EventType.PRACTICE, "Training", zurich(2026, 11, 19, 20, 20),
                zurich(2026, 11, 19, 21, 50), false, null, List.of("Team 1"), null, null, null, null, null, null,
                null, null, List.of(), ResponseStatus.PENDING, null);
        CalendarEntry with = new CalendarEntry(plain.id(), plain.type(), plain.title(), plain.start(), plain.end(),
                false, null, plain.teams(), null, null, null, null, null, null, null, null, List.of(),
                ResponseStatus.PENDING, null,
                new CalendarEntry.PreTrainingLine("Joggen", zurich(2026, 11, 19, 20, 0)));
        assertEquals(plain.start(), plain.begin());
        assertEquals(zurich(2026, 11, 19, 20, 0), with.begin());
        assertEquals("Vortraining 20:00: Joggen · Training 20:20–21:50", CalendarRules.preTrainingLine(with));
        assertTrue(CalendarRules.description(with, APP).startsWith("Vortraining 20:00: Joggen · Training 20:20–21:50\n"));
        assertFalse(CalendarRules.description(plain, APP).contains("Vortraining"));
        String feed = ICalendarWriter.write("STV Bero", zurich(2026, 11, 15, 12, 0), APP, List.of(with));
        assertTrue(feed.contains("DTSTART;TZID=Europe/Zurich:20261119T200000"));
        assertTrue(feed.contains("DTEND;TZID=Europe/Zurich:20261119T215000"));
    }

    @Test
    void anm14SomeoneWhoSkipsThePreTrainingGetsAnEntryThatBeginsWithThePractice() {
        CalendarEntry.PreTrainingLine declined = new CalendarEntry.PreTrainingLine("Joggen", zurich(2026, 11, 19, 20, 0),
                ResponseStatus.DECLINED);
        CalendarEntry skips = entry(ResponseStatus.ATTENDING, declined);
        assertTrue(skips.withoutPreTraining());
        assertEquals(skips.start(), skips.begin());
        String description = CalendarRules.description(skips, APP);
        assertTrue(description.contains("Deine Antwort: " + CalendarRules.responseLabel(ResponseStatus.ATTENDING)
                + "\nVortraining: " + CalendarRules.responseLabel(ResponseStatus.DECLINED)), description);
        String feed = ICalendarWriter.write("STV Bero", zurich(2026, 11, 15, 12, 0), APP, List.of(skips));
        assertTrue(feed.contains("DTSTART;TZID=Europe/Zurich:20261119T202000"));

        // «dabei» at both, or no answer yet: the entry begins with the pre-training
        for (ResponseStatus answer : List.of(ResponseStatus.ATTENDING, ResponseStatus.PENDING)) {
            CalendarEntry with = entry(ResponseStatus.ATTENDING,
                    new CalendarEntry.PreTrainingLine("Joggen", zurich(2026, 11, 19, 20, 0), answer));
            assertEquals(zurich(2026, 11, 19, 20, 0), with.begin());
            assertTrue(CalendarRules.description(with, APP).contains("\nVortraining: " + CalendarRules.responseLabel(answer)));
        }
        // Not at the practice: the pre-training has no line of its own, the entry stays as it is
        CalendarEntry away = entry(ResponseStatus.DECLINED, declined);
        assertEquals(zurich(2026, 11, 19, 20, 0), away.begin());
        assertFalse(CalendarRules.description(away, APP).contains("\nVortraining: "));
    }

    private static CalendarEntry entry(ResponseStatus practice, CalendarEntry.PreTrainingLine preTraining) {
        return new CalendarEntry(ID, EventType.PRACTICE, "Training", zurich(2026, 11, 19, 20, 20),
                zurich(2026, 11, 19, 21, 50), false, null, List.of("Team 1"), null, null, null, null, null, null,
                null, null, List.of(), practice, null, preTraining);
    }
}
