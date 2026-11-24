package ch.stvbero.platform.attendance.event;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Series one year ahead (TER-22) and without an end (TER-23), against the seed of docs/test-data.md. Team 1 has no
 * series on Monday. Default "now": Mon 16.11.2026 06:00.
 */
@QuarkusTest
class SeriesHorizonTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";

    String petra;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
        petra = login("petra");
    }

    private String create(String validUntil) {
        String until = validUntil == null ? "null" : "\"" + validUntil + "\"";
        return given().cookie("stv_session", petra).contentType(ContentType.JSON).body("""
                        {"type":"PRACTICE","title":"Krafttraining","teamIds":["%s"],"weekday":"MONDAY","startTime":"18:30",
                         "endTime":"19:30","validFrom":"2026-11-17","validUntil":%s}""".formatted(TEAM_1, until))
                .post("/api/series").then().statusCode(201).extract().path("id");
    }

    private Map<String, Object> overview(String seriesId) {
        JsonPath list = given().cookie("stv_session", petra).queryParam("team", TEAM_1).get("/api/series").then()
                .statusCode(200).extract().jsonPath();
        return list.getMap("find { it.series.id == '" + seriesId + "' }");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> series(String seriesId) {
        return (Map<String, Object>) overview(seriesId).get("series");
    }

    private static void clock(String now) {
        given().contentType(ContentType.JSON).body("{\"now\":\"" + now + "\"}").put("/api/test/clock").then()
                .statusCode(204);
    }

    /** Mondays from {@code from} to {@code until}, both included. */
    private static int mondays(LocalDate from, LocalDate until) {
        int n = 0;
        for (LocalDate d = from; !d.isAfter(until); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.MONDAY) {
                n++;
            }
        }
        return n;
    }

    @Test
    void ter23ASeriesWithoutAnEndHasItsEventsOneYearAhead() {
        String id = create(null);
        Map<String, Object> s = series(id);
        assertNull(s.get("validUntil"));
        assertEquals("2027-11-16", s.get("eventsUntil"));
        assertEquals(mondays(LocalDate.of(2026, 11, 17), LocalDate.of(2027, 11, 16)), overview(id).get("upcoming"));
    }

    @Test
    void ter22AsTimeGoesOnTheMissingEventsAreCreated() {
        String id = create(null);
        // Nine weeks later: the series reaches one year ahead again, and does not stop
        clock("2027-01-18T06:00:00+01:00");
        assertEquals("2028-01-18", series(id).get("eventsUntil"));
        assertEquals(mondays(LocalDate.of(2027, 1, 18), LocalDate.of(2028, 1, 18)), overview(id).get("upcoming"));

        // Running it again changes nothing
        clock("2027-01-18T06:00:00+01:00");
        assertEquals(mondays(LocalDate.of(2027, 1, 18), LocalDate.of(2028, 1, 18)), overview(id).get("upcoming"));
    }

    @Test
    void ter22HolidaysEnteredAheadAlsoApplyToEventsCreatedLater() {
        String id = create(null);
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"startDate\":\"2027-12-20\",\"endDate\":\"2028-01-02\",\"reason\":\"Weihnachtsferien\"}")
                .post("/api/series/" + id + "/exceptions").then().statusCode(201);
        clock("2027-01-18T06:00:00+01:00");
        // Mondays 20.12.2027 and 27.12.2027 are holidays
        assertEquals(mondays(LocalDate.of(2027, 1, 18), LocalDate.of(2028, 1, 18)) - 2, overview(id).get("upcoming"));
    }

    @Test
    void ter22AnEndFurtherAheadIsReachedStepByStep() {
        String id = create("2030-06-30");
        assertEquals("2027-11-16", series(id).get("eventsUntil"));
        assertEquals("2030-06-30", series(id).get("validUntil"));
        clock("2027-03-01T06:00:00+01:00");
        assertEquals("2028-03-01", series(id).get("eventsUntil"));
    }

    @Test
    void ter22ASeriesThatEndsCreatesNothingAfterItsEnd() {
        String id = create("2027-02-28");
        int before = (int) overview(id).get("upcoming");
        assertEquals(mondays(LocalDate.of(2026, 11, 17), LocalDate.of(2027, 2, 28)), before);
        clock("2027-01-18T06:00:00+01:00");
        assertEquals(mondays(LocalDate.of(2027, 1, 18), LocalDate.of(2027, 2, 28)), overview(id).get("upcoming"));
        assertEquals("2027-02-28", series(id).get("eventsUntil"));
    }

    @Test
    void ter22EventsCreatedAheadMoveIntoTheirSeasonOnceItExists() {
        create(null);
        String alex = login("alex");
        // Mon 02.08.2027 lies after the season 2026/27, and there is no later season yet: the event belongs to the
        // season of the series, so Anna (player of Team 1 in 2026/27) is in it
        String august = given().cookie("stv_session", petra).queryParam("from", "2027-08-02").queryParam("to", "2027-08-02")
                .queryParam("team", TEAM_1).get("/api/events").then().statusCode(200).extract().path("[0].id");
        String june = given().cookie("stv_session", petra).queryParam("from", "2027-06-28").queryParam("to", "2027-06-28")
                .queryParam("team", TEAM_1).get("/api/events").then().statusCode(200).extract().path("[0].id");
        String anna = login("anna");
        given().cookie("stv_session", anna).get("/api/events/" + august).then().statusCode(200);

        // The season 2027/28 is created with the teams of 2026/27; Anna leaves Team 1 there
        String next = given().cookie("stv_session", alex).contentType(ContentType.JSON).body("""
                        {"name":"2027/28","startDate":"2027-07-01","endDate":"2028-06-30",
                         "copyFrom":"60000000-0000-4000-8000-000000000002"}""")
                .post("/api/seasons").then().statusCode(201).extract().path("id");
        given().cookie("stv_session", alex).queryParam("season", next)
                .delete("/api/teams/" + TEAM_1 + "/members/20000000-0000-4000-8000-000000000005").then().statusCode(204);

        // The next run moves the event into its season: Anna is no longer in it; June stays in 2026/27
        clock("2026-11-16T06:00:00+01:00");
        given().cookie("stv_session", anna).get("/api/events/" + august).then().statusCode(403);
        given().cookie("stv_session", anna).get("/api/events/" + june).then().statusCode(200);
        given().cookie("stv_session", petra).get("/api/events/" + august).then().statusCode(200);
    }

    @Test
    void ter20EndingASeriesWithoutAnEndStopsIt() {
        String id = create(null);
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{\"from\":\"2027-03-01\"}")
                .post("/api/series/" + id + "/end").then().statusCode(200);
        assertEquals("2027-02-28", series(id).get("validUntil"));
        clock("2027-01-18T06:00:00+01:00");
        assertEquals(mondays(LocalDate.of(2027, 1, 18), LocalDate.of(2027, 2, 28)), overview(id).get("upcoming"));
    }

    @Test
    void ter23ChangingASeriesCanRemoveAndAddItsEnd() {
        String id = create(null);
        String body = """
                {"type":"PRACTICE","title":"Krafttraining","teamIds":["%s"],"weekday":"MONDAY","startTime":"18:30",
                 "endTime":"19:30","validFrom":"2026-11-17","validUntil":%s}""";
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(body.formatted(TEAM_1, "\"2027-01-31\""))
                .put("/api/series/" + id).then().statusCode(200);
        assertEquals(mondays(LocalDate.of(2026, 11, 17), LocalDate.of(2027, 1, 31)), overview(id).get("upcoming"));

        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(body.formatted(TEAM_1, "null"))
                .put("/api/series/" + id).then().statusCode(200);
        assertNull(series(id).get("validUntil"));
        assertEquals(mondays(LocalDate.of(2026, 11, 17), LocalDate.of(2027, 11, 16)), overview(id).get("upcoming"));
    }
}
