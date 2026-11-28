package ch.stvbero.platform.attendance.event;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Responses to the pre-training of a practice (ANM-14 to ANM-17, E-119). IDs from docs/test-data.md: Anna and Lea
 * play in Team 1, Petra coaches it. Event 02 is a practice of Team 1 without a pre-training.
 */
@QuarkusTest
class PreTrainingResponsesTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String LEA = "20000000-0000-4000-8000-000000000006";
    static final String T02 = "40000000-0000-4000-8000-000000000002";
    static final String ANNA_FEED = "/api/calendar/seed-anna-calendar-000000000000000000000005.ics";

    static final String JOGGEN = "{\"title\":\"Joggen\",\"start\":\"2026-11-20T18:40:00+01:00\"}";

    String petra;
    String anna;
    String practice;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
        petra = login("petra");
        anna = login("anna");
        practice = given().cookie("stv_session", petra).contentType(ContentType.JSON).body(event(JOGGEN))
                .post("/api/events").then().statusCode(201).extract().path("id");
    }

    private static String event(String preTraining) {
        return """
                {"type":"PRACTICE","title":"Training","start":"2026-11-20T19:00:00+01:00",
                 "end":"2026-11-20T20:30:00+01:00","teamIds":["%s"],"location":{"name":"Turnhalle Dorf"}%s}"""
                .formatted(TEAM_1, preTraining == null ? "" : ",\"preTraining\":" + preTraining);
    }

    private static ValidatableResponse respond(String session, String event, String person, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .put("/api/events/" + event + "/responses/" + person).then();
    }

    private static ValidatableResponse read(String session, String event) {
        return given().cookie("stv_session", session).get("/api/events/" + event).then().statusCode(200);
    }

    private static JsonPath overview(String session, String event) {
        return given().cookie("stv_session", session).get("/api/events/" + event + "/overview").then().statusCode(200)
                .extract().jsonPath();
    }

    private static String ofPerson(String person) {
        return "teams[0].groups.entries.flatten().find { it.person.id == '" + person + "' }.response.preTraining";
    }

    // ------------------------------------------------------------------ ANM-14: an answer of its own

    @Test
    void anm14DabeiLeavesThePreTrainingOpenUntilThePersonAnswers() {
        read(anna, practice).body("myResponse.status", equalTo("PENDING"))
                .body("myResponse.preTraining", equalTo("PENDING"));
        // «dabei» at the practice signs nobody up for the pre-training (ANM-06)
        respond(anna, practice, "me", "{\"status\":\"ATTENDING\"}").statusCode(200)
                .body("status", equalTo("ATTENDING")).body("preTraining", equalTo("PENDING"));
        respond(anna, practice, "me", "{\"status\":\"ATTENDING\",\"preTraining\":\"ATTENDING\"}").statusCode(200)
                .body("preTraining", equalTo("ATTENDING"));
        read(anna, practice).body("myResponse.preTraining", equalTo("ATTENDING"));
        // Missing: unchanged
        respond(anna, practice, "me", "{\"status\":\"ATTENDING\"}").statusCode(200)
                .body("preTraining", equalTo("ATTENDING"));
        respond(anna, practice, "me", "{\"status\":\"ATTENDING\",\"preTraining\":\"DECLINED\"}").statusCode(200)
                .body("status", equalTo("ATTENDING")).body("preTraining", equalTo("DECLINED"));
        given().cookie("stv_session", anna).get("/api/events?type=PRACTICE").then().statusCode(200)
                .body("find { it.id == '" + practice + "' }.myResponse.preTraining", equalTo("DECLINED"));
    }

    @Test
    void anm14AnEventWithoutAPreTrainingHasNoSuchAnswer() {
        read(anna, T02).body("myResponse.containsKey('preTraining')", equalTo(false))
                .body("preTrainingCounts", nullValue());
        respond(anna, T02, "me", "{\"status\":\"ATTENDING\",\"preTraining\":\"ATTENDING\"}").statusCode(400)
                .body("fields[0].field", equalTo("preTraining"))
                .body("fields[0].message", equalTo(PreTrainingResponseRules.NONE));
        respond(anna, T02, "me", "{\"status\":\"ATTENDING\"}").statusCode(200)
                .body("containsKey('preTraining')", equalTo(false));
        JsonPath json = overview(petra, T02);
        assertNull(json.get("preTrainingTotal"));
        assertFalse(json.getMap("teams[0]").containsKey("preTrainingCounts"));
    }

    @Test
    void anm14OnlyDabeiAndNichtDabeiAreAnswers() {
        respond(anna, practice, "me", "{\"status\":\"ATTENDING\",\"preTraining\":\"MAYBE\"}").statusCode(400);
        respond(anna, practice, "me", "{\"status\":\"ATTENDING\",\"preTraining\":\"PENDING\"}").statusCode(400);
    }

    // ------------------------------------------------------------------ ANM-15: not without the practice

    @Test
    void anm15OnlyThePreTrainingIsNotPossible() {
        respond(anna, practice, "me", "{\"status\":\"DECLINED\",\"preTraining\":\"ATTENDING\"}").statusCode(422)
                .contentType("application/problem+json").body("rule", equalTo("ANM-15"))
                .body("detail", equalTo(PreTrainingResponseRules.NEEDS_PRACTICE))
                .body("fields[0].field", equalTo("preTraining"));
        respond(anna, practice, "me", "{\"status\":\"MAYBE\",\"comment\":\"Schule\",\"preTraining\":\"ATTENDING\"}")
                .statusCode(422).body("rule", equalTo("ANM-15"));
        // Nothing was saved
        read(anna, practice).body("myResponse.status", equalTo("PENDING"));
        // «nicht dabei» at both is fine
        respond(anna, practice, "me", "{\"status\":\"DECLINED\",\"preTraining\":\"DECLINED\"}").statusCode(200)
                .body("preTraining", equalTo("DECLINED"));
    }

    @Test
    void anm15WithoutDabeiItFollowsThePracticeAndIsAskedAgainWithTheNextDabei() {
        respond(anna, practice, "me", "{\"status\":\"ATTENDING\",\"preTraining\":\"ATTENDING\"}").statusCode(200);
        respond(anna, practice, "me", "{\"status\":\"DECLINED\"}").statusCode(200)
                .body("preTraining", equalTo("DECLINED"));
        respond(anna, practice, "me", "{\"status\":\"MAYBE\",\"comment\":\"Schule\"}").statusCode(200)
                .body("preTraining", equalTo("MAYBE"));
        respond(anna, practice, "me", "{\"status\":\"ATTENDING\"}").statusCode(200)
                .body("preTraining", equalTo("PENDING"));
        read(anna, practice).body("myResponse.preTraining", equalTo("PENDING"));
    }

    // ------------------------------------------------------------------ ANM-16: coaches, on behalf

    @Test
    void anm16CoachesAnswerForThemselvesAndOnBehalfOfOthers() {
        // Petra is coach of Team 1: she answers like a player
        respond(petra, practice, "me", "{\"status\":\"ATTENDING\",\"preTraining\":\"DECLINED\"}").statusCode(200)
                .body("preTraining", equalTo("DECLINED"));
        // On behalf of Anna, in one request
        respond(petra, practice, ANNA, "{\"status\":\"ATTENDING\",\"preTraining\":\"ATTENDING\"}").statusCode(200)
                .body("preTraining", equalTo("ATTENDING")).body("enteredBy.id", equalTo(PETRA));
        read(anna, practice).body("myResponse.preTraining", equalTo("ATTENDING"));
        // A player does not answer for another player
        respond(anna, practice, LEA, "{\"status\":\"ATTENDING\",\"preTraining\":\"ATTENDING\"}").statusCode(403);

        JsonPath json = overview(anna, practice);
        assertEquals("DECLINED", json.getString("coaches.find { it.person.id == '" + PETRA + "' }.response.preTraining"));
        assertEquals("ATTENDING", json.getString(ofPerson(ANNA)));
    }

    // ------------------------------------------------------------------ ANM-17: who is at the pre-training

    @Test
    void anm17OverviewAndListCountThePlayersOfThePreTraining() {
        int players = overview(petra, practice).getInt("total.pending");
        respond(anna, practice, "me", "{\"status\":\"ATTENDING\",\"preTraining\":\"ATTENDING\"}").statusCode(200);
        respond(petra, practice, LEA, "{\"status\":\"ATTENDING\"}").statusCode(200);
        // Coaches are not counted (ANM-23)
        respond(petra, practice, "me", "{\"status\":\"ATTENDING\",\"preTraining\":\"ATTENDING\"}").statusCode(200);

        JsonPath json = overview(petra, practice);
        assertEquals(2, json.getInt("total.attending"));
        assertEquals(1, json.getInt("preTrainingTotal.attending"));
        assertEquals(0, json.getInt("preTrainingTotal.declined"));
        assertEquals(0, json.getInt("preTrainingTotal.maybe"));
        // Lea is «dabei» without an answer to the pre-training: «offen» there
        assertEquals(players - 1, json.getInt("preTrainingTotal.pending"));
        assertEquals(json.getMap("preTrainingTotal"), json.getMap("teams[0].preTrainingCounts"));
        assertEquals("PENDING", json.getString(ofPerson(LEA)));

        respond(petra, practice, LEA, "{\"status\":\"ATTENDING\",\"preTraining\":\"DECLINED\"}").statusCode(200);
        respond(anna, practice, "me", "{\"status\":\"MAYBE\",\"comment\":\"Schule\"}").statusCode(200);
        read(petra, practice)
                .body("counts.attending", equalTo(1)).body("counts.maybe", equalTo(1))
                .body("preTrainingCounts.attending", equalTo(0)).body("preTrainingCounts.declined", equalTo(1))
                .body("preTrainingCounts.maybe", equalTo(1)).body("preTrainingCounts.pending", equalTo(players - 2));
        given().cookie("stv_session", petra).get("/api/events?type=PRACTICE").then().statusCode(200)
                .body("find { it.id == '" + practice + "' }.preTrainingCounts.declined", equalTo(1))
                .body("find { it.id == '" + T02 + "' }.containsKey('preTrainingCounts')", equalTo(false));
    }

    // ------------------------------------------------------------------ TER-29: removed with the pre-training

    @Test
    void ter29RemovingThePreTrainingRemovesItsResponses() {
        respond(anna, practice, "me", "{\"status\":\"ATTENDING\",\"preTraining\":\"ATTENDING\"}").statusCode(200);
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(event(null))
                .put("/api/events/" + practice).then().statusCode(200).body("preTraining", nullValue());
        read(anna, practice).body("myResponse.status", equalTo("ATTENDING"))
                .body("myResponse.containsKey('preTraining')", equalTo(false));
        // A new pre-training starts without answers
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(event("{\"title\":\"Dehnen\",\"start\":\"2026-11-20T18:30:00+01:00\"}"))
                .put("/api/events/" + practice).then().statusCode(200);
        read(anna, practice).body("myResponse.status", equalTo("ATTENDING"))
                .body("myResponse.preTraining", equalTo("PENDING"));
    }

    // ------------------------------------------------------------------ TER-31: calendar subscription

    @Test
    void ter31TheCalendarEntryBeginsWithThePracticeForSomeoneWhoSkipsThePreTraining() {
        respond(anna, practice, "me", "{\"status\":\"ATTENDING\",\"preTraining\":\"ATTENDING\"}").statusCode(200);
        String with = given().get(ANNA_FEED).then().statusCode(200).extract().asString();
        assertTrue(with.contains("DTSTART;TZID=Europe/Zurich:20261120T184000"), with);

        respond(anna, practice, "me", "{\"status\":\"ATTENDING\",\"preTraining\":\"DECLINED\"}").statusCode(200);
        given().get(ANNA_FEED).then().statusCode(200)
                .body(containsString("DTSTART;TZID=Europe/Zurich:20261120T190000"))
                .body(not(containsString("DTSTART;TZID=Europe/Zurich:20261120T184000")));
    }
}
