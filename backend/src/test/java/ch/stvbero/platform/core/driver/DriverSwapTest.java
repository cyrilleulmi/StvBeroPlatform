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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Swaps (DRV-25), history (DRV-26), totals (DRV-27), other trips (DRV-28) and the driving time overwritten by hand
 * (DRV-21, DRV-22), against the seed of docs/test-data.md: on match day 04 of Team 1 Petra Keller drives (full) and
 * Sara Brun (empty). Default "now": Mon 16.11.2026 06:00.
 */
@QuarkusTest
class DriverSwapTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String SARA = "20000000-0000-4000-8000-000000000008";
    static final String KIM = "20000000-0000-4000-8000-000000000011";
    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String EVA = "20000000-0000-4000-8000-000000000012";
    static final String T04 = "40000000-0000-4000-8000-000000000004";

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static ValidatableResponse plan(String session) {
        return given().cookie("stv_session", session).get("/api/teams/" + TEAM_1 + "/driver-plan").then();
    }

    private static ValidatableResponse post(String session, String path, String body) {
        var request = given().cookie("stv_session", session);
        if (body != null) {
            request = request.contentType(ContentType.JSON).body(body);
        }
        return request.post("/api/teams/" + TEAM_1 + "/driver-plan" + path).then();
    }

    private static ValidatableResponse swap(String session, String driver, String partner, String partnerEvent,
                                            boolean direct) {
        String driverId = driver == null ? "" : "\"driverId\":\"" + driver + "\",";
        String other = partnerEvent == null ? "" : ",\"partnerEventId\":\"" + partnerEvent + "\"";
        return post(session, "/swaps", "{\"eventId\":\"" + T04 + "\"," + driverId + "\"partnerId\":\"" + partner
                + "\"" + other + ",\"direct\":" + direct + "}");
    }

    private static String createMatchDay(String session, String day) {
        String body = """
                {"type":"MATCH_DAY","title":"Spieltag","start":"%sT10:00:00+01:00","end":"%sT14:00:00+01:00",
                 "teamIds":["%s"],"location":{"name":"Sporthalle Seefeld"},
                 "meetingPoint":{"time":null,"location":"Parkplatz Sporthalle Nord"}}""".formatted(day, day, TEAM_1);
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .post("/api/events").then().statusCode(201).extract().path("id");
    }

    private static List<String> drivers(JsonPath plan, String eventId) {
        return plan.getList("matchDays.find { it.eventId == '" + eventId + "' }.cars.driver.firstName");
    }

    private static List<String> textsFor(String firstName) {
        JsonPath json = given().get("/api/test/notifications").then().statusCode(200).extract().jsonPath();
        List<Map<String, Object>> all = json.getList("findAll { it.type == 'DRIVER_LIST_CHANGED' }");
        return all.stream().filter(n -> firstName.equals(((Map<?, ?>) n.get("recipient")).get("firstName")))
                .map(n -> (String) n.get("text")).toList();
    }

    // ------------------------------------------------------------------ Swaps (DRV-25)

    @Test
    void drv25DirectTakeoverMovesOnlyTheDriver() {
        String sara = login("sara");
        JsonPath json = swap(sara, null, KIM, null, true).statusCode(200).extract().jsonPath();
        assertThat(drivers(json, T04), contains("Kim", "Petra"));
        assertThat(json.getList("swaps"), empty());
        assertThat(json.getString("history[0].text"),
                equalTo("Übernommen: Kim Baumann fährt am Spieltag So 22.11. statt Sara Brun."));
        assertThat(json.getString("history[0].by.firstName"), equalTo("Sara"));
        assertThat(textsFor("Kim"), hasItem("Du fährst neu am Spieltag So 22.11. statt Sara Brun."));
    }

    @Test
    void drv25DirectSwapAgainstAnotherMatchDay() {
        String petra = login("petra");
        String december = createMatchDay(petra, "2026-12-06");
        String kim = login("kim");
        given().cookie("stv_session", kim).contentType(ContentType.JSON)
                .body("{\"seats\":3,\"eventIds\":[\"" + december + "\"]}")
                .post("/api/teams/" + TEAM_1 + "/driver-plan/cars").then().statusCode(200);

        JsonPath json = swap(login("sara"), null, KIM, december, true).statusCode(200).extract().jsonPath();
        assertThat(drivers(json, T04), contains("Kim", "Petra"));
        assertThat(drivers(json, december), contains("Sara"));
        assertThat(json.getString("history[0].text"), equalTo("Getauscht: Sara Brun hat mit Kim Baumann getauscht. "
                + "Kim Baumann fährt am Spieltag So 22.11., Sara Brun am Spieltag So 06.12."));
    }

    @Test
    void drv25AskAcceptDeclineAndWithdraw() {
        String sara = login("sara");
        String kim = login("kim");
        JsonPath asked = swap(sara, null, KIM, null, false).statusCode(200).extract().jsonPath();
        assertThat(drivers(asked, T04), contains("Sara", "Petra"));
        assertThat(asked.getList("swaps"), hasSize(1));
        assertEquals(false, asked.getBoolean("swaps[0].canAnswer"));
        assertEquals(true, asked.getBoolean("swaps[0].canWithdraw"));
        assertThat(asked.getString("history[0].text"), startsWith("Tausch angefragt: Sara Brun fragt Kim Baumann"));
        assertThat(textsFor("Kim"), hasItem(containsString("Sara Brun fragt, ob du die Fahrt am Spieltag So 22.11. übernimmst.")));
        // A second request for the same trip waits for the first
        swap(sara, null, KIM, null, false).statusCode(409);
        // Others of the team do not see it; Kim cannot withdraw it
        plan(login("anna")).statusCode(200).body("swaps", empty());
        String id = asked.getString("swaps[0].id");
        post(kim, "/swaps/" + id + "/withdraw", null).statusCode(403);
        // Kim accepts: the swap is carried out
        JsonPath accepted = post(kim, "/swaps/" + id + "/accept", null).statusCode(200).extract().jsonPath();
        assertThat(drivers(accepted, T04), contains("Kim", "Petra"));
        assertThat(accepted.getList("swaps"), empty());
        assertThat(textsFor("Sara"), hasItem("Kim Baumann hat den Tausch angenommen und fährt am Spieltag So 22.11."));
        post(kim, "/swaps/" + id + "/accept", null).statusCode(404);

        // Kim asks Sara back, Sara declines; then Kim asks again and withdraws
        String back = swap(kim, null, SARA, null, false).statusCode(200).extract().path("swaps[0].id");
        post(sara, "/swaps/" + back + "/decline", null).statusCode(200).body("swaps", empty())
                .body("history[0].text", startsWith("Tausch abgelehnt: Sara Brun übernimmt die Fahrt von Kim Baumann"));
        String again = swap(kim, null, SARA, null, false).statusCode(200).extract().path("swaps[0].id");
        post(kim, "/swaps/" + again + "/withdraw", null).statusCode(200).body("swaps", empty());
        plan(kim).body("matchDays[0].cars.driver.firstName", contains("Kim", "Petra"));
    }

    @Test
    void drv25RulesAndRights() {
        String sara = login("sara");
        // Petra drives match day 04 herself
        swap(sara, null, PETRA, null, true).statusCode(409).body("rule", equalTo("DRV-25"));
        // Anna does not drive and cannot give away Sara's trip
        swap(login("anna"), SARA, KIM, null, true).statusCode(403);
        swap(login("anna"), null, KIM, null, true).statusCode(409);
        // Eva is not in Team 1
        swap(sara, null, EVA, null, true).statusCode(400);
        // A coach swaps for any driver; when the partner rides along, she leaves her seat
        String anna = "20000000-0000-4000-8000-000000000005";
        JsonPath json = swap(login("petra"), SARA, anna, null, true).statusCode(200).extract().jsonPath();
        assertThat(drivers(json, T04), contains("Petra", "Anna"));
        assertThat(json.getList("matchDays[0].cars.find { it.driver.firstName == 'Petra' }.passengers.firstName"),
                contains("Mia", "Lea"));
    }

    // ------------------------------------------------------------------ History, totals, other trips

    @Test
    void drv26SeedCarsAreInTheHistory() {
        JsonPath json = plan(login("anna")).statusCode(200).extract().jsonPath();
        List<String> texts = json.getList("history.text");
        assertThat(texts, hasItem("Eingetragen: Petra Keller fährt am Spieltag So 22.11."));
        assertThat(texts, hasItem("Eingetragen: Sara Brun fährt am Spieltag So 22.11."));
    }

    @Test
    void drv27Drv28OtherTripsCountAndAddUp() {
        String kim = login("kim");
        String body = "{\"date\":\"2026-11-01\",\"title\":\"Turnier Tenero\",\"distanceKm\":100}";
        JsonPath json = post(kim, "/trips", body).statusCode(200).extract().jsonPath();
        assertThat(json.getList("extraTrips.title"), contains("Turnier Tenero"));
        assertEquals(200.0, json.getDouble("extraTrips[0].creditKm"));
        assertEquals(true, json.getBoolean("extraTrips[0].over"));
        Map<String, Object> share = json.getMap("shares.find { it.person.firstName == 'Kim' }");
        assertEquals(1, share.get("trips"));
        assertEquals(200.0f, ((Number) share.get("kilometres")).floatValue());
        // Totals: Kim's trip that is over, Petra's and Sara's planned trips of match day 04
        assertEquals(1, json.getInt("totals.trips"));
        assertEquals(2, json.getInt("totals.plannedTrips"));
        assertEquals(200.0, json.getDouble("totals.kilometres"));
        assertEquals(93.6, json.getDouble("totals.plannedKilometres"));
        assertThat(json.getString("history[0].text"),
                equalTo("Weitere Fahrt: Kim Baumann, «Turnier Tenero» am 01.11.2026, 100.0 km pro Weg."));
        // «Fahrten» counts it too (DRV-09)
        given().cookie("stv_session", kim).get("/api/driver-summary").then().statusCode(200)
                .body("rows.find { it.person.firstName == 'Kim' }.trips", equalTo(1));

        // Anna cannot add one for Kim nor remove Kim's; Kim removes her own
        post(login("anna"), "/trips", body.replace("{", "{\"driverId\":\"" + KIM + "\",")).statusCode(403);
        String trip = json.getString("extraTrips[0].id");
        given().cookie("stv_session", login("anna")).delete("/api/teams/" + TEAM_1 + "/driver-plan/trips/" + trip)
                .then().statusCode(403);
        given().cookie("stv_session", kim).delete("/api/teams/" + TEAM_1 + "/driver-plan/trips/" + trip)
                .then().statusCode(200).body("extraTrips", empty())
                .body("history[0].text", startsWith("Weitere Fahrt entfernt: Kim Baumann"));
        post(kim, "/trips", "{\"date\":\"2026-11-01\",\"title\":\"\",\"distanceKm\":0}").statusCode(400);
    }

    // ------------------------------------------------------------------ Driving time (DRV-21, DRV-22)

    @Test
    void drv21Drv22DrivingTimeCanBeOverwrittenAndExplainsItself() {
        String petra = login("petra");
        String path = "/api/events/" + T04 + "/driver-list/route/duration";
        given().cookie("stv_session", petra).get("/api/events/" + T04 + "/driver-list").then().statusCode(200)
                .body("route.durationMinutes", equalTo(26)).body("route.durationManual", equalTo(false))
                .body("route.provider", equalTo("SIMULATOR")).body("route.departureTimeUsed", equalTo(false))
                .body("route.calculatedAt", startsWith("2026-11-16T06:00"));

        given().cookie("stv_session", login("anna")).contentType(ContentType.JSON).body("{\"minutes\":40}")
                .put(path).then().statusCode(403);
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{\"minutes\":0}")
                .put(path).then().statusCode(400);
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{\"minutes\":40}")
                .put(path).then().statusCode(200)
                .body("route.durationMinutes", equalTo(40)).body("route.calculatedMinutes", equalTo(26))
                .body("route.durationManual", equalTo(true)).body("route.durationChangedBy.firstName", equalTo("Petra"));
        // The plan and the timeline use it; recalculating keeps it
        plan(petra).body("matchDays[0].durationMinutes", equalTo(40));
        given().cookie("stv_session", petra).post("/api/events/" + T04 + "/driver-list/route/recalculate").then()
                .statusCode(200).body("route.durationMinutes", equalTo(40));
        given().cookie("stv_session", petra).get("/api/events/" + T04 + "/timeline").then().statusCode(200)
                .body("driveMinutes", equalTo(40));

        given().cookie("stv_session", petra).delete(path).then().statusCode(200)
                .body("route.durationMinutes", equalTo(26)).body("route.durationManual", equalTo(false));
    }
}
