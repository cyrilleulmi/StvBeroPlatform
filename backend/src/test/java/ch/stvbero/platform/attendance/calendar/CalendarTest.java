package ch.stvbero.platform.attendance.calendar;

import ch.stvbero.platform.testsupport.Seed;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.validate.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F-18: calendar subscription (CAL-01 to CAL-09) with the seed of docs/test-data.md. */
@QuarkusTest
class CalendarTest {

    static final String ANNA_FEED = "/api/calendar/" + Seed.ANNA_CALENDAR_TOKEN + ".ics";
    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";

    static String event(int nr) {
        return "40000000-0000-4000-8000-0000000000%02d".formatted(nr);
    }

    static String seriesEvent(int series, String date) {
        return UUID.nameUUIDFromBytes(("termin-" + series + "-" + date).getBytes(StandardCharsets.UTF_8)).toString();
    }

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static String feed(String path) {
        return given().get(path).then().statusCode(200)
                .contentType(startsWith("text/calendar"))
                .extract().asString();
    }

    /** Unfolded VEVENTs by UID (without «@stvbero»): property line name with parameters → value. */
    static Map<String, Map<String, String>> events(String feed) {
        Map<String, Map<String, String>> events = new LinkedHashMap<>();
        Map<String, String> current = null;
        for (String line : feed.replace("\r\n ", "").split("\r\n")) {
            if (line.equals("BEGIN:VEVENT")) {
                current = new LinkedHashMap<>();
            } else if (line.equals("END:VEVENT")) {
                String uid = current.get("UID");
                events.put(uid.substring(0, uid.indexOf('@')), current);
                current = null;
            } else if (current != null) {
                int colon = line.indexOf(':');
                current.put(line.substring(0, colon), line.substring(colon + 1));
            }
        }
        return events;
    }

    // ------------------------------------------------------------------ Criterion 1: address in the profile

    @Test
    void cal01PersonalAddressIsCreatedOnceAndNeedsLogin() {
        given().get("/api/me/calendar").then().statusCode(401);
        given().cookie("stv_session", login("anna")).get("/api/me/calendar").then().statusCode(200)
                .body("feedPath", equalTo(ANNA_FEED));
        String petra = login("petra");
        String first = given().cookie("stv_session", petra).get("/api/me/calendar").then().statusCode(200)
                .body("feedPath", matchesPattern("/api/calendar/[A-Za-z0-9_-]{43}\\.ics")).extract().path("feedPath");
        given().cookie("stv_session", petra).get("/api/me/calendar").then().body("feedPath", equalTo(first));
        assertNotEquals(ANNA_FEED, first);
        assertTrue(feed(first).contains("BEGIN:VCALENDAR"));
    }

    // ------------------------------------------------------------------ Criterion 2: content of the feed

    @Test
    void cal02FeedHasUpcomingEventsOfOwnTeamsAndThePast30Days() {
        Map<String, Map<String, String>> events = events(feed(ANNA_FEED));
        // Team 1 events: practice, match day, cancelled practice, «Teamessen», past ones of the last 30 days
        for (int nr : new int[] {1, 2, 3, 4, 6, 7, 9, 10}) {
            assertTrue(events.containsKey(event(nr)), "event " + nr);
        }
        // Match day 05 is Team 2 only, event 08 is in the archive season (May 2026)
        assertFalse(events.containsKey(event(5)));
        assertFalse(events.containsKey(event(8)));
        // "Now" is Mon 16.11.2026 06:00: Tue 20.10. is within 30 days, Thu 15.10. is not
        assertTrue(events.containsKey(seriesEvent(1, "2026-10-20")));
        assertFalse(events.containsKey(seriesEvent(2, "2026-10-15")));
        // Upcoming events until the end of the series
        assertTrue(events.containsKey(seriesEvent(2, "2027-04-29")));
        // Team 3 practice is not in Anna's feed
        assertFalse(events.containsKey(seriesEvent(3, "2026-11-18")));
    }

    @Test
    void cal02PersonWithoutTeamGetsAnEmptyCalendar() {
        String alex = login("alex");
        String path = given().cookie("stv_session", alex).get("/api/me/calendar").then().extract().path("feedPath");
        String feed = feed(path);
        assertTrue(feed.contains("BEGIN:VCALENDAR"));
        assertFalse(feed.contains("BEGIN:VEVENT"));
    }

    // ------------------------------------------------------------------ Criterion 9: format and one event field by field

    @Test
    void cal09FeedIsValidICalendar() throws Exception {
        Calendar calendar = new CalendarBuilder().build(new StringReader(feed(ANNA_FEED)));
        ValidationResult result = calendar.validate();
        assertFalse(result.hasErrors(), result.toString());
        List<VEvent> events = calendar.getComponents(Component.VEVENT);
        assertEquals(events(feed(ANNA_FEED)).size(), events.size());
        assertTrue(events.size() > 50);
    }

    @Test
    void cal09MatchDayFieldByField() throws Exception {
        Map<String, String> e = events(feed(ANNA_FEED)).get(event(4));
        assertEquals(event(4) + "@stvbero", e.get("UID"));
        assertEquals("20261116T050000Z", e.get("DTSTAMP"));
        assertEquals("20261122T093000", e.get("DTSTART;TZID=Europe/Zurich"));
        assertEquals("20261122T130000", e.get("DTEND;TZID=Europe/Zurich"));
        assertEquals("Spieltag · Team 1", e.get("SUMMARY"));
        assertEquals("Sporthalle Seefeld\\, Musterdorf", e.get("LOCATION"));
        assertEquals("CONFIRMED", e.get("STATUS"));
        assertEquals("http://localhost:4200/termine/" + event(4), e.get("URL"));
        String description = e.get("DESCRIPTION").replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";");
        assertTrue(description.startsWith("""
                Treffpunkt: 08:00, Parkplatz Sporthalle Nord
                Spiele:
                09:30 Team 1 – UHC Musterdorf
                11:45 Unihockey Beispielstadt – Team 1
                Notizen: Weisse Trikots mitnehmen
                Karte: https://"""), description);
        assertTrue(description.endsWith("""
                Deine Antwort: dabei
                In der App: http://localhost:4200/termine/40000000-0000-4000-8000-000000000004"""), description);

        // The same event read by ical4j: the time zone gives the right instant
        Calendar calendar = new CalendarBuilder().build(new StringReader(feed(ANNA_FEED)));
        VEvent parsed = calendar.<VEvent>getComponents(Component.VEVENT).stream()
                .filter(v -> v.getProperty(Property.UID).orElseThrow().getValue().equals(event(4) + "@stvbero"))
                .findFirst().orElseThrow();
        Temporal start = parsed.getDateTimeStart().getDate();
        assertEquals(ZonedDateTime.parse("2026-11-22T09:30+01:00[Europe/Zurich]").toInstant(),
                ZonedDateTime.from(start).toInstant());
        assertEquals("Spieltag · Team 1", parsed.getProperty(Property.SUMMARY).orElseThrow().getValue());
        assertEquals("Sporthalle Seefeld, Musterdorf", parsed.getProperty(Property.LOCATION).orElseThrow().getValue());
    }

    // ------------------------------------------------------------------ Criterion 4: cancelled and moved

    @Test
    void cal04CancelledEventIsDeliveredAsCancelled() {
        Map<String, String> e = events(feed(ANNA_FEED)).get(event(3));
        assertEquals("CANCELLED", e.get("STATUS"));
        assertEquals("Abgesagt: Training · Team 1 + Team 2", e.get("SUMMARY"));
        assertTrue(e.get("DESCRIPTION").startsWith("Abgesagt: Halle besetzt\\n"));

        String petra = login("petra");
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{\"reason\":\"Halle gesperrt\"}")
                .post("/api/events/" + event(2) + "/cancel").then().statusCode(200);
        assertEquals("CANCELLED", events(feed(ANNA_FEED)).get(event(2)).get("STATUS"));
    }

    @Test
    void cal04MovedEventKeepsItsUid() {
        String before = feed(ANNA_FEED);
        String body = """
                {"type":"PRACTICE","title":"Training","start":"2026-11-20T19:00:00+01:00","end":"2026-11-20T20:30:00+01:00",
                 "teamIds":["%s"],"location":{"name":"Sporthalle Nord"}}""".formatted(TEAM_1);
        given().cookie("stv_session", login("petra")).contentType(ContentType.JSON).body(body)
                .put("/api/events/" + event(2)).then().statusCode(200);
        String after = feed(ANNA_FEED);
        assertEquals(events(before).keySet(), events(after).keySet());
        assertEquals("20261119T190000", events(before).get(event(2)).get("DTSTART;TZID=Europe/Zurich"));
        assertEquals("20261120T190000", events(after).get(event(2)).get("DTSTART;TZID=Europe/Zurich"));
        assertEquals(1, after.split("UID:" + event(2), -1).length - 1);
    }

    @Test
    void ter31EntryOfAPracticeWithPreTrainingBeginsWithIt() {
        Map<String, String> before = events(feed(ANNA_FEED)).get(event(2));
        assertEquals("20261119T190000", before.get("DTSTART;TZID=Europe/Zurich"));
        assertFalse(before.get("DESCRIPTION").contains("Vortraining"));

        String body = """
                {"type":"PRACTICE","title":"Training","start":"2026-11-19T19:00:00+01:00","end":"2026-11-19T20:30:00+01:00",
                 "teamIds":["%s"],"location":{"name":"Sporthalle Nord"},
                 "preTraining":{"title":"Joggen","start":"2026-11-18T18:40:00+01:00"}}""".formatted(TEAM_1);
        // Another day is refused, the same day is taken
        given().cookie("stv_session", login("petra")).contentType(ContentType.JSON).body(body)
                .put("/api/events/" + event(2)).then().statusCode(400);
        given().cookie("stv_session", login("petra")).contentType(ContentType.JSON)
                .body(body.replace("2026-11-18T18:40", "2026-11-19T18:40"))
                .put("/api/events/" + event(2)).then().statusCode(200);
        Map<String, String> after = events(feed(ANNA_FEED)).get(event(2));
        assertEquals("20261119T184000", after.get("DTSTART;TZID=Europe/Zurich"));
        assertEquals("20261119T203000", after.get("DTEND;TZID=Europe/Zurich"));
        assertTrue(after.get("DESCRIPTION").startsWith("Vortraining 18:40: Joggen · Training 19:00–20:30\\n"),
                after.get("DESCRIPTION"));
    }

    // ------------------------------------------------------------------ Criterion 5: daylight saving time

    @Test
    void cal05TimesAcrossDaylightSavingChanges() {
        Map<String, Map<String, String>> events = events(feed(ANNA_FEED));
        // Before and after the change on 25.10.2026 and on 28.03.2027: always 18:30 local time
        assertEquals("20261020T183000", events.get(seriesEvent(1, "2026-10-20")).get("DTSTART;TZID=Europe/Zurich"));
        assertEquals("20261027T183000", events.get(seriesEvent(1, "2026-10-27")).get("DTSTART;TZID=Europe/Zurich"));
        assertEquals("20270323T183000", events.get(seriesEvent(1, "2027-03-23")).get("DTSTART;TZID=Europe/Zurich"));
        assertEquals("20270330T183000", events.get(seriesEvent(1, "2027-03-30")).get("DTSTART;TZID=Europe/Zurich"));
    }

    // ------------------------------------------------------------------ Criteria 6 and 7: token

    @Test
    void cal06UnknownTokenIs404AndTokenIsNoLogin() {
        given().get("/api/calendar/" + "x".repeat(43) + ".ics").then().statusCode(404)
                .contentType(startsWith("application/problem+json"));
        given().get("/api/calendar/kurz.ics").then().statusCode(404);
        // The token only opens the feed, it is not a session
        given().cookie("stv_session", Seed.ANNA_CALENDAR_TOKEN).get("/api/me").then().statusCode(401);
        given().cookie("stv_session", Seed.ANNA_CALENDAR_TOKEN).get("/api/me/calendar").then().statusCode(401);
    }

    @Test
    void cal07RenewedAddressReplacesTheOldOneAtOnce() {
        String anna = login("anna");
        String renewed = given().cookie("stv_session", anna).post("/api/me/calendar/renew").then().statusCode(200)
                .extract().path("feedPath");
        assertNotEquals(ANNA_FEED, renewed);
        given().get(ANNA_FEED).then().statusCode(404);
        assertTrue(feed(renewed).contains("UID:" + event(1)));
        given().cookie("stv_session", anna).get("/api/me/calendar").then().body("feedPath", equalTo(renewed));
        given().post("/api/me/calendar/renew").then().statusCode(401);
    }

    // ------------------------------------------------------------------ Criterion 8: nothing of other people

    @Test
    void cal08NoCommentsOrResponsesOfOthers() {
        String feed = feed(ANNA_FEED);
        String unfolded = feed.replace("\r\n ", "");
        // Comments of Mia, Eva and Marc for events in Anna's feed
        for (String foreign : List.of("Muss evtl. arbeiten", "Komme evtl. später", "Ferien", "Knie schmerzt",
                "Komme später", "Mia", "Lea", "Petra", "Jana")) {
            assertFalse(unfolded.contains(foreign), foreign);
        }
        Map<String, Map<String, String>> events = events(feed);
        // Mia said «Krank» for event 01; Anna's own response there is «dabei»
        assertFalse(events.get(event(1)).get("DESCRIPTION").contains("Krank"));
        assertTrue(events.get(event(1)).get("DESCRIPTION").contains("Deine Antwort: dabei"));
        // Anna's own comment for event 07
        assertTrue(events.get(event(7)).get("DESCRIPTION").contains("Deine Antwort: nicht dabei («Krank»)"));
        assertTrue(events.get(event(6)).get("DESCRIPTION").contains("Deine Antwort: offen"));
        List<String> descriptions = new ArrayList<>();
        events.values().forEach(e -> descriptions.add(e.get("DESCRIPTION")));
        assertTrue(descriptions.stream().noneMatch(d -> d.contains("eingetragen von")));
    }
}
