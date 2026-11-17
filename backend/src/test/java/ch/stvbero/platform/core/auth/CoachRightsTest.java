package ch.stvbero.platform.core.auth;

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
import static org.hamcrest.Matchers.hasItem;

/**
 * F-12: coaches manage all teams, deleting stays with admins (RR-03, RR-04, RR-06, RR-08, E-69).
 * Seed: petra coach Team 1 and player Team 2, marc coach Team 2, anna player Team 1, alex admin.
 */
@QuarkusTest
class CoachRightsTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String TEAM_3 = "10000000-0000-4000-8000-000000000003";
    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String NINA = "20000000-0000-4000-8000-000000000009";
    static final String TINA = "20000000-0000-4000-8000-000000000016";
    static final String T1_LINE_1 = "30000000-0000-4000-8000-000000000001";
    static final String T1_LINE_2 = "30000000-0000-4000-8000-000000000002";
    static final String T02 = "40000000-0000-4000-8000-000000000002";
    static final String T01 = "40000000-0000-4000-8000-000000000001";
    static final String SEASON_2025 = "60000000-0000-4000-8000-000000000001";

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static io.restassured.specification.RequestSpecification as(String session) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON);
    }

    private static ValidatableResponse forbidden(ValidatableResponse response) {
        return response.statusCode(403).contentType("application/problem+json")
                .body("title", equalTo("Keine Berechtigung"));
    }

    // ------------------------------------------------------------------ Criterion 1

    @Test
    void k1MarcManagesTeam1() {
        String marc = login("marc");
        // Edit an event of Team 1 (Thu practice, series 02, Team 1 only)
        as(marc).get("/api/events/" + T02).then().statusCode(200).body("canEdit", equalTo(true));
        as(marc).body("""
                {"type":"PRACTICE","title":"Training verschoben","start":"2026-11-19T19:30:00+01:00",
                 "end":"2026-11-19T21:00:00+01:00","teamIds":["%s"]}""".formatted(TEAM_1))
                .put("/api/events/" + T02).then().statusCode(200).body("title", equalTo("Training verschoben"));
        // Assign a player of Team 1 to a line
        as(marc).body("{\"groupId\":\"" + T1_LINE_2 + "\"}")
                .put("/api/teams/" + TEAM_1 + "/assignments/" + ANNA).then().statusCode(204);
        as(marc).get("/api/teams/" + TEAM_1).then().statusCode(200)
                .body("lineup.groups.find { it.id == '" + T1_LINE_2 + "' }.players.firstName", hasItem("Anna"));
        // Respond on behalf of a player of Team 1 and see her comment
        as(marc).body("{\"status\":\"MAYBE\",\"comment\":\"Kommt später\"}")
                .put("/api/events/" + T02 + "/responses/" + NINA).then().statusCode(200);
        JsonPath overview = as(marc).get("/api/events/" + T02 + "/overview").then().statusCode(200)
                .extract().jsonPath();
        assertThat(overview.getString("teams[0].groups.entries.flatten().find { it.person.id == '" + NINA
                + "' }.response.comment"), equalTo("Kommt später"));
        // Comments of others in Team 1 as well (event 01: Mia «Krank»)
        assertThat(as(marc).get("/api/events/" + T01 + "/overview").then().extract().jsonPath()
                .getString("teams[0].groups.entries.flatten().find { it.person.firstName == 'Mia' }.response.comment"), equalTo("Krank"));
    }

    @Test
    void meShowsCoachFlag() {
        as(login("marc")).get("/api/me").then().statusCode(200).body("coach", equalTo(true)).body("admin", equalTo(false));
        as(login("anna")).get("/api/me").then().statusCode(200).body("coach", equalTo(false));
        as(login("alex")).get("/api/me").then().statusCode(200).body("coach", equalTo(false)).body("admin", equalTo(true));
    }

    // ------------------------------------------------------------------ Criterion 2

    @Test
    void k2PetraCreatesPersonAndAddsHerToTeam2() {
        String petra = login("petra");
        String id = as(petra).body("{\"firstName\":\"Nora\",\"lastName\":\"Neu\",\"email\":\"nora@example.ch\"}")
                .post("/api/persons").then().statusCode(201).extract().path("id");
        as(petra).body("{\"personId\":\"" + id + "\",\"teamRole\":\"PLAYER\"}")
                .post("/api/teams/" + TEAM_2 + "/members").then().statusCode(201).body("teamRole", equalTo("PLAYER"));
        as(petra).body("{\"teamRole\":\"COACH\"}")
                .put("/api/teams/" + TEAM_2 + "/members/" + id).then().statusCode(200).body("teamRole", equalTo("COACH"));
        as(petra).get("/api/persons/" + id).then().statusCode(200)
                .body("memberships.team.name", contains("Team 2"));
    }

    // ------------------------------------------------------------------ Criterion 3: one test per delete endpoint

    @Test
    void k3CoachCannotRemoveMember() {
        String petra = login("petra");
        forbidden(as(petra).delete("/api/teams/" + TEAM_1 + "/members/" + ANNA).then());
        forbidden(as(login("marc")).delete("/api/teams/" + TEAM_2 + "/members/" + PETRA).then());
        as(petra).get("/api/teams/" + TEAM_1).then().statusCode(200)
                .body("lineup.groups.players.flatten().firstName", hasItem("Anna"));
    }

    @Test
    void k3CoachCannotDeleteLine() {
        forbidden(as(login("petra")).delete("/api/teams/" + TEAM_1 + "/groups/" + T1_LINE_1).then());
        forbidden(as(login("marc")).delete("/api/teams/" + TEAM_1 + "/groups/" + T1_LINE_1).then());
        as(login("alex")).get("/api/teams/" + TEAM_1 + "/groups").then()
                .body("groups.id", hasItem(T1_LINE_1));
    }

    // ------------------------------------------------------------------ Criterion 4

    @Test
    void k4SeasonsSwissUnihockeyAndAdminFlagStayWithAdmins() {
        String petra = login("petra");
        forbidden(as(petra).body("{\"name\":\"2027/28\",\"startDate\":\"2027-07-01\",\"endDate\":\"2028-06-30\"}")
                .post("/api/seasons").then());
        forbidden(as(petra).post("/api/seasons/" + SEASON_2025 + "/activate").then());
        forbidden(as(petra).body("{\"suTeamId\":900002}").put("/api/teams/" + TEAM_2 + "/swiss-unihockey").then());
        forbidden(as(petra).body("{\"suTeamId\":null}").put("/api/teams/" + TEAM_1 + "/swiss-unihockey").then());
        forbidden(as(petra).post("/api/teams/" + TEAM_1 + "/swiss-unihockey/sync").then());
        forbidden(as(petra).body("{\"firstName\":\"Petra\",\"lastName\":\"Keller\",\"admin\":true}")
                .put("/api/persons/" + PETRA).then());
        as(petra).get("/api/me").then().body("admin", equalTo(false));
    }

    // ------------------------------------------------------------------ Criterion 5

    @Test
    void k5AdminStillDeletes() {
        String alex = login("alex");
        as(alex).delete("/api/teams/" + TEAM_1 + "/groups/" + T1_LINE_1).then().statusCode(204);
        as(alex).delete("/api/teams/" + TEAM_1 + "/members/" + ANNA).then().statusCode(204);
        as(alex).body("{\"firstName\":\"Petra\",\"lastName\":\"Keller\",\"admin\":true}")
                .put("/api/persons/" + PETRA).then().statusCode(200).body("admin", equalTo(true));
        as(alex).post("/api/teams/" + TEAM_1 + "/swiss-unihockey/sync").then().statusCode(200);
    }

    // ------------------------------------------------------------------ Criterion 6

    @Test
    void k6PlayerUnchanged() {
        String anna = login("anna");
        forbidden(as(anna).get("/api/persons").then());
        forbidden(as(anna).body("{\"firstName\":\"X\",\"lastName\":\"Y\"}").post("/api/persons").then());
        forbidden(as(anna).get("/api/teams/" + TEAM_2).then());
        forbidden(as(anna).get("/api/teams/" + TEAM_3 + "/groups").then());
        forbidden(as(anna).body("{\"groupId\":\"" + T1_LINE_2 + "\"}")
                .put("/api/teams/" + TEAM_1 + "/assignments/" + ANNA).then());
        forbidden(as(anna).body("{\"status\":\"ATTENDING\"}").put("/api/events/" + T01 + "/responses/" + NINA).then());
        as(anna).get("/api/teams").then().statusCode(200).body("name", contains("Team 1"));
        // Event list with a foreign team filter: nothing of Team 3 except shared events (TER-13)
        List<String> ids = as(anna).get("/api/events?team=" + TEAM_3).then().statusCode(200).extract().path("id");
        assertThat(ids, contains("40000000-0000-4000-8000-000000000006"));
        // Comments of others stay hidden
        List<Map<String, Object>> responses = as(anna).get("/api/events/" + T01 + "/overview").then().statusCode(200)
                .extract().jsonPath().getList("teams[1].groups.entries.flatten().response");
        assertThat(responses.stream().anyMatch(r -> r.containsKey("comment")), equalTo(false));
    }

    // ------------------------------------------------------------------ Criterion 7

    @Test
    void k7PetraHasCoachRightsAlsoInTeam2() {
        String petra = login("petra");
        // In Team 2 Petra is only a player, but coach in Team 1
        as(petra).body("{\"groupId\":\"30000000-0000-4000-8000-000000000005\"}")
                .put("/api/teams/" + TEAM_2 + "/assignments/" + PETRA).then().statusCode(204);
        as(petra).get("/api/events/40000000-0000-4000-8000-000000000005/call-up").then().statusCode(200);
        as(petra).body("{\"name\":\"Linie 3\"}").post("/api/teams/" + TEAM_2 + "/groups").then().statusCode(201);
        as(petra).body("{\"personId\":\"" + TINA + "\",\"teamRole\":\"PLAYER\"}")
                .post("/api/teams/" + TEAM_2 + "/members").then().statusCode(201);
    }

    // ------------------------------------------------------------------ Criterion 8

    @Test
    void k8LateCancellationOnlyToCoachesOfTheTeam() {
        given().contentType(ContentType.JSON).body("{\"now\":\"2026-11-16T19:00:00+01:00\"}")
                .put("/api/test/clock").then().statusCode(204);
        as(login("anna")).body("{\"status\":\"DECLINED\"}").put("/api/events/" + T01 + "/responses/me")
                .then().statusCode(200);
        List<String> recipients = given().get("/api/test/notifications").then().statusCode(200).extract().jsonPath()
                .getList("findAll { it.type == 'LATE_CANCELLATION' && !it.id.startsWith('90000000') }.recipient.id");
        // Only Petra (coach of Team 1), not Marc (Team 2, also in the event) nor Ruth (Team 3)
        assertThat(recipients, contains(PETRA));
    }
}
