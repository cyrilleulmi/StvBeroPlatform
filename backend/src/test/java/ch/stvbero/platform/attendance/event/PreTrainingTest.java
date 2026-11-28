package ch.stvbero.platform.attendance.event;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pre-training of a practice on events and series (TER-29 to TER-31, E-117). IDs from docs/test-data.md. */
@QuarkusTest
class PreTrainingTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";

    String petra;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
        petra = login("petra");
    }

    // ------------------------------------------------------------------ Events

    private static String event(String type, String start, String end, String preTraining) {
        return """
                {"type":"%s","title":"Training","start":"%s","end":"%s","teamIds":["%s"],
                 "location":{"name":"Turnhalle Dorf"}%s}"""
                .formatted(type, start, end, TEAM_1, preTraining == null ? "" : ",\"preTraining\":" + preTraining);
    }

    private ValidatableResponse create(String body) {
        return given().cookie("stv_session", petra).contentType(ContentType.JSON).body(body).post("/api/events").then();
    }

    private ValidatableResponse update(String id, String body) {
        return given().cookie("stv_session", petra).contentType(ContentType.JSON).body(body)
                .put("/api/events/" + id).then();
    }

    private String createPractice(String preTraining) {
        return create(event("PRACTICE", "2026-11-20T19:00:00+01:00", "2026-11-20T20:30:00+01:00", preTraining))
                .statusCode(201).extract().path("id");
    }

    private List<String> notificationTexts(String person, String eventId) {
        return given().cookie("stv_session", login(person)).get("/api/notifications").then().statusCode(200)
                .extract().jsonPath().getList("findAll { it.type == 'EVENT_RESCHEDULED' && it.eventId == '" + eventId
                        + "' }.text");
    }

    @Test
    void ter29PracticeWithPreTrainingIsReadBackOnTheEventAndInTheList() {
        String id = createPractice("{\"title\":\" Joggen \",\"start\":\"2026-11-20T18:40:00+01:00\"}");
        given().cookie("stv_session", petra).get("/api/events/" + id).then().statusCode(200)
                .body("preTraining.title", equalTo("Joggen"))
                .body("preTraining.start", equalTo("2026-11-20T18:40:00+01:00"))
                .body("start", equalTo("2026-11-20T19:00:00+01:00"));
        given().cookie("stv_session", petra).get("/api/events?type=PRACTICE").then().statusCode(200)
                .body("find { it.id == '" + id + "' }.preTraining.title", equalTo("Joggen"))
                .body("find { it.id == '" + id + "' }.preTraining.start", equalTo("2026-11-20T18:40:00+01:00"))
                // Practices without one do not have the property at all
                .body("find { it.id != '" + id + "' }.containsKey('preTraining')", equalTo(false));
    }

    @Test
    void ter29UpdateChangesAndRemovesIt() {
        String id = createPractice("{\"title\":\"Joggen\",\"start\":\"2026-11-20T18:40:00+01:00\"}");
        String time = "\"start\":\"2026-11-20T18:30:00+01:00\"";
        update(id, event("PRACTICE", "2026-11-20T19:00:00+01:00", "2026-11-20T20:30:00+01:00",
                "{\"title\":\"Dehnen\"," + time + "}")).statusCode(200)
                .body("preTraining.title", equalTo("Dehnen"))
                .body("preTraining.start", equalTo("2026-11-20T18:30:00+01:00"));
        // Missing and null both remove it
        update(id, event("PRACTICE", "2026-11-20T19:00:00+01:00", "2026-11-20T20:30:00+01:00", null)).statusCode(200)
                .body("preTraining", nullValue());
        update(id, event("PRACTICE", "2026-11-20T19:00:00+01:00", "2026-11-20T20:30:00+01:00",
                "{\"title\":\"Joggen\"," + time + "}")).statusCode(200).body("preTraining.title", equalTo("Joggen"));
        update(id, event("PRACTICE", "2026-11-20T19:00:00+01:00", "2026-11-20T20:30:00+01:00", "null")).statusCode(200)
                .body("preTraining", nullValue());
        given().cookie("stv_session", petra).get("/api/events/" + id).then().body("preTraining", nullValue());
    }

    @Test
    void ter29InvalidPreTrainingIs400WithTheFieldAndAGermanMessage() {
        String ok = "\"start\":\"2026-11-20T18:40:00+01:00\"";
        // Not a practice
        create(event("OTHER", "2026-11-20T19:00:00+01:00", "2026-11-20T20:30:00+01:00",
                "{\"title\":\"Joggen\"," + ok + "}"))
                .statusCode(400).body("fields[0].field", equalTo("preTraining.title"))
                .body("fields[0].message", equalTo("Ein Vortraining gibt es nur bei Trainings."));
        // Not before the practice
        create(event("PRACTICE", "2026-11-20T19:00:00+01:00", "2026-11-20T20:30:00+01:00",
                "{\"title\":\"Joggen\",\"start\":\"2026-11-20T19:00:00+01:00\"}"))
                .statusCode(400).body("fields[0].field", equalTo("preTraining.start"))
                .body("fields[0].message", equalTo("Das Vortraining muss vor dem Training beginnen."));
        // Another day
        create(event("PRACTICE", "2026-11-20T19:00:00+01:00", "2026-11-20T20:30:00+01:00",
                "{\"title\":\"Joggen\",\"start\":\"2026-11-19T18:40:00+01:00\"}"))
                .statusCode(400).body("fields[0].field", equalTo("preTraining.start"))
                .body("fields[0].message", equalTo("Das Vortraining muss am gleichen Tag sein wie das Training."));
        // Blank title, no title, no start
        create(event("PRACTICE", "2026-11-20T19:00:00+01:00", "2026-11-20T20:30:00+01:00",
                "{\"title\":\"   \"," + ok + "}")).statusCode(400).body("fields[0].field", equalTo("preTraining.title"));
        create(event("PRACTICE", "2026-11-20T19:00:00+01:00", "2026-11-20T20:30:00+01:00",
                "{" + ok + "}")).statusCode(400).body("fields[0].field", equalTo("preTraining.title"));
        create(event("PRACTICE", "2026-11-20T19:00:00+01:00", "2026-11-20T20:30:00+01:00",
                "{\"title\":\"Joggen\"}")).statusCode(400).body("fields[0].field", equalTo("preTraining.start"));
        // Too long
        create(event("PRACTICE", "2026-11-20T19:00:00+01:00", "2026-11-20T20:30:00+01:00",
                "{\"title\":\"" + "x".repeat(61) + "\"," + ok + "}")).statusCode(400)
                .body("fields[0].field", equalTo("preTraining.title"));
        // Changing a practice with one into another type is refused as well
        String id = createPractice("{\"title\":\"Joggen\"," + ok + "}");
        update(id, event("OTHER", "2026-11-20T19:00:00+01:00", "2026-11-20T20:30:00+01:00",
                "{\"title\":\"Joggen\"," + ok + "}")).statusCode(400);
    }

    @Test
    void ter31ChangingThePreTrainingNotifiesTheMembersLikeAChangedTime() {
        String id = createPractice(null);
        assertEquals(List.of(), notificationTexts("anna", id));
        String body = event("PRACTICE", "2026-11-20T19:00:00+01:00", "2026-11-20T20:30:00+01:00",
                "{\"title\":\"Joggen\",\"start\":\"2026-11-20T18:40:00+01:00\"}");
        update(id, body).statusCode(200);
        assertEquals(List.of("Training Fr 20.11. hat neu ein Vortraining: «Joggen» ab 18:40."),
                notificationTexts("anna", id));
        // Nothing changes, nothing is sent; whoever changes it is not told
        update(id, body).statusCode(200);
        assertEquals(1, notificationTexts("anna", id).size());
        assertEquals(List.of(), notificationTexts("petra", id));
        update(id, event("PRACTICE", "2026-11-20T19:00:00+01:00", "2026-11-20T20:30:00+01:00", null)).statusCode(200);
        assertEquals(2, notificationTexts("anna", id).size());
        assertTrue(notificationTexts("anna", id).stream().anyMatch(t -> t.contains("entfällt das Vortraining «Joggen»")));
    }

    // ------------------------------------------------------------------ Series

    private static String series(String start, String preTraining) {
        return """
                {"type":"PRACTICE","title":"Konditionstraining","teamIds":["%s"],"weekday":"MONDAY","startTime":"%s",
                 "endTime":"21:00","validFrom":"2026-11-16","validUntil":"2026-12-07","location":{"name":"Sporthalle Nord"}%s}"""
                .formatted(TEAM_1, start, preTraining == null ? "" : ",\"preTraining\":" + preTraining);
    }

    private static final String JOGGEN_1910 = "{\"title\":\"Joggen\",\"startTime\":\"19:10\"}";

    private ValidatableResponse postSeries(String body) {
        return given().cookie("stv_session", petra).contentType(ContentType.JSON).body(body).post("/api/series").then();
    }

    private String createSeries(String body) {
        return postSeries(body).statusCode(201).extract().path("id");
    }

    private ValidatableResponse updateSeries(String id, String body) {
        return given().cookie("stv_session", petra).contentType(ContentType.JSON).body(body)
                .put("/api/series/" + id).then();
    }

    private ValidatableResponse preview(String id, String body) {
        return given().cookie("stv_session", petra).contentType(ContentType.JSON).body(body)
                .post("/api/series/" + id + "/preview").then().statusCode(200);
    }

    /** The events of the series by date, as the list returns them. */
    private List<Map<String, Object>> eventsOf(String seriesId) {
        return given().cookie("stv_session", petra).get("/api/events?type=PRACTICE&to=2026-12-31").then().statusCode(200)
                .extract().jsonPath().getList("findAll { it.seriesId == '" + seriesId + "' }");
    }

    @SuppressWarnings("unchecked")
    private static Object preTraining(Map<String, Object> event, String property) {
        Map<String, Object> pre = (Map<String, Object>) event.get("preTraining");
        return pre == null ? null : pre.get(property);
    }

    @Test
    void ter30SeriesGivesItsEventsThePreTrainingAtTheRightMoment() {
        String id = createSeries(series("19:30", JOGGEN_1910));
        given().cookie("stv_session", petra).get("/api/series/" + id).then().statusCode(200)
                .body("preTraining.title", equalTo("Joggen")).body("preTraining.startTime", equalTo("19:10"));
        List<Map<String, Object>> events = eventsOf(id);
        assertEquals(List.of("2026-11-16T19:10:00+01:00", "2026-11-23T19:10:00+01:00", "2026-11-30T19:10:00+01:00",
                "2026-12-07T19:10:00+01:00"), events.stream().map(e -> preTraining(e, "start")).toList());
        assertEquals(List.of("Joggen", "Joggen", "Joggen", "Joggen"),
                events.stream().map(e -> preTraining(e, "title")).toList());
        // The overview of the series of a team shows it as well
        given().cookie("stv_session", petra).get("/api/series?team=" + TEAM_1).then().statusCode(200)
                .body("series.preTraining.startTime", hasItem("19:10"));
    }

    @Test
    void ter30ChangingTheSeriesChangesUpcomingEventsButNotAnIndividuallyChangedOne() {
        String id = createSeries(series("19:30", JOGGEN_1910));
        String changedByHand = (String) eventsOf(id).get(1).get("id");
        update(changedByHand, event("PRACTICE", "2026-11-23T19:30:00+01:00", "2026-11-23T21:00:00+01:00",
                "{\"title\":\"Dehnen\",\"start\":\"2026-11-23T19:00:00+01:00\"}")).statusCode(200);

        updateSeries(id, series("19:30", "{\"title\":\"Kraft\",\"startTime\":\"18:45\"}")).statusCode(200)
                .body("preTraining.title", equalTo("Kraft")).body("preTraining.startTime", equalTo("18:45"));
        List<Map<String, Object>> events = eventsOf(id);
        assertEquals(List.of("Kraft", "Dehnen", "Kraft", "Kraft"),
                events.stream().map(e -> preTraining(e, "title")).toList());
        assertEquals("2026-11-23T19:00:00+01:00", preTraining(events.get(1), "start"));
        assertEquals("2026-11-30T18:45:00+01:00", preTraining(events.get(2), "start"));
        // A later start of the series with a later time of the pre-training: the events follow
        updateSeries(id, series("20:00", "{\"title\":\"Kraft\",\"startTime\":\"19:30\"}")).statusCode(200);
        assertEquals("2026-11-16T19:30:00+01:00", preTraining(eventsOf(id).get(0), "start"));
        assertEquals("2026-11-23T19:00:00+01:00", preTraining(eventsOf(id).get(1), "start"));
    }

    @Test
    void ter30RemovingItOnTheSeriesRemovesItOnTheEvents() {
        String id = createSeries(series("19:30", JOGGEN_1910));
        updateSeries(id, series("19:30", null)).statusCode(200).body("preTraining", nullValue());
        List<Map<String, Object>> events = eventsOf(id);
        assertEquals(4, events.size());
        assertTrue(events.stream().noneMatch(e -> e.containsKey("preTraining")));
        given().cookie("stv_session", petra).get("/api/series/" + id).then().body("preTraining", nullValue());
    }

    @Test
    void ter19ThePreviewCountsAPreTrainingAsAChange() {
        String id = createSeries(series("19:30", null));
        preview(id, series("19:30", null)).body("adjusted", equalTo(0));
        preview(id, series("19:30", JOGGEN_1910)).body("adjusted", equalTo(4)).body("from", equalTo("2026-11-16"));
        // Once stored, the same pre-training is no change; a different time or title is
        updateSeries(id, series("19:30", JOGGEN_1910)).statusCode(200);
        preview(id, series("19:30", JOGGEN_1910)).body("adjusted", equalTo(0));
        preview(id, series("19:30", "{\"title\":\"Joggen\",\"startTime\":\"19:00\"}")).body("adjusted", equalTo(4));
        preview(id, series("19:30", "{\"title\":\"Laufen\",\"startTime\":\"19:10\"}")).body("adjusted", equalTo(4));
        preview(id, series("19:30", null)).body("adjusted", equalTo(4));
    }

    @Test
    void ter30InvalidPreTrainingOfASeriesIs400() {
        postSeries(series("19:30", "{\"title\":\"Joggen\",\"startTime\":\"19:30\"}"))
                .statusCode(400).body("fields[0].field", equalTo("preTraining.startTime"))
                .body("fields[0].message", equalTo("Das Vortraining muss vor dem Training beginnen."));
        postSeries(series("19:30", "{\"title\":\"Joggen\",\"startTime\":\"abends\"}"))
                .statusCode(400).body("fields[0].field", equalTo("preTraining.startTime"));
        postSeries(series("19:30", "{\"title\":\" \",\"startTime\":\"19:00\"}"))
                .statusCode(400).body("fields[0].field", equalTo("preTraining.title"));
        postSeries(series("19:30", "{\"title\":\"Joggen\",\"startTime\":\"19:00\"}").replace("PRACTICE", "OTHER"))
                .statusCode(400).body("fields[0].field", equalTo("preTraining.title"))
                .body("fields[0].message", containsString("nur bei Trainings"));
        // The preview refuses it as well
        String id = createSeries(series("19:30", null));
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(series("19:30", "{\"title\":\"Joggen\",\"startTime\":\"20:00\"}"))
                .post("/api/series/" + id + "/preview").then().statusCode(400);
    }

    @Test
    void ter30SeriesWithoutPreTrainingHasNoneOnItsEvents() {
        String id = createSeries(series("19:30", null));
        List<Map<String, Object>> events = eventsOf(id);
        assertEquals(4, events.size());
        assertTrue(events.stream().noneMatch(e -> e.containsKey("preTraining")));
        given().cookie("stv_session", petra).get("/api/events/" + events.get(0).get("id")).then()
                .body("preTraining", nullValue());
    }
}
