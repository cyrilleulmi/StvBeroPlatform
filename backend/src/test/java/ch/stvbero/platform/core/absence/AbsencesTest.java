package ch.stvbero.platform.core.absence;

import ch.stvbero.platform.core.testsupport.Seed;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

/**
 * Absences (ABS-01 to ABS-07) against the seed of docs/test-data.md. "Now": Mon 16.11.2026 06:00. Team 1 practises
 * on Tuesday (series 01, with Team 2) and Thursday (series 02). Sara is on holiday 14.–20.12.2026 (seed).
 */
@QuarkusTest
class AbsencesTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    /** Practice Tue 17.11.2026 (series 01): Anna «dabei», Mia «nicht dabei» «Krank». */
    static final String EVENT_01 = "40000000-0000-4000-8000-000000000001";
    /** Practice Thu 19.11.2026 (series 02): only Anna «dabei». */
    static final String EVENT_02 = "40000000-0000-4000-8000-000000000002";
    /** Other event Fri 11.12.2026 «Teamessen». */
    static final String EVENT_06 = "40000000-0000-4000-8000-000000000006";

    static String seriesEvent(int series, String date) {
        return UUID.nameUUIDFromBytes(("termin-" + series + "-" + date).getBytes(StandardCharsets.UTF_8)).toString();
    }

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static ValidatableResponse myResponse(String session, String event) {
        return given().cookie("stv_session", session).get("/api/events/" + event).then().statusCode(200);
    }

    private static String absence(String from, String to, String reason, String note) {
        return """
                {"from":"%s","to":"%s","reason":"%s"%s}""".formatted(from, to, reason,
                note == null ? "" : ",\"note\":\"" + note + "\"");
    }

    private static ValidatableResponse create(String session, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .post("/api/me/absences").then();
    }

    private static void respond(String session, String event, String status) {
        given().cookie("stv_session", session).contentType(ContentType.JSON).body("{\"status\":\"" + status + "\"}")
                .put("/api/events/" + event + "/responses/me").then().statusCode(200);
    }

    // ------------------------------------------------------------------ Seed, ABS-03, ABS-06

    @Test
    void abs03SeedDeclinedSarasPracticesInHerHolidays() {
        String sara = login("sara");
        given().cookie("stv_session", sara).get("/api/me/absences").then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(Seed.SARA_ABSENCE_ID))
                .body("[0].from", equalTo("2026-12-14")).body("[0].to", equalTo("2026-12-20"))
                .body("[0].reason", equalTo("VACATION")).body("[0].note", equalTo("Skiferien"))
                .body("[0].declinedPractices", equalTo(2));
        for (String event : new String[]{seriesEvent(1, "2026-12-15"), seriesEvent(2, "2026-12-17")}) {
            myResponse(sara, event).body("myResponse.status", equalTo("DECLINED"))
                    .body("myResponse.comment", equalTo("Abwesend: Ferien · Skiferien"));
        }
        // Only practices: the «Teamessen» stays open
        myResponse(sara, EVENT_06).body("myResponse.status", equalTo("PENDING"));
    }

    @Test
    void abs03NewAbsenceDeclinesPracticesAndOverridesAttending() {
        String anna = login("anna");
        create(anna, absence("2026-11-17", "2026-11-20", "INJURY", "Knie")).statusCode(201)
                .body("reason", equalTo("INJURY")).body("declinedPractices", equalTo(2));
        myResponse(anna, EVENT_01).body("myResponse.status", equalTo("DECLINED"))
                .body("myResponse.comment", equalTo("Abwesend: Verletzung · Knie"))
                .body("myResponse.lateCancellation", equalTo(false));
        myResponse(anna, EVENT_02).body("myResponse.status", equalTo("DECLINED"));
        // The coach sees the reason in the overview
        given().cookie("stv_session", login("petra")).get("/api/events/" + EVENT_01 + "/overview").then().statusCode(200)
                .body("teams[0].groups.find { it.name == 'Linie 1' }.entries.find { it.person.firstName == 'Anna' }.response.comment",
                        equalTo("Abwesend: Verletzung · Knie"));
    }

    @Test
    void abs03OwnDeclineKeepsItsComment() {
        String mia = login("mia");
        create(mia, absence("2026-11-17", "2026-11-17", "VACATION", null)).statusCode(201)
                .body("declinedPractices", equalTo(0));
        myResponse(mia, EVENT_01).body("myResponse.status", equalTo("DECLINED")).body("myResponse.comment", equalTo("Krank"));
    }

    // ------------------------------------------------------------------ ABS-04 later responses and events

    @Test
    void abs04ResponseByHandWinsAfterwards() {
        String anna = login("anna");
        String id = create(anna, absence("2026-11-17", "2026-11-20", "VACATION", null)).statusCode(201)
                .extract().path("id");
        respond(anna, EVENT_02, "ATTENDING");
        // Changing the note keeps the response given by hand; the other practice follows the new note
        given().cookie("stv_session", anna).contentType(ContentType.JSON)
                .body(absence("2026-11-17", "2026-11-20", "VACATION", "Kurztrip"))
                .put("/api/me/absences/" + id).then().statusCode(200).body("declinedPractices", equalTo(1));
        myResponse(anna, EVENT_02).body("myResponse.status", equalTo("ATTENDING"));
        myResponse(anna, EVENT_01).body("myResponse.comment", equalTo("Abwesend: Ferien · Kurztrip"));
    }

    @Test
    void abs04PracticeCreatedOrMovedLaterFollowsTheAbsence() {
        String mia = login("mia");
        create(mia, absence("2026-11-30", "2026-12-02", "VACATION", null)).statusCode(201);
        String petra = login("petra");
        String body = """
                {"type":"PRACTICE","title":"Zusatztraining","start":"2026-12-01T19:00:00+01:00","end":"2026-12-01T20:30:00+01:00",
                 "teamIds":["%s"],"responseDeadlineHours":24}""".formatted(TEAM_1);
        String event = given().cookie("stv_session", petra).contentType(ContentType.JSON).body(body)
                .post("/api/events").then().statusCode(201).extract().path("id");
        myResponse(mia, event).body("myResponse.status", equalTo("DECLINED"))
                .body("myResponse.comment", equalTo("Abwesend: Ferien"));
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(body.replace("2026-12-01", "2026-12-03")).put("/api/events/" + event).then().statusCode(200);
        myResponse(mia, event).body("myResponse.status", equalTo("PENDING"));
    }

    // ------------------------------------------------------------------ ABS-05 change and delete

    @Test
    void abs05ShorterOrDeletedAbsenceReopensItsPractices() {
        String mia = login("mia");
        String id = create(mia, absence("2026-11-18", "2026-11-20", "VACATION", null)).statusCode(201)
                .body("declinedPractices", equalTo(1)).extract().path("id");
        myResponse(mia, EVENT_02).body("myResponse.status", equalTo("DECLINED"));
        given().cookie("stv_session", mia).contentType(ContentType.JSON)
                .body(absence("2026-11-25", "2026-11-27", "VACATION", null))
                .put("/api/me/absences/" + id).then().statusCode(200).body("declinedPractices", equalTo(1));
        myResponse(mia, EVENT_02).body("myResponse.status", equalTo("PENDING"));
        myResponse(mia, seriesEvent(2, "2026-11-26")).body("myResponse.status", equalTo("DECLINED"));

        given().cookie("stv_session", mia).delete("/api/me/absences/" + id).then().statusCode(204);
        myResponse(mia, seriesEvent(2, "2026-11-26")).body("myResponse.status", equalTo("PENDING"));
        given().cookie("stv_session", mia).get("/api/me/absences").then().statusCode(200).body("$", empty());
    }

    // ------------------------------------------------------------------ ABS-02 validation

    @Test
    void abs02InvalidAbsences() {
        String anna = login("anna");
        create(anna, absence("2026-11-20", "2026-11-19", "VACATION", null)).statusCode(400);
        create(anna, absence("2026-11-01", "2026-11-10", "VACATION", null)).statusCode(400);
        create(anna, absence("2026-11-20", "2026-11-21", "OTHER", null)).statusCode(422).body("rule", equalTo("ABS-02"));
        create(anna, absence("2026-11-20", "2026-11-25", "VACATION", null)).statusCode(201);
        create(anna, absence("2026-11-25", "2026-11-28", "INJURY", null)).statusCode(409).body("rule", equalTo("ABS-02"));
        create(anna, "{\"from\":\"2026-11-20\",\"reason\":\"VACATION\"}").statusCode(400);
    }

    // ------------------------------------------------------------------ RR-07

    @Test
    void rr07OnlyOwnAbsences() {
        String anna = login("anna");
        given().get("/api/me/absences").then().statusCode(401);
        given().cookie("stv_session", anna).get("/api/me/absences").then().statusCode(200).body("$", empty());
        given().cookie("stv_session", anna).contentType(ContentType.JSON)
                .body(absence("2026-12-14", "2026-12-15", "VACATION", null))
                .put("/api/me/absences/" + Seed.SARA_ABSENCE_ID).then().statusCode(404);
        given().cookie("stv_session", anna).delete("/api/me/absences/" + Seed.SARA_ABSENCE_ID).then().statusCode(404);
        // Not even a coach changes someone else's absence
        given().cookie("stv_session", login("petra")).delete("/api/me/absences/" + Seed.SARA_ABSENCE_ID)
                .then().statusCode(404);
        given().cookie("stv_session", login("sara")).get("/api/me/absences").then().statusCode(200)
                .body("$", hasSize(1)).body("[0].note", equalTo("Skiferien"));
    }

    @Test
    void abs07AbsenceIsNotAResponseOfOthers() {
        // Nina has no absence: her response to Sara's holiday practice stays open
        myResponse(login("nina"), seriesEvent(1, "2026-12-15")).body("myResponse.status", equalTo("PENDING"))
                .body("myResponse.comment", nullValue());
    }
}
