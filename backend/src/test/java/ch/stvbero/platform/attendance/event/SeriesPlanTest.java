package ch.stvbero.platform.attendance.event;

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
 * Reworking the series of a team (TER-18 to TER-20), against the seed of docs/test-data.md: series 02 «Training»
 * Thu 19:00–20:30 of Team 1 (20.08.2026–29.04.2027); its event 02 on Thu 19.11. has Anna's response. Default
 * "now": Mon 16.11.2026 06:00.
 */
@QuarkusTest
class SeriesPlanTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String SERIES_2 = "70000000-0000-4000-8000-000000000002";

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static String input(String weekday, String start, String end) {
        return """
                {"type":"PRACTICE","title":"Training","teamIds":["%s"],"weekday":"%s","startTime":"%s","endTime":"%s",
                 "validFrom":"2026-08-20","validUntil":"2027-04-29","location":{"name":"Sporthalle Nord"}}"""
                .formatted(TEAM_1, weekday, start, end);
    }

    private static ValidatableResponse post(String session, String path, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body).post(path).then();
    }

    private static Map<String, Object> series2(JsonPath list) {
        return list.getMap("find { it.series.id == '" + SERIES_2 + "' }");
    }

    private static int upcoming(String session) {
        JsonPath list = given().cookie("stv_session", session).queryParam("team", TEAM_1).get("/api/series").then()
                .statusCode(200).extract().jsonPath();
        return (int) series2(list).get("upcoming");
    }

    @Test
    void ter18TheSeriesOfATeamWithTheirUpcomingEvents() {
        JsonPath list = given().cookie("stv_session", login("petra")).queryParam("team", TEAM_1).get("/api/series")
                .then().statusCode(200).extract().jsonPath();
        List<String> ids = list.getList("series.id");
        assertTrue(ids.contains(SERIES_2));
        assertTrue(ids.contains("70000000-0000-4000-8000-000000000001"));
        Map<String, Object> s2 = series2(list);
        assertTrue((int) s2.get("upcoming") > 10);
        assertTrue(((String) s2.get("next")).startsWith("2026-11-19T19:00"));
        assertEquals(true, s2.get("canEdit"));

        // Players see them but cannot change them; other teams see nothing
        JsonPath anna = given().cookie("stv_session", login("anna")).queryParam("team", TEAM_1).get("/api/series")
                .then().statusCode(200).extract().jsonPath();
        assertEquals(false, series2(anna).get("canEdit"));
        given().cookie("stv_session", login("eva")).queryParam("team", TEAM_1).get("/api/series").then().statusCode(403);
        given().cookie("stv_session", login("petra")).get("/api/series").then().statusCode(400);
    }

    @Test
    void ter19APreviewTellsWhatAChangeDoesAndChangesNothing() {
        String petra = login("petra");
        int before = upcoming(petra);

        // Later on the same day: every upcoming event is adjusted, nothing goes
        JsonPath later = post(petra, "/api/series/" + SERIES_2 + "/preview", input("THURSDAY", "19:30", "21:00"))
                .statusCode(200).extract().jsonPath();
        assertEquals(before, later.getInt("adjusted"));
        assertEquals(0, later.getInt("created"));
        assertEquals(0, later.getInt("removed"));
        assertEquals(0, later.getInt("cancelled"));
        assertEquals("2026-11-19", later.getString("from"));

        // The same details: nothing changes
        JsonPath same = post(petra, "/api/series/" + SERIES_2 + "/preview", input("THURSDAY", "19:00", "20:30"))
                .statusCode(200).extract().jsonPath();
        assertEquals(0, same.getInt("adjusted"));
        assertEquals(null, same.getString("from"));

        // Friday instead of Thursday: the Thursdays go, Anna's response cancels the 19.11. instead of removing it
        JsonPath friday = post(petra, "/api/series/" + SERIES_2 + "/preview", input("FRIDAY", "19:00", "20:30"))
                .statusCode(200).extract().jsonPath();
        assertTrue(friday.getInt("created") > 10);
        assertTrue(friday.getInt("cancelled") >= 1);
        assertEquals(before, friday.getInt("removed") + friday.getInt("cancelled"));

        // Nothing was changed
        assertEquals(before, upcoming(petra));
        given().cookie("stv_session", petra).get("/api/series/" + SERIES_2).then().statusCode(200)
                .body("weekday", org.hamcrest.Matchers.equalTo("THURSDAY"))
                .body("startTime", org.hamcrest.Matchers.equalTo("19:00"));
    }

    @Test
    void ter20EndingASeriesRemovesItsUpcomingEventsFromADayOn() {
        String petra = login("petra");
        String from = "{\"from\":\"2026-12-01\"}";
        JsonPath preview = post(petra, "/api/series/" + SERIES_2 + "/end/preview", from).statusCode(200)
                .extract().jsonPath();
        int gone = preview.getInt("removed") + preview.getInt("cancelled");
        assertTrue(gone > 10);
        assertEquals("2026-12-03", preview.getString("from"));

        int before = upcoming(petra);
        post(petra, "/api/series/" + SERIES_2 + "/end", from).statusCode(200)
                .body("validUntil", org.hamcrest.Matchers.equalTo("2026-11-30"));
        // Thu 19.11. and 26.11. stay
        assertEquals(before - gone, upcoming(petra));
        assertEquals(2, upcoming(petra));
    }

    @Test
    void ter20EndingASeriesBeforeItBeginsLeavesNoEvent() {
        String petra = login("petra");
        String body = """
                {"type":"PRACTICE","title":"Konditraining","teamIds":["%s"],"weekday":"MONDAY","startTime":"18:00",
                 "endTime":"19:00","validFrom":"2027-01-04","validUntil":"2027-03-29"}""".formatted(TEAM_1);
        String id = post(petra, "/api/series", body).statusCode(201).extract().path("id");
        post(petra, "/api/series/" + id + "/end", "{\"from\":\"2026-12-01\"}").statusCode(200);
        JsonPath list = given().cookie("stv_session", petra).queryParam("team", TEAM_1).get("/api/series").then()
                .statusCode(200).extract().jsonPath();
        assertEquals(0, (int) list.getMap("find { it.series.id == '" + id + "' }").get("upcoming"));
        // A series without upcoming events comes last
        List<Integer> counts = list.getList("upcoming");
        assertEquals(0, counts.get(counts.size() - 1));
    }

    @Test
    void ter19PlayersNeitherPreviewNorEnd() {
        String anna = login("anna");
        post(anna, "/api/series/" + SERIES_2 + "/preview", input("FRIDAY", "19:00", "20:30")).statusCode(403);
        post(anna, "/api/series/" + SERIES_2 + "/end/preview", "{\"from\":\"2026-12-01\"}").statusCode(403);
        post(anna, "/api/series/" + SERIES_2 + "/end", "{\"from\":\"2026-12-01\"}").statusCode(403);
        post(login("petra"), "/api/series/" + SERIES_2 + "/end", "{}").statusCode(400);
        assertFalse(upcoming(anna) == 0);
    }
}
