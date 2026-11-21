package ch.stvbero.platform.core.driver;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Driver plan of a team for a whole season (DRV-14, DRV-17), against the seed of docs/test-data.md: Team 1 has
 * match day 04 with Petra's full car and Sara's empty car. Default "now": Mon 16.11.2026 06:00.
 */
@QuarkusTest
class DriverPlanTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String SEASON_2025 = "60000000-0000-4000-8000-000000000001";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String KIM = "20000000-0000-4000-8000-000000000011";
    static final String EVA = "20000000-0000-4000-8000-000000000012";
    static final String T04 = "40000000-0000-4000-8000-000000000004";
    static final String T05 = "40000000-0000-4000-8000-000000000005";

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static ValidatableResponse plan(String session, String team) {
        return given().cookie("stv_session", session).get("/api/teams/" + team + "/driver-plan").then();
    }

    private static ValidatableResponse enter(String session, String driver, int seats, String... events) {
        String driverId = driver == null ? "" : "\"driverId\":\"" + driver + "\",";
        String ids = String.join(",", List.of(events).stream().map(id -> "\"" + id + "\"").toList());
        return given().cookie("stv_session", session).contentType(ContentType.JSON)
                .body("{" + driverId + "\"seats\":" + seats + ",\"eventIds\":[" + ids + "]}")
                .post("/api/teams/" + TEAM_1 + "/driver-plan/cars").then();
    }

    /** A further match day of Team 1 on a Sunday in December, with or without a meeting point. */
    private static String createMatchDay(String session, String day, String meetingPoint) {
        String mp = meetingPoint == null ? "" : ",\"meetingPoint\":{\"time\":null,\"location\":\"" + meetingPoint + "\"}";
        String body = """
                {"type":"MATCH_DAY","title":"Spieltag","start":"%sT10:00:00+01:00","end":"%sT14:00:00+01:00",
                 "teamIds":["%s"],"location":{"name":"Sporthalle Seefeld"}%s}""".formatted(day, day, TEAM_1, mp);
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .post("/api/events").then().statusCode(201).extract().path("id");
    }

    private static Map<String, Object> share(JsonPath plan, String firstName) {
        return plan.getMap("shares.find { it.person.firstName == '" + firstName + "' }");
    }

    @Test
    void drv14PlanShowsEveryMatchDayOfTheSeasonWithItsCars() {
        String petra = login("petra");
        String december = createMatchDay(petra, "2026-12-06", "Parkplatz Sporthalle Nord");
        String noMeetingPoint = createMatchDay(petra, "2026-12-13", null);

        JsonPath json = plan(login("anna"), TEAM_1).statusCode(200).extract().jsonPath();
        assertEquals("Team 1", json.getString("teamName"));
        assertEquals("2026/27", json.getString("seasonName"));
        assertEquals(true, json.getBoolean("canJoin"));
        assertEquals(false, json.getBoolean("canManage"));
        assertThat(json.get("members"), nullValue());
        assertThat(json.getList("matchDays.eventId"), contains(T04, december, noMeetingPoint));
        // Match day 04 as in its own driver list
        assertEquals("Sporthalle Seefeld", json.getString("matchDays[0].venue"));
        assertEquals("Musterdorf", json.getString("matchDays[0].town"));
        assertThat(json.getList("matchDays[0].cars.driver.firstName"), contains("Sara", "Petra"));
        assertThat(json.getList("matchDays[0].cars.find { it.driver.firstName == 'Petra' }.passengers.firstName"),
                contains("Mia", "Lea", "Anna"));
        assertEquals(3, json.getInt("matchDays[0].freeSeats"));
        assertEquals(23.4, json.getDouble("matchDays[0].distanceKm"));
        assertEquals(26, json.getInt("matchDays[0].durationMinutes"));
        assertEquals(true, json.getBoolean("matchDays[0].changeable"));
        // Without a meeting point no driver can be entered yet (DRV-01)
        assertEquals(false, json.getBoolean("matchDays[2].ready"));
        assertThat(json.getList("matchDays[2].missing"), contains("MEETING_POINT"));
        assertEquals(false, json.getBoolean("matchDays[2].changeable"));
        assertThat(json.getList("matchDays[2].cars"), hasSize(0));
    }

    @Test
    void drv14OnePersonEntersHerselfForSeveralMatchDaysAtOnce() {
        String petra = login("petra");
        String december = createMatchDay(petra, "2026-12-06", "Parkplatz Sporthalle Nord");
        String january = createMatchDay(petra, "2027-01-10", "Parkplatz Sporthalle Nord");
        String kim = login("kim");

        JsonPath json = enter(kim, null, 4, T04, december, january).statusCode(200).extract().jsonPath();
        assertThat(json.getList("matchDays[0].cars.driver.firstName"), contains("Kim", "Sara", "Petra"));
        assertThat(json.getList("matchDays[1].cars.driver.firstName"), contains("Kim"));
        assertThat(json.getList("matchDays[2].cars.driver.firstName"), contains("Kim"));
        assertEquals(4, json.getInt("matchDays[1].cars[0].seats"));

        // Entering again leaves the match days she already drives on as they are, and adds nothing twice
        enter(kim, null, 2, T04, december).statusCode(200)
                .body("matchDays[1].cars", hasSize(1)).body("matchDays[1].cars[0].seats", equalTo(4));
        // The match day itself shows the car too
        given().cookie("stv_session", kim).get("/api/events/" + december + "/driver-list").then().statusCode(200)
                .body("cars.driver.firstName", contains("Kim"));
    }

    @Test
    void drv14CoachPlansForOthersAndMembersOnlyForThemselves() {
        String petra = login("petra");
        String december = createMatchDay(petra, "2026-12-06", "Parkplatz Sporthalle Nord");

        plan(petra, TEAM_1).statusCode(200).body("canManage", equalTo(true))
                .body("members.firstName", hasSize(8));
        enter(petra, ANNA, 3, december).statusCode(200)
                .body("matchDays[1].cars.driver.firstName", contains("Anna"));
        // Anna cannot enter Kim (RR-07), and Eva of Team 2 cannot drive for Team 1
        enter(login("anna"), KIM, 3, december).statusCode(403);
        enter(petra, EVA, 3, december).statusCode(422).body("rule", equalTo("DRV-02"));
        // Anna sits in Petra's car on match day 04: entering her as driver there moves her out of the seat
        enter(petra, ANNA, 3, T04).statusCode(200);
        given().cookie("stv_session", petra).get("/api/events/" + T04 + "/driver-list").then()
                .body("cars.find { it.driver.firstName == 'Petra' }.passengers.firstName", contains("Mia", "Lea"));
    }

    @Test
    void drv14IfOneMatchDayRefusesNothingIsEntered() {
        String petra = login("petra");
        String december = createMatchDay(petra, "2026-12-06", "Parkplatz Sporthalle Nord");
        String noMeetingPoint = createMatchDay(petra, "2026-12-13", null);

        enter(login("kim"), null, 3, december, noMeetingPoint).statusCode(409).body("rule", equalTo("DRV-01"));
        plan(petra, TEAM_1).body("matchDays[1].cars", hasSize(0));
        // A match day of another team is not part of this plan
        enter(petra, null, 3, T05).statusCode(400).body("fields[0].field", equalTo("eventIds"));
        enter(petra, null, 9, december).statusCode(400);
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{\"seats\":3,\"eventIds\":[]}")
                .post("/api/teams/" + TEAM_1 + "/driver-plan/cars").then().statusCode(400);
    }

    @Test
    void drv17SharesShowDoneAndPlannedTripsOfEveryMember() {
        String petra = login("petra");
        String december = createMatchDay(petra, "2026-12-06", "Parkplatz Sporthalle Nord");
        enter(petra, null, 3, december).statusCode(200);

        // Upcoming: Petra drives twice, Sara once; the route of match day 04 is 23.4 km one way
        JsonPath json = plan(login("anna"), TEAM_1).statusCode(200).extract().jsonPath();
        assertThat(json.getList("shares.person.firstName"),
                contains("Kim", "Sara", "Mia", "Lea", "Petra", "Anna", "Nina", "Jana"));
        assertEquals(2, share(json, "Petra").get("plannedTrips"));
        assertEquals(0, share(json, "Petra").get("trips"));
        // Both match days are 23.4 km away: twice the return trip of 46.8 km
        assertEquals(93.6f, share(json, "Petra").get("plannedKilometres"));
        assertEquals(1, share(json, "Sara").get("plannedTrips"));
        assertEquals(0, share(json, "Anna").get("plannedTrips"));

        // After match day 04 its trips count as done
        given().contentType(ContentType.JSON).body("{\"now\":\"2026-11-23T08:00:00+01:00\"}").put("/api/test/clock")
                .then().statusCode(204);
        JsonPath later = plan(login("anna"), TEAM_1).statusCode(200).extract().jsonPath();
        assertEquals(1, share(later, "Petra").get("trips"));
        assertEquals(1, share(later, "Petra").get("plannedTrips"));
        assertEquals(46.8f, share(later, "Petra").get("kilometres"));
        assertEquals(1, share(later, "Sara").get("trips"));
        assertEquals(true, later.getBoolean("matchDays[0].over"));
        // After the end only coaches and admins change the list (DRV-02)
        assertEquals(false, later.getBoolean("matchDays[0].changeable"));
        plan(petra, TEAM_1).body("matchDays[0].changeable", equalTo(true));
    }

    @Test
    void drv14PlanIsForTheTeamCoachesAndAdmins() {
        // Eva plays only in Team 2
        plan(login("eva"), TEAM_1).statusCode(403);
        plan(login("marc"), TEAM_1).statusCode(200).body("canManage", equalTo(true)).body("canJoin", equalTo(false));
        plan(login("alex"), TEAM_2).statusCode(200).body("matchDays.eventId", contains(T05));
        given().get("/api/teams/" + TEAM_1 + "/driver-plan").then().statusCode(401);
        plan(login("alex"), "10000000-0000-4000-8000-000000000999").statusCode(404);
        // The archive can be read, but not changed
        given().cookie("stv_session", login("alex")).queryParam("season", SEASON_2025)
                .get("/api/teams/" + TEAM_1 + "/driver-plan").then().statusCode(200)
                .body("seasonName", equalTo("2025/26")).body("canManage", equalTo(false))
                .body("matchDays", hasSize(0));
    }
}
