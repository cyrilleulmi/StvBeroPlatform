package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.error.Concurrently;
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
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;

/** F-14: attendance check per event (PRS-01 to PRS-10, E-70). IDs from docs/test-data.md. */
@QuarkusTest
class AttendanceTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String MARC = "20000000-0000-4000-8000-000000000003";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String LEA = "20000000-0000-4000-8000-000000000006";
    static final String MIA = "20000000-0000-4000-8000-000000000007";
    static final String SARA = "20000000-0000-4000-8000-000000000008";
    static final String NINA = "20000000-0000-4000-8000-000000000009";
    static final String JANA = "20000000-0000-4000-8000-000000000010";
    static final String KIM = "20000000-0000-4000-8000-000000000011";
    static final String TINA = "20000000-0000-4000-8000-000000000016";
    static final String T01 = "40000000-0000-4000-8000-000000000001";
    static final String T09 = "40000000-0000-4000-8000-000000000009";
    static final String T10 = "40000000-0000-4000-8000-000000000010";

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static ValidatableResponse event(String session, String id) {
        return given().cookie("stv_session", session).get("/api/events/" + id).then();
    }

    private static ValidatableResponse attendance(String session, String id) {
        return given().cookie("stv_session", session).get("/api/events/" + id + "/attendance").then();
    }

    private static ValidatableResponse record(String session, String id, Map<String, String> entries) {
        String body = "{\"entries\":[" + String.join(",", entries.entrySet().stream()
                .map(e -> "{\"personId\":\"" + e.getKey() + "\",\"status\":\"" + e.getValue() + "\"}").toList()) + "]}";
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .put("/api/events/" + id + "/attendance").then();
    }

    private static void clock(String now) {
        given().contentType(ContentType.JSON).body("{\"now\":\"" + now + "\"}").put("/api/test/clock").then().statusCode(204);
    }

    private static String eventBody(String start, String end, Boolean attendanceCheck) {
        return """
                {"type":"PRACTICE","title":"Zusatztraining","start":"%s","end":"%s","teamIds":["%s"]%s}"""
                .formatted(start, end, TEAM_1, attendanceCheck == null ? "" : ",\"attendanceCheck\":" + attendanceCheck);
    }

    private static String createEvent(String session, Boolean attendanceCheck) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON)
                .body(eventBody("2026-11-20T18:00:00+01:00", "2026-11-20T20:00:00+01:00", attendanceCheck))
                .post("/api/events").then().statusCode(201).extract().path("id");
    }

    private static ValidatableResponse updateEvent(String session, String id, Boolean attendanceCheck) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON)
                .body(eventBody("2026-11-20T18:00:00+01:00", "2026-11-20T20:00:00+01:00", attendanceCheck))
                .put("/api/events/" + id).then();
    }

    /** Status per person name in the attendance list. */
    private static Map<String, String> statuses(JsonPath json) {
        List<String> names = json.getList("teams.groups.flatten().entries.flatten().person.firstName");
        List<String> values = json.getList("teams.groups.flatten().entries.flatten().status");
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            result.put(names.get(i), values.get(i));
        }
        return result;
    }

    // ------------------------------------------------------------------ PRS-01 switch

    @Test
    void prs01SwitchIsOffByDefaultAndCanBeSet() {
        String petra = login("petra");
        String off = createEvent(petra, null);
        event(petra, off).statusCode(200).body("attendanceCheck", equalTo(false));
        String on = createEvent(petra, true);
        event(petra, on).statusCode(200).body("attendanceCheck", equalTo(true));
        // Without the field an existing event keeps its value; false switches it off
        updateEvent(petra, on, null).statusCode(200).body("attendanceCheck", equalTo(true));
        updateEvent(petra, on, false).statusCode(200).body("attendanceCheck", equalTo(false));
        // Seed: series events are off, events 09 and 10 on
        event(petra, T01).body("attendanceCheck", equalTo(false));
        event(petra, T09).body("attendanceCheck", equalTo(true));
    }

    @Test
    void prs01SeriesPassesTheSwitchOnAndASingleEventCanDeviate() {
        String petra = login("petra");
        String body = """
                {"type":"PRACTICE","title":"Konditionstraining","teamIds":["%s"],"weekday":"FRIDAY","startTime":"18:00",
                 "endTime":"19:30","validFrom":"2026-11-20","validUntil":"2026-12-11"%s}""";
        JsonPath series = given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(body.formatted(TEAM_1, ",\"attendanceCheck\":true"))
                .post("/api/series").then().statusCode(201).body("attendanceCheck", equalTo(true))
                .extract().jsonPath();
        String seriesId = series.getString("id");
        List<String> ids = given().cookie("stv_session", petra)
                .get("/api/events?team=" + TEAM_1 + "&from=2026-11-20&to=2026-12-11").then().statusCode(200)
                .extract().jsonPath().getList("findAll { it.seriesId == '" + seriesId + "' }.id");
        assertThat(ids.size(), equalTo(4));
        for (String id : ids) {
            event(petra, id).body("attendanceCheck", equalTo(true));
        }
        // The first event deviates
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(eventBody("2026-11-20T18:00:00+01:00", "2026-11-20T19:30:00+01:00", false))
                .put("/api/events/" + ids.get(0)).then().statusCode(200).body("attendanceCheck", equalTo(false));
        // Switching the series off applies to the others, the deviating event stays as it is
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(body.formatted(TEAM_1, ",\"attendanceCheck\":false"))
                .put("/api/series/" + seriesId).then().statusCode(200).body("attendanceCheck", equalTo(false));
        event(petra, ids.get(1)).body("attendanceCheck", equalTo(false));
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(body.formatted(TEAM_1, ",\"attendanceCheck\":true"))
                .put("/api/series/" + seriesId).then().statusCode(200);
        event(petra, ids.get(0)).body("attendanceCheck", equalTo(false));
        event(petra, ids.get(1)).body("attendanceCheck", equalTo(true));
        // Without the field the series keeps its value
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(body.formatted(TEAM_1, ""))
                .put("/api/series/" + seriesId).then().statusCode(200).body("attendanceCheck", equalTo(true));
    }

    // ------------------------------------------------------------------ PRS-02 from the start on

    @Test
    void prs02OnlyFromTheStartOn() {
        String petra = login("petra");
        String id = createEvent(petra, true);
        event(petra, id).body("canRecordAttendance", equalTo(false));
        attendance(petra, id).statusCode(409).body("rule", equalTo("PRS-02"));
        record(petra, id, Map.of(ANNA, "PRESENT")).statusCode(409).body("rule", equalTo("PRS-02"));
        clock("2026-11-20T18:00:00+01:00");
        event(petra, id).body("canRecordAttendance", equalTo(true));
        attendance(petra, id).statusCode(200);
        // Long after the end it is still possible (assumption PRS-02)
        clock("2027-01-20T18:00:00+01:00");
        event(petra, id).body("canRecordAttendance", equalTo(true));
        record(petra, id, Map.of(ANNA, "PRESENT")).statusCode(200);
    }

    // ------------------------------------------------------------------ PRS-03 list and preselection

    @Test
    void prs03ListByLineWithResponseAndPreselection() {
        JsonPath json = attendance(login("petra"), T09).statusCode(200).extract().jsonPath();
        assertThat(json.getBoolean("recorded"), equalTo(false));
        assertThat(json.get("recordedBy"), nullValue());
        assertThat(json.getList("teams[0].groups.name"), contains("Linie 1", "Linie 2", "Goalies", "Ohne Linie"));
        assertThat(json.getList("teams[0].groups.type"), contains("LINE", "LINE", "GOALIES", "NO_LINE"));
        assertThat(json.getList("teams[0].groups[0].entries.person.firstName"), contains("Mia", "Lea", "Anna"));
        assertThat(json.getList("teams[0].groups[1].entries.person.firstName"), contains("Sara", "Nina"));
        // «dabei» → anwesend, everything else (nicht dabei, vielleicht, offen) → abwesend
        Map<String, String> statuses = statuses(json);
        assertThat(statuses, equalTo(Map.of("Anna", "PRESENT", "Lea", "PRESENT", "Mia", "ABSENT", "Sara", "PRESENT",
                "Nina", "ABSENT", "Jana", "PRESENT", "Kim", "ABSENT")));
        assertThat(json.getList("teams.groups.flatten().entries.flatten().recorded"), everyItem(equalTo(false)));
        // Response next to the name, with comment for the coach
        assertThat(json.getString("teams[0].groups[0].entries.find { it.person.firstName == 'Mia' }.response.status"), equalTo("DECLINED"));
        assertThat(json.getString("teams[0].groups[0].entries.find { it.person.firstName == 'Mia' }.response.comment"), equalTo("Krank"));
        assertThat(json.getString("teams[0].groups[3].entries[0].response.status"), equalTo("PENDING"));
        // Nothing saved by looking at it
        event(login("petra"), T09).body("attendanceRecorded", equalTo(false));
    }

    @Test
    void prs03SharedEventIsGroupedByTeamThenLine() {
        String petra = login("petra");
        String id = given().cookie("stv_session", petra).contentType(ContentType.JSON).body("""
                        {"type":"PRACTICE","title":"Gemeinsames Training","start":"2026-11-20T18:00:00+01:00",
                         "end":"2026-11-20T20:00:00+01:00","teamIds":["%s","%s"],"attendanceCheck":true}"""
                        .formatted(TEAM_2, TEAM_1))
                .post("/api/events").then().statusCode(201).extract().path("id");
        clock("2026-11-20T19:00:00+01:00");
        JsonPath json = attendance(petra, id).statusCode(200).extract().jsonPath();
        // Teams by name as in the overview, each with its own lines
        assertThat(json.getList("teams.team.name"), contains("Team 1", "Team 2"));
        assertThat(json.getList("teams.team.id"), contains(TEAM_1, TEAM_2));
        assertThat(json.getList("teams[0].groups.name"), contains("Linie 1", "Linie 2", "Goalies", "Ohne Linie"));
        assertThat(json.getList("teams[0].groups[0].entries.person.id"), hasItem(LEA));
        assertThat(json.getList("teams[1].groups.find { it.name == 'Linie 2' }.entries.person.id"), hasItem(LEA));
        // Lea is in both teams and has one status in both
        JsonPath saved = record(petra, id, Map.of(LEA, "EXCUSED")).statusCode(200).extract().jsonPath();
        assertThat(saved.getList("teams.groups.flatten().entries.flatten().findAll { it.person.id == '%s' }.status"
                .formatted(LEA)), contains("EXCUSED", "EXCUSED"));
    }

    // ------------------------------------------------------------------ PRS-04 save and correct

    @Test
    void prs04SaveStoresWhoAndWhenAndCanBeCorrected() {
        clock("2026-11-12T21:00:00+01:00");
        String petra = login("petra");
        JsonPath saved = record(petra, T09, Map.of(ANNA, "PRESENT", MIA, "EXCUSED", SARA, "ABSENT", KIM, "PRESENT"))
                .statusCode(200).extract().jsonPath();
        assertThat(saved.getBoolean("recorded"), equalTo(true));
        assertThat(saved.getString("recordedBy.id"), equalTo(PETRA));
        assertThat(saved.getString("recordedAt"), equalTo("2026-11-12T21:00:00+01:00"));
        Map<String, String> statuses = statuses(saved);
        assertThat(statuses.get("Mia"), equalTo("EXCUSED"));
        assertThat(statuses.get("Sara"), equalTo("ABSENT"));
        assertThat(statuses.get("Kim"), equalTo("PRESENT"));
        // Not saved: still the preselection
        assertThat(statuses.get("Lea"), equalTo("PRESENT"));
        assertThat(saved.getBoolean("teams[0].groups[0].entries.find { it.person.firstName == 'Lea' }.recorded"), equalTo(false));

        // Correction later by Marc (coach of another team, E-69): the last value counts
        clock("2026-11-13T08:00:00+01:00");
        JsonPath corrected = record(login("marc"), T09, Map.of(SARA, "EXCUSED")).statusCode(200).extract().jsonPath();
        assertThat(corrected.getString("recordedBy.id"), equalTo(MARC));
        assertThat(corrected.getString("recordedAt"), equalTo("2026-11-13T08:00:00+01:00"));
        assertThat(statuses(corrected).get("Sara"), equalTo("EXCUSED"));
        assertThat(statuses(corrected).get("Mia"), equalTo("EXCUSED"));
        assertThat(statuses(attendance(login("alex"), T09).statusCode(200).extract().jsonPath()).get("Sara"), equalTo("EXCUSED"));

        // Responses stay what they were (intention and fact are separate)
        event(login("sara"), T09).body("myResponse.status", equalTo("ATTENDING"));
        event(login("mia"), T09).body("myResponse.status", equalTo("DECLINED"));
    }

    @Test
    void prs04SeedEvent10IsRecordedByPetra() {
        JsonPath json = attendance(login("petra"), T10).statusCode(200).extract().jsonPath();
        assertThat(json.getBoolean("recorded"), equalTo(true));
        assertThat(json.getString("recordedBy.firstName"), equalTo("Petra"));
        assertThat(json.getString("recordedAt"), equalTo("2026-11-05T21:00:00+01:00"));
        assertThat(statuses(json), equalTo(Map.of("Anna", "PRESENT", "Lea", "PRESENT", "Mia", "EXCUSED", "Sara", "ABSENT",
                "Nina", "PRESENT", "Jana", "PRESENT", "Kim", "ABSENT")));
        assertThat(json.getList("teams.groups.flatten().entries.flatten().recorded"), everyItem(equalTo(true)));
    }

    @Test
    void duplicatePersonGives400() {
        given().cookie("stv_session", login("petra")).contentType(ContentType.JSON).body("""
                        {"entries":[{"personId":"%s","status":"PRESENT"},{"personId":"%s","status":"ABSENT"}]}"""
                        .formatted(ANNA, ANNA))
                .put("/api/events/" + T09 + "/attendance").then().statusCode(400);
        given().cookie("stv_session", login("petra")).contentType(ContentType.JSON).body("{\"entries\":[{\"personId\":\"" + ANNA + "\"}]}")
                .put("/api/events/" + T09 + "/attendance").then().statusCode(400);
    }

    @Test
    void concurrentSaveNeverGives500() {
        String petra = login("petra");
        List<Integer> codes = Concurrently.statusCodes(4,
                () -> record(petra, T09, Map.of(ANNA, "PRESENT")).extract().statusCode());
        assertThat(codes, everyItem(equalTo(200)));
    }

    // ------------------------------------------------------------------ PRS-05 no coaches

    @Test
    void prs05CoachesAreNotPartOfTheList() {
        String petra = login("petra");
        JsonPath json = attendance(petra, T09).extract().jsonPath();
        assertThat(json.getList("teams.groups.flatten().entries.flatten().person.id"), not(hasItem(PETRA)));
        record(petra, T09, Map.of(PETRA, "PRESENT")).statusCode(422).body("rule", equalTo("PRS-05"));
        record(petra, T09, Map.of(TINA, "PRESENT")).statusCode(422).body("rule", equalTo("PRS-05"));
        event(petra, T09).body("attendanceRecorded", equalTo(false));
    }

    // ------------------------------------------------------------------ PRS-06 own attendance only

    @Test
    void prs06PlayersSeeOnlyTheirOwnAttendance() {
        event(login("sara"), T10).statusCode(200).body("myAttendance", equalTo("ABSENT"))
                .body("canRecordAttendance", equalTo(false)).body("$", not(org.hamcrest.Matchers.hasKey("attendanceRecorded")));
        event(login("mia"), T10).body("myAttendance", equalTo("EXCUSED"));
        event(login("anna"), T10).body("myAttendance", equalTo("PRESENT"));
        // Nothing recorded: nothing shown
        event(login("anna"), T09).body("myAttendance", nullValue());
        // No access to the list of others
        String anna = login("anna");
        attendance(anna, T10).statusCode(403);
        record(anna, T10, Map.of(ANNA, "PRESENT")).statusCode(403);
        // The overview contains no attendance
        given().cookie("stv_session", anna).get("/api/events/" + T10 + "/overview").then().statusCode(200)
                .body(not(org.hamcrest.Matchers.containsString("EXCUSED")));
        // Coach: no own attendance, but knows that something is recorded
        event(login("petra"), T10).body("myAttendance", nullValue()).body("attendanceRecorded", equalTo(true))
                .body("canRecordAttendance", equalTo(true));
    }

    // ------------------------------------------------------------------ PRS-07 switched off

    @Test
    void prs07SwitchedOffGives409() {
        clock("2026-11-17T19:00:00+01:00");
        String petra = login("petra");
        event(petra, T01).body("canRecordAttendance", equalTo(false));
        attendance(petra, T01).statusCode(409).body("rule", equalTo("PRS-07"));
        record(petra, T01, Map.of(ANNA, "PRESENT")).statusCode(409).body("rule", equalTo("PRS-07"));
    }

    // ------------------------------------------------------------------ PRS-08 switch off keeps the data

    @Test
    void prs08SwitchingOffKeepsTheData() {
        String petra = login("petra");
        String id = createEvent(petra, true);
        clock("2026-11-20T19:00:00+01:00");
        record(petra, id, Map.of(ANNA, "EXCUSED")).statusCode(200);
        updateEvent(petra, id, false).statusCode(200).body("attendanceCheck", equalTo(false))
                .body("attendanceRecorded", equalTo(true)).body("canRecordAttendance", equalTo(false));
        event(login("anna"), id).body("myAttendance", nullValue());
        updateEvent(petra, id, true).statusCode(200);
        assertThat(statuses(attendance(petra, id).statusCode(200).extract().jsonPath()).get("Anna"), equalTo("EXCUSED"));
        event(login("anna"), id).body("myAttendance", equalTo("EXCUSED"));
    }

    // ------------------------------------------------------------------ PRS-09 cancelled

    @Test
    void prs09CancelledEventTakesNoAttendance() {
        String petra = login("petra");
        String id = createEvent(petra, true);
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{\"reason\":\"Halle besetzt\"}")
                .post("/api/events/" + id + "/cancel").then().statusCode(200);
        clock("2026-11-20T19:00:00+01:00");
        event(petra, id).body("canRecordAttendance", equalTo(false));
        attendance(petra, id).statusCode(409).body("rule", equalTo("PRS-09"));
        record(petra, id, Map.of(ANNA, "PRESENT")).statusCode(409).body("rule", equalTo("PRS-09"));
    }

    // ------------------------------------------------------------------ PRS-10 permissions

    @Test
    void prs10CoachesOfAnyTeamAndAdminsMayRecord() {
        attendance(login("marc"), T09).statusCode(200);
        attendance(login("ruth"), T09).statusCode(200);
        record(login("alex"), T09, Map.of(JANA, "PRESENT", NINA, "ABSENT", LEA, "PRESENT")).statusCode(200)
                .body("recordedBy.firstName", equalTo("Alex"));
        attendance(login("anna"), T09).statusCode(403).body("rule", equalTo("RR-07"));
        given().get("/api/events/" + T09 + "/attendance").then().statusCode(401);
        given().cookie("stv_session", login("petra")).get("/api/events/40000000-0000-4000-8000-000000000099/attendance")
                .then().statusCode(404);
    }
}
