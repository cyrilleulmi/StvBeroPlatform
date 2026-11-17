package ch.stvbero.platform.core.event;

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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/** F-16: warnings for events and call-ups. IDs and expected values from docs/test-data.md (S48 to S51). */
@QuarkusTest
class WarningsTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String LEA = "20000000-0000-4000-8000-000000000006";
    static final String MIA = "20000000-0000-4000-8000-000000000007";
    static final String SARA = "20000000-0000-4000-8000-000000000008";
    static final String JANA = "20000000-0000-4000-8000-000000000010";
    static final String LINE_1_TEAM_1 = "30000000-0000-4000-8000-000000000001";
    static final String LINE_2_TEAM_1 = "30000000-0000-4000-8000-000000000002";
    static final String T01 = "40000000-0000-4000-8000-000000000001";
    static final String T02 = "40000000-0000-4000-8000-000000000002";
    static final String T03 = "40000000-0000-4000-8000-000000000003";
    static final String T04 = "40000000-0000-4000-8000-000000000004";
    static final String T06 = "40000000-0000-4000-8000-000000000006";

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static ValidatableResponse overview(String session, String event) {
        return given().cookie("stv_session", session).get("/api/events/" + event + "/overview").then();
    }

    private static ValidatableResponse callUp(String session, String event) {
        return given().cookie("stv_session", session).get("/api/events/" + event + "/call-up").then();
    }

    private static ValidatableResponse setCallUp(String session, String event, String... ids) {
        String body = "{\"personIds\":[" + String.join(",", List.of(ids).stream().map(id -> "\"" + id + "\"").toList()) + "]}";
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .put("/api/events/" + event + "/call-up").then();
    }

    private static ValidatableResponse setMinimums(String session, String team, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .put("/api/teams/" + team + "/warning-minimums").then();
    }

    private static List<String> summary(JsonPath json) {
        List<Map<String, Object>> warnings = json.getList("warnings");
        return warnings.stream().map(w -> ((Map<?, ?>) w.get("team")).get("name") + " " + w.get("type")
                + (w.get("groupName") == null ? "" : " " + w.get("groupName")) + " " + w.get("count") + "/"
                + w.get("minimum")).toList();
    }

    private static void clock(String now) {
        given().contentType(ContentType.JSON).body("{\"now\":\"" + now + "\"}").put("/api/test/clock").then().statusCode(204);
    }

    @Test
    void criterion1And9OverviewOfEvent02WithoutGoalie() {
        JsonPath json = overview(login("petra"), T02).statusCode(200).extract().jsonPath();
        assertThat(summary(json), contains("Team 1 GOALIES 0/1", "Team 1 FIELD_PLAYERS 1/6", "Team 1 LINE Linie 1 1/3"));
        assertThat(json.getString("warnings[2].groupId"), equalTo(LINE_1_TEAM_1));
    }

    @Test
    void criterion1OverviewOfEvent01PerTeam() {
        JsonPath json = overview(login("alex"), T01).statusCode(200).extract().jsonPath();
        assertThat(summary(json), contains(
                "Team 1 FIELD_PLAYERS 4/6", "Team 1 LINE Linie 1 2/3", "Team 1 LINE Linie 2 1/3",
                "Team 2 GOALIES 0/1", "Team 2 FIELD_PLAYERS 3/6", "Team 2 LINE Linie 1 2/3", "Team 2 LINE Linie 2 1/3"));
    }

    @Test
    void criterion1WarningDisappearsWhenTheGoalieResponds() {
        String petra = login("petra");
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{\"status\":\"ATTENDING\"}")
                .put("/api/events/" + T02 + "/responses/" + JANA).then().statusCode(200);
        assertThat(summary(overview(petra, T02).extract().jsonPath()),
                contains("Team 1 FIELD_PLAYERS 1/6", "Team 1 LINE Linie 1 1/3"));
    }

    @Test
    void criterion2CallUpWarningsFromTheSelection() {
        String petra = login("petra");
        // Draft: Anna, Lea, Mia (Linie 1), Sara (Linie 2), Jana (goalie)
        assertThat(summary(callUp(petra, T04).statusCode(200).extract().jsonPath()),
                contains("Team 1 FIELD_PLAYERS 4/10", "Team 1 LINE Linie 2 1/3"));
        // Without Jana: no goalie called up, even though she responded «dabei»
        JsonPath json = setCallUp(petra, T04, ANNA, LEA, MIA, SARA).statusCode(200).extract().jsonPath();
        assertThat(summary(json), contains("Team 1 GOALIES 0/1", "Team 1 FIELD_PLAYERS 4/10", "Team 1 LINE Linie 2 1/3"));
        assertThat(json.getString("warnings[2].groupId"), equalTo(LINE_2_TEAM_1));
    }

    @Test
    void criterion3PublishingWithWarningsIsAllowed() {
        String petra = login("petra");
        given().cookie("stv_session", petra).post("/api/events/" + T04 + "/call-up/publish").then().statusCode(200)
                .body("status", equalTo("PUBLISHED"))
                .body("warnings.type", contains("FIELD_PLAYERS", "LINE"));
    }

    @Test
    void criterion4DefaultsAndChangeOnTheTeam() {
        String petra = login("petra");
        given().cookie("stv_session", petra).get("/api/teams/" + TEAM_1).then().statusCode(200)
                .body("warningMinimums.goalies", equalTo(1))
                .body("warningMinimums.fieldPlayersMatchDay", equalTo(10))
                .body("warningMinimums.fieldPlayersPractice", equalTo(6))
                .body("warningMinimums.perLine", equalTo(3));
        setMinimums(petra, TEAM_1, "{\"goalies\":0,\"fieldPlayersMatchDay\":3,\"fieldPlayersPractice\":1,\"perLine\":2}")
                .statusCode(200).body("fieldPlayersMatchDay", equalTo(3)).body("perLine", equalTo(2));
        // Event 02: goalie warning off, 1 field player is enough, Anna alone in Linie 1 is below 2
        assertThat(summary(overview(petra, T02).extract().jsonPath()), contains("Team 1 LINE Linie 1 1/2"));
        // Only Team 1 changed: Team 2 keeps its defaults in event 01
        assertThat(summary(overview(petra, T01).extract().jsonPath()), contains(
                "Team 1 LINE Linie 2 1/2", "Team 2 GOALIES 0/1", "Team 2 FIELD_PLAYERS 3/6",
                "Team 2 LINE Linie 1 2/3", "Team 2 LINE Linie 2 1/3"));
        // Coach of another team (E-69) and admin may change them as well
        setMinimums(login("marc"), TEAM_1, "{\"goalies\":1,\"fieldPlayersMatchDay\":10,\"fieldPlayersPractice\":6,\"perLine\":3}")
                .statusCode(200);
        setMinimums(login("alex"), TEAM_2, "{\"goalies\":2,\"fieldPlayersMatchDay\":10,\"fieldPlayersPractice\":6,\"perLine\":3}")
                .statusCode(200).body("goalies", equalTo(2));
    }

    @Test
    void criterion4InvalidMinimumsAre400() {
        String petra = login("petra");
        setMinimums(petra, TEAM_1, "{\"goalies\":-1,\"fieldPlayersMatchDay\":10,\"fieldPlayersPractice\":6,\"perLine\":3}")
                .statusCode(400).body("fields[0].field", equalTo("goalies"));
        setMinimums(petra, TEAM_1, "{\"goalies\":1,\"fieldPlayersMatchDay\":100,\"fieldPlayersPractice\":6,\"perLine\":3}")
                .statusCode(400);
        setMinimums(petra, TEAM_1, "{\"goalies\":1,\"fieldPlayersMatchDay\":10,\"fieldPlayersPractice\":6}")
                .statusCode(400);
        setMinimums(petra, "10000000-0000-4000-8000-000000000099",
                "{\"goalies\":1,\"fieldPlayersMatchDay\":10,\"fieldPlayersPractice\":6,\"perLine\":3}").statusCode(404);
    }

    @Test
    void criterion4And6PlayersNeitherSeeNorChangeTheMinimums() {
        String anna = login("anna");
        given().cookie("stv_session", anna).get("/api/teams/" + TEAM_1).then().statusCode(200)
                .body("$", not(hasKey("warningMinimums")));
        setMinimums(anna, TEAM_1, "{\"goalies\":0,\"fieldPlayersMatchDay\":0,\"fieldPlayersPractice\":0,\"perLine\":0}")
                .statusCode(403).body("rule", equalTo("RR-07"));
    }

    @Test
    void criterion5NoWarningsForCancelledOtherAndPastEvents() {
        String petra = login("petra");
        overview(petra, T03).statusCode(200).body("warnings", empty());
        overview(petra, T06).statusCode(200).body("warnings", empty());
        // After event 02 (Thu 19.11.2026 19:00–20:30)
        clock("2026-11-19T21:00:00+01:00");
        overview(petra, T02).statusCode(200).body("warnings", empty());
        given().cookie("stv_session", petra).get("/api/events/" + T02).then().statusCode(200).body("warningCount", equalTo(0));
    }

    @Test
    void criterion5NoCallUpWarningsAfterTheMatchDay() {
        clock("2026-11-22T14:00:00+01:00");
        callUp(login("petra"), T04).statusCode(200).body("warnings", empty());
    }

    @Test
    void criterion6PlayersGetNoWarningsFromTheApi() {
        String anna = login("anna");
        overview(anna, T02).statusCode(200).body("$", not(hasKey("warnings")));
        given().cookie("stv_session", anna).get("/api/events").then().statusCode(200)
                .body("warningCount", everyItem(nullValue()));
        given().cookie("stv_session", anna).get("/api/events/" + T02).then().statusCode(200)
                .body("$", not(hasKey("warningCount")));
    }

    @Test
    void criterion8EventListCountsTheWarnings() {
        JsonPath json = given().cookie("stv_session", login("petra")).get("/api/events").then().statusCode(200)
                .extract().jsonPath();
        assertThat(json.getInt("find { it.id == '" + T01 + "' }.warningCount"), equalTo(7));
        assertThat(json.getInt("find { it.id == '" + T02 + "' }.warningCount"), equalTo(3));
        assertThat(json.getInt("find { it.id == '" + T03 + "' }.warningCount"), equalTo(0));
        assertThat(json.getInt("find { it.id == '" + T04 + "' }.warningCount"), equalTo(3));
        assertThat(json.getInt("find { it.id == '" + T06 + "' }.warningCount"), equalTo(0));
        given().cookie("stv_session", login("petra")).get("/api/events/" + T02).then().statusCode(200)
                .body("warningCount", equalTo(3));
    }
}
