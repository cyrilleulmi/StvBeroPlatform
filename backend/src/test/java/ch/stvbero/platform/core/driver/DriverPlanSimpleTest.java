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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simple planning of a season (DRV-29, DRV-30), against the seed of docs/test-data.md: Team 1 has match day 04
 * with Petra's full car and Sara's empty car; every test adds a match day in December without cars. Default "now":
 * Mon 16.11.2026 06:00.
 */
@QuarkusTest
class DriverPlanSimpleTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String T04 = "40000000-0000-4000-8000-000000000004";
    static final String PLAN = "/api/teams/" + TEAM_1 + "/driver-plan";
    static final String NINA = "20000000-0000-4000-8000-000000000009";

    /** A further match day of Team 1 on Sun 06.12.2026, ready for drivers. */
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

    private static ValidatableResponse put(String session, String path, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body).put(PLAN + path).then();
    }

    private static ValidatableResponse preview(String session) {
        return given().cookie("stv_session", session).post(PLAN + "/distribute/preview").then();
    }

    /** Proposes a distribution and enters it as proposed. */
    private static ValidatableResponse distribute(String session) {
        List<Map<String, Object>> trips = preview(session).statusCode(200).extract().jsonPath().getList("trips");
        return enter(session, trips);
    }

    private static ValidatableResponse enter(String session, List<Map<String, Object>> trips) {
        StringBuilder body = new StringBuilder("{\"trips\":[");
        for (int i = 0; i < trips.size(); i++) {
            Map<String, Object> trip = trips.get(i);
            @SuppressWarnings("unchecked")
            Map<String, Object> driver = (Map<String, Object>) trip.get("driver");
            body.append(i == 0 ? "" : ",").append("{\"eventId\":\"").append(trip.get("eventId"))
                    .append("\",\"driverId\":\"").append(driver.get("id")).append("\",\"seats\":")
                    .append(trip.get("seats")).append("}");
        }
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body.append("]}").toString())
                .post(PLAN + "/distribute").then();
    }

    private static List<String> texts(String firstName) {
        return given().cookie("stv_session", login(firstName)).get("/api/notifications").then()
                .statusCode(200).extract().jsonPath().getList("findAll { it.type == 'DRIVER_LIST_CHANGED' }.text");
    }

    private static Map<String, Object> matchDay(JsonPath plan, String id) {
        return plan.getMap("matchDays.find { it.eventId == '" + id + "' }");
    }

    private static List<String> drivers(JsonPath plan, String id) {
        return plan.getList("matchDays.find { it.eventId == '" + id + "' }.cars.driver.firstName");
    }

    @Test
    void drv29ByDefaultEveryPlayerAndCoachIsExpected() {
        JsonPath json = plan(login("petra"));
        int members = json.getList("shares").size();

        assertEquals(members, json.getInt("expected"));
        assertEquals(members, json.getInt("expectedDefault"));
        assertFalse(json.getBoolean("expectedManual"));
        assertEquals(members, matchDay(json, december).get("expected"));
        // Petra's car has three seats, Sara's three: eight places with the two drivers
        assertEquals(8, matchDay(json, T04).get("places"));
        assertEquals(0, matchDay(json, december).get("places"));
    }

    @Test
    void drv29CoachSetsTheNumberForTheTeamAndForOneMatchDay() {
        String petra = login("petra");
        put(petra, "/expected", "{\"expected\":9}").statusCode(200);
        JsonPath json = put(petra, "/match-days/" + december + "/expected", "{\"expected\":6}").statusCode(200)
                .extract().jsonPath();

        assertEquals(9, json.getInt("expected"));
        assertTrue(json.getBoolean("expectedManual"));
        assertEquals(9, matchDay(json, T04).get("expected"));
        assertEquals(6, matchDay(json, december).get("expected"));
        assertEquals(true, matchDay(json, december).get("expectedManual"));

        // Without a number the defaults apply again
        put(petra, "/match-days/" + december + "/expected", "{\"expected\":null}").statusCode(200);
        json = put(petra, "/expected", "{\"expected\":null}").statusCode(200).extract().jsonPath();
        assertEquals(json.getInt("expectedDefault"), json.getInt("expected"));
        assertEquals(json.getInt("expectedDefault"), matchDay(json, december).get("expected"));
    }

    @Test
    void drv29OnlyCoachesAndAdminsSetTheNumberAndItHasLimits() {
        put(login("anna"), "/expected", "{\"expected\":9}").statusCode(403);
        String petra = login("petra");
        put(petra, "/expected", "{\"expected\":0}").statusCode(400);
        put(petra, "/expected", "{\"expected\":61}").statusCode(400);
        put(petra, "/match-days/40000000-0000-4000-8000-000000000001/expected", "{\"expected\":5}").statusCode(404);
    }

    @Test
    void drv30FairDistributionIsAProposalFirst() {
        String petra = login("petra");
        put(petra, "/expected", "{\"expected\":10}").statusCode(200);
        JsonPath proposal = preview(petra).statusCode(200).extract().jsonPath();

        // Match day 04 keeps Petra and Sara (eight places) and needs one more car; December needs two
        List<String> events = proposal.getList("trips.eventId");
        assertEquals(1, events.stream().filter(T04::equals).count());
        assertEquals(2, events.stream().filter(december::equals).count());
        // Petra and Sara already drive once, so December goes to others
        List<String> inDecember = proposal.getList("trips.findAll { it.eventId == '" + december + "' }.driver.firstName");
        assertFalse(inDecember.contains("Petra"));
        assertFalse(inDecember.contains("Sara"));
        // Nothing is entered yet
        assertEquals(java.util.Set.of("Petra", "Sara"), java.util.Set.copyOf(drivers(plan(petra), T04)));
        assertTrue(drivers(plan(petra), december).isEmpty());
    }

    @Test
    void drv30MatchDayNamesTheCreditOfOneTripForTheProposal() {
        String petra = login("petra");
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{\"distanceKm\":23.4}")
                .put("/api/events/" + december + "/driver-list/route").then().statusCode(200);
        JsonPath json = plan(petra);

        // The page adds this to the kilometres of a proposed driver: there and back, like an entered trip (DRV-05)
        assertEquals(23.4f, matchDay(json, december).get("distanceKm"));
        assertEquals(46.8f, matchDay(json, december).get("creditKm"));
    }

    @Test
    void drv30EnteredTripsStayAndALaterDistributionOnlyAdds() {
        String petra = login("petra");
        put(petra, "/expected", "{\"expected\":10}").statusCode(200);
        JsonPath json = distribute(petra).statusCode(200).extract().jsonPath();

        List<String> day04 = drivers(json, T04);
        assertEquals(3, day04.size());
        assertTrue(day04.containsAll(List.of("Petra", "Sara")));
        assertEquals(2, drivers(json, december).size());
        assertTrue((int) matchDay(json, T04).get("places") >= 10);
        assertTrue((int) matchDay(json, december).get("places") >= 10);
        // Nobody drives twice while others have not driven
        List<Integer> planned = json.getList("shares.plannedTrips");
        assertTrue(planned.stream().allMatch(n -> n <= 1));

        // Nothing is missing: the next proposal is empty, and fewer people expected removes nobody
        assertTrue(preview(petra).statusCode(200).extract().jsonPath().getList("trips").isEmpty());
        put(petra, "/expected", "{\"expected\":4}").statusCode(200);
        assertTrue(preview(petra).statusCode(200).extract().jsonPath().getList("trips").isEmpty());
        JsonPath after = plan(petra);
        assertEquals(day04, drivers(after, T04));
        assertEquals(drivers(json, december), drivers(after, december));

        // More people: only the missing trips are added
        put(petra, "/expected", "{\"expected\":16}").statusCode(200);
        JsonPath more = distribute(petra).statusCode(200).extract().jsonPath();
        assertTrue(drivers(more, T04).containsAll(day04));
        assertTrue(drivers(more, december).containsAll(drivers(json, december)));
    }

    @Test
    void drv30AProposalThatNoLongerFitsIsRefused() {
        String petra = login("petra");
        put(petra, "/expected", "{\"expected\":10}").statusCode(200);
        List<Map<String, Object>> trips = preview(petra).statusCode(200).extract().jsonPath().getList("trips");
        enter(petra, trips).statusCode(200);
        // The same trips once more: the drivers already drive, nothing is entered twice
        enter(petra, trips).statusCode(409).body("rule", org.hamcrest.Matchers.equalTo("DRV-30"));
        assertEquals(3, drivers(plan(petra), T04).size());
        // A match day that is over cannot be planned
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"trips\":[{\"eventId\":\"40000000-0000-4000-8000-000000000001\",\"driverId\":\"" + NINA
                        + "\",\"seats\":3}]}")
                .post(PLAN + "/distribute").then().statusCode(409);
        // Seats from 1 to 8
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"trips\":[{\"eventId\":\"" + december + "\",\"driverId\":\"" + NINA + "\",\"seats\":9}]}")
                .post(PLAN + "/distribute").then().statusCode(400);
    }

    @Test
    void drv30EveryDriverGetsOneNotificationAndOnlyCoachesDistribute() {
        preview(login("anna")).statusCode(403);
        given().cookie("stv_session", login("anna")).contentType(ContentType.JSON).body("{\"trips\":[]}")
                .post(PLAN + "/distribute").then().statusCode(403);
        String petra = login("petra");
        put(petra, "/expected", "{\"expected\":10}").statusCode(200);
        JsonPath json = distribute(petra).statusCode(200).extract().jsonPath();

        String driver = drivers(json, december).get(0).toLowerCase();
        assertEquals(1, texts(driver).stream().filter(t -> t.startsWith("Die Fahrten der Saison wurden verteilt.")).count());
    }

    @Test
    void drv33RemovingAllPlannedDrivers() {
        given().cookie("stv_session", login("anna")).delete(PLAN + "/cars").then().statusCode(403);
        String petra = login("petra");
        put(petra, "/expected", "{\"expected\":10}").statusCode(200);
        distribute(petra).statusCode(200);

        JsonPath json = given().cookie("stv_session", petra).delete(PLAN + "/cars").then().statusCode(200)
                .extract().jsonPath();
        assertTrue(drivers(json, T04).isEmpty());
        assertTrue(drivers(json, december).isEmpty());
        // Sara (driver) and Anna (passenger of Petra) are told once each
        assertEquals(1, texts("sara").stream()
                .filter(t -> t.startsWith("Die geplanten Fahrten wurden entfernt. Du fährst")).count());
        assertEquals(1, texts("anna").stream()
                .filter(t -> t.startsWith("Die geplanten Fahrten wurden entfernt. Du hast keinen Platz")).count());
    }

    @Test
    void drv31NextInLineTakesOverOrAddsACar() {
        String petra = login("petra");
        // One more car in December: the first by name who has no trip yet
        JsonPath json = given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{}")
                .post(PLAN + "/match-days/" + december + "/next").then().statusCode(200).extract().jsonPath();
        List<String> first = drivers(json, december);
        assertEquals(1, first.size());
        assertFalse(List.of("Petra", "Sara").contains(first.get(0)));

        // Sara cannot drive on match day 04: the next in line takes her car, nobody who already drives there
        String sara = json.getString("matchDays.find { it.eventId == '" + T04 + "' }.cars.find { it.driver.firstName == 'Sara' }.driver.id");
        json = given().cookie("stv_session", login("sara")).contentType(ContentType.JSON)
                .body("{\"replaces\":\"" + sara + "\"}")
                .post(PLAN + "/match-days/" + T04 + "/next").then().statusCode(200).extract().jsonPath();
        List<String> day04 = drivers(json, T04);
        assertEquals(2, day04.size());
        assertTrue(day04.contains("Petra"));
        assertFalse(day04.contains("Sara"));

        // A player cannot hand over somebody else's trip or add a car
        String petraId = json.getString("matchDays.find { it.eventId == '" + T04 + "' }.cars.find { it.driver.firstName == 'Petra' }.driver.id");
        given().cookie("stv_session", login("anna")).contentType(ContentType.JSON)
                .body("{\"replaces\":\"" + petraId + "\"}")
                .post(PLAN + "/match-days/" + T04 + "/next").then().statusCode(403);
        given().cookie("stv_session", login("anna")).contentType(ContentType.JSON).body("{}")
                .post(PLAN + "/match-days/" + december + "/next").then().statusCode(403);
    }
}
