package ch.stvbero.platform.driver;

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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Match days left out of the driver list (DRV-35), against the seed of docs/test-data.md: Team 1 has match day 04
 * with Petra's and Sara's car; every test adds a match day in December without cars. Default "now": Mon 16.11.2026.
 */
@QuarkusTest
class DriverPlanIgnoredTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String T04 = "40000000-0000-4000-8000-000000000004";
    static final String PLAN = "/api/teams/" + TEAM_1 + "/driver-plan";

    String december;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
        String body = """
                {"type":"MATCH_DAY","title":"Spieltag","start":"2026-12-06T10:00:00+01:00","end":"2026-12-06T14:00:00+01:00",
                 "teamIds":["%s"],"location":{"name":"Sporthalle Seefeld"},
                 "meetingPoint":{"time":null,"location":"Parkplatz Sporthalle Nord"}}""".formatted(TEAM_1);
        december = given().cookie("stv_session", login("petra")).contentType(ContentType.JSON).body(body)
                .post("/api/events").then().statusCode(201).extract().path("id");
    }

    private static JsonPath plan(String session) {
        return given().cookie("stv_session", session).get(PLAN).then().statusCode(200).extract().jsonPath();
    }

    private static ValidatableResponse ignore(String session, String event, Object ignored) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON)
                .body("{\"ignored\":" + ignored + "}").put(PLAN + "/match-days/" + event + "/ignored").then();
    }

    private static Map<String, Object> matchDay(JsonPath plan, String id) {
        return plan.getMap("matchDays.find { it.eventId == '" + id + "' }");
    }

    private static int trips(JsonPath plan, String firstName) {
        Map<String, Object> share = plan.getMap("shares.find { it.person.firstName == '" + firstName + "' }");
        return (int) share.get("trips") + (int) share.get("plannedTrips");
    }

    @Test
    void drv35AnIgnoredMatchDayCountsNoTripsAndCanBeTakenInAgain() {
        String petra = login("petra");
        assertEquals(1, trips(plan(petra), "Petra"));
        int total = plan(petra).getInt("totals.plannedTrips");

        JsonPath json = ignore(petra, T04, true).statusCode(200).extract().jsonPath();
        assertEquals(true, matchDay(json, T04).get("ignored"));
        assertEquals(false, matchDay(json, T04).get("changeable"));
        // The cars stay, but count nothing
        assertEquals(2, json.getList("matchDays.find { it.eventId == '" + T04 + "' }.cars").size());
        assertEquals(0, trips(json, "Petra"));
        assertEquals(total - 2, json.getInt("totals.plannedTrips"));

        json = ignore(petra, T04, false).statusCode(200).extract().jsonPath();
        assertEquals(false, matchDay(json, T04).get("ignored"));
        assertEquals(1, trips(json, "Petra"));
    }

    @Test
    void drv35FairDistributionSkipsAnIgnoredMatchDay() {
        String petra = login("petra");
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{\"expected\":10}")
                .put(PLAN + "/expected").then().statusCode(200);
        ignore(petra, december, true).statusCode(200);

        List<String> events = given().cookie("stv_session", petra).post(PLAN + "/distribute/preview").then()
                .statusCode(200).extract().jsonPath().getList("trips.eventId");
        assertFalse(events.isEmpty());
        assertFalse(events.contains(december));

        // Trips on the ignored match day 04 do not count: Sara (Brun, first by name) is in line again for December,
        // although she drives on match day 04 (without ignoring, December goes to others, DriverPlanSimpleTest)
        ignore(petra, december, false).statusCode(200);
        ignore(petra, T04, true).statusCode(200);
        JsonPath proposal = given().cookie("stv_session", petra).post(PLAN + "/distribute/preview").then()
                .statusCode(200).extract().jsonPath();
        assertFalse(proposal.getList("trips.eventId").contains(T04));
        assertTrue(proposal.getList("trips.findAll { it.eventId == '" + december + "' }.driver.firstName")
                .contains("Sara"));
    }

    @Test
    void drv35NoDriversAreEnteredOnAnIgnoredMatchDay() {
        String petra = login("petra");
        ignore(petra, december, true).statusCode(200);

        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{}")
                .post(PLAN + "/match-days/" + december + "/next").then().statusCode(409);
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"seats\":4,\"eventIds\":[\"" + december + "\"]}").post(PLAN + "/cars").then().statusCode(409);
        assertTrue(plan(petra).getList("matchDays.find { it.eventId == '" + december + "' }.cars").isEmpty());
    }

    @Test
    void drv35RemoveAllKeepsTheCarsOfAnIgnoredMatchDay() {
        String petra = login("petra");
        ignore(petra, T04, true).statusCode(200);
        JsonPath json = given().cookie("stv_session", petra).delete(PLAN + "/cars").then().statusCode(200)
                .extract().jsonPath();
        assertEquals(2, json.getList("matchDays.find { it.eventId == '" + T04 + "' }.cars").size());
    }

    @Test
    void drv35OnlyCoachesAndAdminsLeaveOutAMatchDay() {
        ignore(login("anna"), T04, true).statusCode(403);
        String petra = login("petra");
        ignore(petra, T04, null).statusCode(400);
        ignore(petra, "40000000-0000-4000-8000-000000000001", true).statusCode(404);
    }
}
