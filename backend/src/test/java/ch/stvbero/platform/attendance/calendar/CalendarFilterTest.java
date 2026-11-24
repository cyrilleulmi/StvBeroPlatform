package ch.stvbero.platform.attendance.calendar;

import ch.stvbero.platform.attendance.event.EventType;
import ch.stvbero.platform.attendance.event.Participation;
import ch.stvbero.platform.attendance.event.ResponseStatus;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static ch.stvbero.platform.attendance.calendar.CalendarTest.ANNA_FEED;
import static ch.stvbero.platform.attendance.calendar.CalendarTest.TEAM_1;
import static ch.stvbero.platform.attendance.calendar.CalendarTest.event;
import static ch.stvbero.platform.attendance.calendar.CalendarTest.events;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Filters of the calendar feed (CAL-10 to CAL-15) with the seed of docs/test-data.md. */
@QuarkusTest
class CalendarFilterTest {

    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static Map<String, Map<String, String>> feed(String path) {
        return events(given().get(path).then().statusCode(200).extract().asString());
    }

    private static void change(String session, String body) {
        given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .put("/api/me/calendar/filter").then().statusCode(200);
    }

    // ------------------------------------------------------------------ Rules without database

    @Test
    void participationFollowsResponseOptionalTeamAndCallUp() {
        assertNull(CalendarFilter.participation(ResponseStatus.ATTENDING, Participation.EXPECTED, null));
        assertEquals(CalendarResponse.PENDING, CalendarFilter.participation(null, Participation.EXPECTED, null));
        assertEquals(CalendarResponse.OPTIONAL_PENDING,
                CalendarFilter.participation(ResponseStatus.PENDING, Participation.OPTIONAL, null));
        assertEquals(CalendarResponse.MAYBE,
                CalendarFilter.participation(ResponseStatus.MAYBE, Participation.OPTIONAL, null));
        assertEquals(CalendarResponse.DECLINED,
                CalendarFilter.participation(ResponseStatus.DECLINED, Participation.EXPECTED, null));
        // Published call-up (CAL-12): called up counts as «dabei» unless she cancelled, not called up as declined
        assertNull(CalendarFilter.participation(null, Participation.EXPECTED, true));
        assertNull(CalendarFilter.participation(ResponseStatus.MAYBE, Participation.EXPECTED, true));
        assertEquals(CalendarResponse.DECLINED,
                CalendarFilter.participation(ResponseStatus.DECLINED, Participation.EXPECTED, true));
        assertEquals(CalendarResponse.DECLINED,
                CalendarFilter.participation(ResponseStatus.ATTENDING, Participation.EXPECTED, false));
    }

    @Test
    void includesChecksTypeTeamsAndParticipation() {
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        CalendarFilter onlyAttending = new CalendarFilter(EnumSet.of(EventType.MATCH_DAY),
                EnumSet.noneOf(CalendarResponse.class), Set.of(t2));
        assertTrue(onlyAttending.includes(EventType.MATCH_DAY, List.of(t1), null));
        assertFalse(onlyAttending.includes(EventType.PRACTICE, List.of(t1), null));
        assertFalse(onlyAttending.includes(EventType.MATCH_DAY, List.of(t1), CalendarResponse.PENDING));
        assertFalse(onlyAttending.includes(EventType.MATCH_DAY, List.of(t2), null));
        // One team of the person in the event is not excluded: the event stays
        assertTrue(onlyAttending.includes(EventType.MATCH_DAY, List.of(t1, t2), null));
        assertTrue(CalendarFilter.all().includes(EventType.OTHER, List.of(t1), CalendarResponse.OPTIONAL_PENDING));
    }

    // ------------------------------------------------------------------ API and feed

    @Test
    void cal14NewAddressDeliversEverythingAndShowsTheOwnTeams() {
        given().cookie("stv_session", login("lea")).get("/api/me/calendar").then().statusCode(200)
                .body("filter.types", contains("PRACTICE", "MATCH_DAY", "OTHER"))
                .body("filter.responses", containsInAnyOrder("PENDING", "OPTIONAL_PENDING", "MAYBE", "DECLINED"))
                .body("filter.teams.teamId", contains(TEAM_1, TEAM_2))
                .body("filter.teams.included", contains(true, true));
    }

    @Test
    void cal11OnlyAttendingAndPending() {
        String anna = login("anna");
        change(anna, """
                {"types":["PRACTICE","MATCH_DAY","OTHER"],"responses":["PENDING"],"excludedTeamIds":[]}""");
        Map<String, Map<String, String>> events = feed(ANNA_FEED);
        assertTrue(events.containsKey(event(1)), "01 «dabei»");
        assertTrue(events.containsKey(event(4)), "04 «dabei», call-up only in draft");
        assertTrue(events.containsKey(event(6)), "06 no response yet");
        assertFalse(events.containsKey(event(7)), "07 «nicht dabei»");
        // The address stays the same (CAL-10)
        given().cookie("stv_session", anna).get("/api/me/calendar").then()
                .body("feedPath", equalTo(ANNA_FEED)).body("filter.responses", contains("PENDING"));
    }

    @Test
    void cal11OnlyAttending() {
        change(login("anna"), """
                {"types":["PRACTICE","MATCH_DAY","OTHER"],"responses":[],"excludedTeamIds":[]}""");
        Map<String, Map<String, String>> events = feed(ANNA_FEED);
        assertTrue(events.containsKey(event(1)));
        assertTrue(events.containsKey(event(4)));
        assertFalse(events.containsKey(event(6)));
        assertFalse(events.containsKey(event(7)));
    }

    @Test
    void cal10OnlyMatchDays() {
        change(login("anna"), """
                {"types":["MATCH_DAY"],"responses":["PENDING","MAYBE","DECLINED","OPTIONAL_PENDING"],"excludedTeamIds":[]}""");
        Map<String, Map<String, String>> events = feed(ANNA_FEED);
        assertTrue(events.containsKey(event(4)));
        assertFalse(events.containsKey(event(1)));
        assertFalse(events.containsKey(event(6)));
        events.values().forEach(e -> assertTrue(e.get("SUMMARY").startsWith("Spieltag")
                || e.get("SUMMARY").startsWith("Abgesagt: Spieltag"), e.get("SUMMARY")));
    }

    @Test
    void cal10AtLeastOneType() {
        given().cookie("stv_session", login("anna")).contentType(ContentType.JSON)
                .body("{\"types\":[],\"responses\":[],\"excludedTeamIds\":[]}")
                .put("/api/me/calendar/filter").then().statusCode(422)
                .body("rule", equalTo("CAL-10")).body("fields[0].field", equalTo("types"));
    }

    @Test
    void cal12NotCalledUpCountsAsDeclined() {
        String lea = login("lea");
        String path = given().cookie("stv_session", lea).get("/api/me/calendar").then().extract().path("feedPath");
        // Match day 05 (Team 2): Lea responded «dabei» but is not called up in the published call-up
        assertTrue(feed(path).containsKey(event(5)));
        change(lea, """
                {"types":["PRACTICE","MATCH_DAY","OTHER"],"responses":["PENDING","MAYBE"],"excludedTeamIds":[]}""");
        assertFalse(feed(path).containsKey(event(5)));
        assertTrue(feed(path).containsKey(event(1)));
    }

    @Test
    void cal13ExcludedTeam() {
        String lea = login("lea");
        String path = given().cookie("stv_session", lea).get("/api/me/calendar").then().extract().path("feedPath");
        given().cookie("stv_session", lea).contentType(ContentType.JSON).body("""
                        {"types":["PRACTICE","MATCH_DAY","OTHER"],
                         "responses":["PENDING","MAYBE","DECLINED","OPTIONAL_PENDING"],
                         "excludedTeamIds":["%s","%s"]}""".formatted(TEAM_2, UUID.randomUUID()))
                .put("/api/me/calendar/filter").then().statusCode(200)
                .body("filter.teams.included", contains(true, false));
        Map<String, Map<String, String>> events = feed(path);
        assertFalse(events.containsKey(event(5)), "Team 2 only");
        assertTrue(events.containsKey(event(1)), "Team 1 + Team 2: Lea is still in Team 1");
        assertTrue(events.containsKey(event(4)), "Team 1");
    }

    @Test
    void cal15RenewingKeepsTheFilter() {
        String anna = login("anna");
        change(anna, """
                {"types":["MATCH_DAY"],"responses":[],"excludedTeamIds":[]}""");
        given().cookie("stv_session", anna).post("/api/me/calendar/renew").then().statusCode(200)
                .body("filter.types", contains("MATCH_DAY")).body("filter.responses.size()", equalTo(0));
    }
}
