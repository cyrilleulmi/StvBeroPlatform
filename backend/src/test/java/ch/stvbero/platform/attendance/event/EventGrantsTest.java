package ch.stvbero.platform.attendance.event;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Special permissions per event and series (RR-09 to RR-14) with the seed of docs/test-data.md: Kim is a player of
 * Team 1 without coach rights; event 01 is a practice of Team 1 + Team 2 from series 01.
 */
@QuarkusTest
class EventGrantsTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String T01 = "40000000-0000-4000-8000-000000000001";
    static final String T02 = "40000000-0000-4000-8000-000000000002";
    static final String SERIES_1 = "70000000-0000-4000-8000-000000000001";
    static final String KIM = "20000000-0000-4000-8000-000000000011";
    static final String NINA = "20000000-0000-4000-8000-000000000009";
    static final String RUTH = "20000000-0000-4000-8000-000000000004";

    static final String T01_BODY = """
            {"type":"PRACTICE","title":"Training","start":"2026-11-17T18:30:00+01:00","end":"2026-11-17T20:00:00+01:00",
             "teamIds":["%s","%s"],"location":{"name":"Sporthalle Nord"},"notes":"Bälle mitnehmen"}""";

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    static String seriesEvent(String date) {
        return UUID.nameUUIDFromBytes(("termin-1-" + date).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static ValidatableResponse grant(String session, String path, String... personIds) {
        String ids = String.join("\",\"", personIds);
        return given().cookie("stv_session", session).contentType(ContentType.JSON)
                .body("{\"personIds\":[" + (personIds.length == 0 ? "" : "\"" + ids + "\"") + "]}")
                .put(path + "/grants").then();
    }

    private static ValidatableResponse update(String session, String event, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .put("/api/events/" + event).then();
    }

    private static List<Map<String, Object>> notifications(String type) {
        JsonPath json = given().get("/api/test/notifications").then().statusCode(200).extract().jsonPath();
        return json.getList("findAll { it.type == '" + type + "' && !it.id.startsWith('90000000') }");
    }

    @Test
    void rr09GrantGivesCoachRightsForThisEventOnly() {
        String petra = login("petra");
        String kim = login("kim");
        // Without a special permission: player rights
        update(kim, T01, T01_BODY.formatted(TEAM_1, TEAM_2)).statusCode(403);
        given().cookie("stv_session", kim).get("/api/events/" + T01).then().statusCode(200)
                .body("canEdit", equalTo(false)).body("myGrant", nullValue());

        grant(petra, "/api/events/" + T01, KIM).statusCode(200)
                .body("grants.person.firstName", contains("Kim")).body("grants[0].viaSeries", equalTo(false))
                .body("canManage", equalTo(true)).body("candidates.firstName", hasItem("Kim"));

        given().cookie("stv_session", kim).get("/api/events/" + T01).then().statusCode(200)
                .body("canEdit", equalTo(true)).body("myGrant", equalTo(true));
        update(kim, T01, T01_BODY.formatted(TEAM_1, TEAM_2)).statusCode(200).body("notes", equalTo("Bälle mitnehmen"));
        // Respond on behalf and see the reasons (Mia «Krank»)
        given().cookie("stv_session", kim).contentType(ContentType.JSON).body("{\"status\":\"ATTENDING\"}")
                .put("/api/events/" + T01 + "/responses/" + NINA).then().statusCode(200)
                .body("enteredBy.firstName", equalTo("Kim"));
        given().cookie("stv_session", kim).get("/api/events/" + T01 + "/overview").then().statusCode(200)
                .body(containsString("Krank"));
        // Not other events
        given().cookie("stv_session", kim).get("/api/events/" + T02).then().body("canEdit", equalTo(false));
        given().cookie("stv_session", kim).contentType(ContentType.JSON).body("{\"reason\":\"x\"}")
                .post("/api/events/" + T02 + "/cancel").then().statusCode(403);
        // Cancelling works
        given().cookie("stv_session", kim).contentType(ContentType.JSON).body("{\"reason\":\"Halle besetzt\"}")
                .post("/api/events/" + T01 + "/cancel").then().statusCode(200);
    }

    @Test
    void rr11TeamsAndGrantsStayWithCoaches() {
        String petra = login("petra");
        String kim = login("kim");
        grant(petra, "/api/events/" + T01, KIM).statusCode(200);
        update(kim, T01, """
                {"type":"PRACTICE","title":"Training","start":"2026-11-17T18:30:00+01:00","end":"2026-11-17T20:00:00+01:00",
                 "teamIds":["%s"],"location":{"name":"Sporthalle Nord"}}""".formatted(TEAM_1))
                .statusCode(422).body("rule", equalTo("RR-11"));
        grant(kim, "/api/events/" + T01, KIM, NINA).statusCode(403);
        given().cookie("stv_session", kim).get("/api/events/" + T01 + "/grants").then().statusCode(200)
                .body("canManage", equalTo(false)).body("candidates", nullValue());
    }

    @Test
    void rr12OnlyMembersOfTheTeams() {
        grant(login("petra"), "/api/events/" + T01, RUTH).statusCode(422)
                .body("rule", equalTo("RR-12")).body("fields[0].field", equalTo("personIds"));
    }

    @Test
    void rr13MembersSeeWhoHasAPermission() {
        grant(login("petra"), "/api/events/" + T01, KIM).statusCode(200);
        given().cookie("stv_session", login("anna")).get("/api/events/" + T01 + "/grants").then().statusCode(200)
                .body("grants.person.firstName", contains("Kim")).body("canManage", equalTo(false));
        // Ruth is in Team 3 only
        given().cookie("stv_session", login("ruth")).get("/api/events/" + T01 + "/grants").then().statusCode(200);
        given().cookie("stv_session", login("alex")).get("/api/events/" + T01 + "/grants").then().statusCode(200)
                .body("canManage", equalTo(true));
    }

    @Test
    void rr10SeriesGrantAppliesToAllEventsOfTheSeries() {
        String petra = login("petra");
        String kim = login("kim");
        grant(petra, "/api/series/" + SERIES_1, KIM).statusCode(200).body("grants[0].viaSeries", equalTo(true));
        String later = seriesEvent("2026-12-01");
        given().cookie("stv_session", kim).get("/api/events/" + later).then().body("canEdit", equalTo(true));
        given().cookie("stv_session", kim).get("/api/events/" + T01 + "/grants").then()
                .body("grants[0].viaSeries", equalTo(true));
        // The series itself stays with the coaches
        given().cookie("stv_session", kim).get("/api/series/" + SERIES_1 + "/grants").then().statusCode(200)
                .body("canManage", equalTo(false));
        grant(kim, "/api/series/" + SERIES_1).statusCode(403);
        // Removing it removes the rights
        grant(petra, "/api/series/" + SERIES_1).statusCode(200).body("grants", hasSize(0));
        given().cookie("stv_session", kim).get("/api/events/" + later).then().body("canEdit", equalTo(false));
    }

    @Test
    void ben08HolderIsNotifiedAndHearsLateCancellationsWithReason() {
        String petra = login("petra");
        grant(petra, "/api/events/" + T01, KIM).statusCode(200);
        List<Map<String, Object>> granted = notifications("EVENT_GRANTED");
        assertThat(granted, hasSize(1));
        assertThat(((Map<?, ?>) granted.getFirst().get("recipient")).get("id"), equalTo(KIM));
        assertThat((String) granted.getFirst().get("text"), containsString("Sonderberechtigung"));
        // Granting again does not notify again
        grant(petra, "/api/events/" + T01, KIM).statusCode(200);
        assertThat(notifications("EVENT_GRANTED"), hasSize(1));

        // Late cancellation of Anna after the response deadline (S5) with a reason
        given().contentType(ContentType.JSON).body("{\"now\":\"2026-11-16T19:00:00+01:00\"}")
                .put("/api/test/clock").then().statusCode(204);
        given().cookie("stv_session", login("anna")).contentType(ContentType.JSON)
                .body("{\"status\":\"DECLINED\",\"comment\":\"Krank\"}")
                .put("/api/events/" + T01 + "/responses/me").then().statusCode(200);
        List<Map<String, Object>> late = notifications("LATE_CANCELLATION");
        List<Object> recipients = late.stream().<Object>map(n -> ((Map<?, ?>) n.get("recipient")).get("id")).toList();
        assertThat(recipients, hasItem(KIM));
        assertThat(recipients, hasItem("20000000-0000-4000-8000-000000000002"));
        late.forEach(n -> assertThat((String) n.get("text"), containsString("Grund: «Krank»")));
        assertThat(recipients, not(hasItem("20000000-0000-4000-8000-000000000005")));
    }
}
