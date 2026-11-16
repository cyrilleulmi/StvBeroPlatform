package ch.stvbero.platform.core.line;

import ch.stvbero.platform.core.error.Concurrently;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/** F-02: lines and goalies (LIN-01 to LIN-11, RR-03, RR-07). IDs from docs/test-data.md. */
@QuarkusTest
class LinesTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String TEAM_3 = "10000000-0000-4000-8000-000000000003";
    static final String T1_LINE_1 = "30000000-0000-4000-8000-000000000001";
    static final String T1_LINE_2 = "30000000-0000-4000-8000-000000000002";
    static final String T1_GOALIES = "30000000-0000-4000-8000-000000000003";
    static final String T2_LINE_1 = "30000000-0000-4000-8000-000000000004";
    static final String T2_LINE_2 = "30000000-0000-4000-8000-000000000005";
    static final String T3_GOALIES = "30000000-0000-4000-8000-000000000008";
    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String MARC = "20000000-0000-4000-8000-000000000003";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String LEA = "20000000-0000-4000-8000-000000000006";
    static final String KIM = "20000000-0000-4000-8000-000000000011";
    static final String SEASON_2025 = "60000000-0000-4000-8000-000000000001";

    String petra;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
        petra = login("petra");
    }

    private static String line(String name) {
        return "{\"name\":\"" + name + "\"}";
    }

    private static String line(String name, int sortOrder) {
        return "{\"name\":\"" + name + "\",\"sortOrder\":" + sortOrder + "}";
    }

    private static String assignment(String groupId) {
        return groupId == null ? "{\"groupId\":null}" : "{\"groupId\":\"" + groupId + "\"}";
    }

    @Test
    void k1SeedGroupsAndAssignments() {
        given().cookie("stv_session", petra).get("/api/teams/" + TEAM_1 + "/groups").then().statusCode(200)
                .body("groups.id", contains(T1_LINE_1, T1_LINE_2, T1_GOALIES))
                .body("groups.type", contains("LINE", "LINE", "GOALIES"))
                .body("groups.sortOrder", contains(1, 2, null))
                .body("groups[0].players.firstName", contains("Mia", "Lea", "Anna"))
                .body("groups[1].players.firstName", contains("Sara", "Nina"))
                .body("groups[2].players.firstName", contains("Jana"))
                .body("noLine.firstName", contains("Kim"));
        given().cookie("stv_session", login("ruth")).get("/api/teams/" + TEAM_3 + "/groups").then().statusCode(200)
                .body("groups.name", contains("Linie 1", "Goalies"))
                .body("groups[1].id", equalTo(T3_GOALIES))
                .body("groups[1].players", empty())
                .body("noLine", empty());
    }

    @Test
    void k2CreateRenameMoveAndDeleteLine() {
        String line3 = given().cookie("stv_session", petra).contentType(ContentType.JSON).body(line("Linie 3"))
                .post("/api/teams/" + TEAM_1 + "/groups").then().statusCode(201)
                .body("type", equalTo("LINE"))
                .body("sortOrder", equalTo(3))
                .extract().path("id");

        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(line("Powerplay", 1))
                .put("/api/teams/" + TEAM_1 + "/groups/" + line3).then().statusCode(200)
                .body("name", equalTo("Powerplay"))
                .body("sortOrder", equalTo(1));
        given().cookie("stv_session", petra).get("/api/teams/" + TEAM_1 + "/groups").then().statusCode(200)
                .body("groups.name", contains("Powerplay", "Linie 1", "Linie 2", "Goalies"))
                .body("groups.sortOrder", contains(1, 2, 3, null));

        // Delete line 2: Sara and Nina then have no line (LIN-06), sort order without a gap
        given().cookie("stv_session", petra).delete("/api/teams/" + TEAM_1 + "/groups/" + T1_LINE_2).then().statusCode(204);
        given().cookie("stv_session", petra).get("/api/teams/" + TEAM_1 + "/groups").then().statusCode(200)
                .body("groups.name", contains("Powerplay", "Linie 1", "Goalies"))
                .body("groups.sortOrder", contains(1, 2, null))
                .body("noLine.firstName", contains("Kim", "Sara", "Nina"));
        given().cookie("stv_session", petra).delete("/api/teams/" + TEAM_1 + "/groups/" + T1_LINE_2).then().statusCode(404);
    }

    @Test
    void k2NewLineAtSpecificPosition() {
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(line("Linie 0", 1))
                .post("/api/teams/" + TEAM_1 + "/groups").then().statusCode(201).body("sortOrder", equalTo(1));
        given().cookie("stv_session", petra).get("/api/teams/" + TEAM_1 + "/groups").then()
                .body("groups.name", contains("Linie 0", "Linie 1", "Linie 2", "Goalies"));
    }

    @Test
    void lin09NameUniquePerTeam() {
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(line("linie 2"))
                .post("/api/teams/" + TEAM_1 + "/groups").then().statusCode(409).body("rule", equalTo("LIN-09"));
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(line("Linie 1"))
                .put("/api/teams/" + TEAM_1 + "/groups/" + T1_LINE_2).then().statusCode(409).body("rule", equalTo("LIN-09"));
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(line(""))
                .post("/api/teams/" + TEAM_1 + "/groups").then().statusCode(400).body("fields[0].field", equalTo("name"));
        // The same name in another team is allowed
        given().cookie("stv_session", login("marc")).contentType(ContentType.JSON).body(line("Linie 3"))
                .post("/api/teams/" + TEAM_2 + "/groups").then().statusCode(201);
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(line("Linie 3"))
                .post("/api/teams/" + TEAM_1 + "/groups").then().statusCode(201);
    }

    @Test
    void doubleClickOnCreateLineGives409InsteadOf500() {
        for (int round = 0; round < 5; round++) {
            reset();
            List<Integer> status = Concurrently.statusCodes(2, () -> given().cookie("stv_session", petra)
                    .contentType(ContentType.JSON).body(line("Linie 3"))
                    .post("/api/teams/" + TEAM_1 + "/groups").statusCode());
            assertThat(status, contains(201, 409));
        }
    }

    @Test
    void k3AssignAndSetNoLine() {
        // S14: assign Kim to line 2
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(assignment(T1_LINE_2))
                .put("/api/teams/" + TEAM_1 + "/assignments/" + KIM).then().statusCode(204);
        given().cookie("stv_session", petra).get("/api/teams/" + TEAM_1 + "/groups").then()
                .body("groups[1].players.firstName", contains("Kim", "Sara", "Nina"))
                .body("noLine", empty());
        // back to «ohne Linie»
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(assignment(null))
                .put("/api/teams/" + TEAM_1 + "/assignments/" + KIM).then().statusCode(204);
        given().cookie("stv_session", petra).get("/api/teams/" + TEAM_1 + "/groups").then()
                .body("noLine.firstName", contains("Kim"));
        // Make her a goalie
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(assignment(T1_GOALIES))
                .put("/api/teams/" + TEAM_1 + "/assignments/" + KIM).then().statusCode(204);
        given().cookie("stv_session", petra).get("/api/teams/" + TEAM_1 + "/groups").then()
                .body("groups[2].players.firstName", contains("Kim", "Jana"));
    }

    @Test
    void s15AtMostOneGroupPerPlayerAndTeam() {
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(assignment(T1_LINE_2))
                .put("/api/teams/" + TEAM_1 + "/assignments/" + ANNA).then().statusCode(204);
        given().cookie("stv_session", petra).get("/api/teams/" + TEAM_1 + "/groups").then()
                .body("groups[0].players.firstName", not(hasItem("Anna")))
                .body("groups[1].players.firstName", contains("Sara", "Anna", "Nina"))
                .body("groups.players.flatten().findAll { it.firstName == 'Anna' }", hasSize(1));
        given().cookie("stv_session", login("anna")).get("/api/me").then().statusCode(200)
                .body("memberships[0].groupId", equalTo(T1_LINE_2));
    }

    @Test
    void lin03LeaHasHerOwnLineInEveryTeam() {
        given().cookie("stv_session", login("lea")).get("/api/me").then().statusCode(200)
                .body("memberships.team.name", contains("Team 1", "Team 2"))
                .body("memberships.groupId", contains(T1_LINE_1, T2_LINE_2));
        // A change in Team 1 leaves Team 2 unchanged
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(assignment(T1_LINE_2))
                .put("/api/teams/" + TEAM_1 + "/assignments/" + LEA).then().statusCode(204);
        given().cookie("stv_session", login("lea")).get("/api/me").then().statusCode(200)
                .body("memberships.groupId", contains(T1_LINE_2, T2_LINE_2));
        // A group of another team cannot be assigned
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(assignment(T2_LINE_1))
                .put("/api/teams/" + TEAM_1 + "/assignments/" + LEA).then().statusCode(404);
    }

    @Test
    void lin10OnlyPlayersOfTheTeam() {
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(assignment(T1_LINE_1))
                .put("/api/teams/" + TEAM_1 + "/assignments/" + PETRA).then().statusCode(409)
                .body("rule", equalTo("LIN-10"));
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(assignment(T1_LINE_1))
                .put("/api/teams/" + TEAM_1 + "/assignments/" + MARC).then().statusCode(404);
    }

    @Test
    void lin10ChangeToCoachRemovesTheAssignment() {
        String alex = login("alex");
        given().cookie("stv_session", alex).contentType(ContentType.JSON).body("{\"teamRole\":\"COACH\"}")
                .put("/api/teams/" + TEAM_1 + "/members/" + ANNA).then().statusCode(200)
                .body("groupId", nullValue());
        given().cookie("stv_session", alex).contentType(ContentType.JSON).body("{\"teamRole\":\"PLAYER\"}")
                .put("/api/teams/" + TEAM_1 + "/members/" + ANNA).then().statusCode(200);
        given().cookie("stv_session", petra).get("/api/teams/" + TEAM_1 + "/groups").then()
                .body("groups[0].players.firstName", contains("Mia", "Lea"))
                .body("noLine.firstName", containsInAnyOrder("Kim", "Anna"));
    }

    @Test
    void k6GoaliesExactlyOnceAndNotDeletable() {
        given().cookie("stv_session", petra).delete("/api/teams/" + TEAM_1 + "/groups/" + T1_GOALIES).then().statusCode(409)
                .body("rule", equalTo("LIN-05"));
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(line("Torhüterinnen"))
                .put("/api/teams/" + TEAM_1 + "/groups/" + T1_GOALIES).then().statusCode(409)
                .body("rule", equalTo("LIN-05"));
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(line("Goalies"))
                .post("/api/teams/" + TEAM_1 + "/groups").then().statusCode(409);
        given().cookie("stv_session", petra).get("/api/teams/" + TEAM_1 + "/groups").then()
                .body("groups.findAll { it.type == 'GOALIES' }", hasSize(1));
    }

    @Test
    void k6NewTeamHasGoaliesImmediately() {
        String alex = login("alex");
        String id = given().cookie("stv_session", alex).contentType(ContentType.JSON).body("{\"name\":\"Team 4\"}")
                .post("/api/teams").then().statusCode(201).extract().path("id");
        given().cookie("stv_session", alex).get("/api/teams/" + id + "/groups").then().statusCode(200)
                .body("groups.type", contains("GOALIES"))
                .body("groups.name", contains("Goalies"));
    }

    @Test
    void k7OnlyCoachesOfTheTeamAndAdminsCanEdit() {
        String marc = login("marc");
        String anna = login("anna");
        // marc is coach of Team 2, not of Team 1: 403 (RR-07)
        given().cookie("stv_session", marc).get("/api/teams/" + TEAM_1 + "/groups").then().statusCode(403);
        given().cookie("stv_session", marc).contentType(ContentType.JSON).body(line("Linie 3"))
                .post("/api/teams/" + TEAM_1 + "/groups").then().statusCode(403);
        given().cookie("stv_session", marc).contentType(ContentType.JSON).body(line("X"))
                .put("/api/teams/" + TEAM_1 + "/groups/" + T1_LINE_1).then().statusCode(403);
        given().cookie("stv_session", marc).delete("/api/teams/" + TEAM_1 + "/groups/" + T1_LINE_1).then().statusCode(403);
        given().cookie("stv_session", marc).contentType(ContentType.JSON).body(assignment(T1_LINE_2))
                .put("/api/teams/" + TEAM_1 + "/assignments/" + ANNA).then().statusCode(403);

        // anna is a player: may read, not change
        given().cookie("stv_session", anna).get("/api/teams/" + TEAM_1 + "/groups").then().statusCode(200)
                .body("groups", hasSize(3));
        given().cookie("stv_session", anna).contentType(ContentType.JSON).body(assignment(T1_LINE_2))
                .put("/api/teams/" + TEAM_1 + "/assignments/" + ANNA).then().statusCode(403);
        given().cookie("stv_session", anna).contentType(ContentType.JSON).body(line("Linie 3"))
                .post("/api/teams/" + TEAM_1 + "/groups").then().statusCode(403);

        // petra is only a player in Team 2
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(assignment(T2_LINE_1))
                .put("/api/teams/" + TEAM_2 + "/assignments/" + LEA).then().statusCode(403);

        // Admin may
        given().cookie("stv_session", login("alex")).contentType(ContentType.JSON).body(assignment(T1_LINE_2))
                .put("/api/teams/" + TEAM_1 + "/assignments/" + ANNA).then().statusCode(204);
        // Without login: 401
        given().get("/api/teams/" + TEAM_1 + "/groups").then().statusCode(401);
    }

    @Test
    void unknownTeamAndUnknownLineGive404() {
        String alex = login("alex");
        given().cookie("stv_session", alex).get("/api/teams/10000000-0000-4000-8000-000000000099/groups").then().statusCode(404);
        given().cookie("stv_session", alex).delete("/api/teams/" + TEAM_1 + "/groups/" + T2_LINE_1).then().statusCode(404);
    }

    @Test
    void sai02ArchiveKeepsItsLineup() {
        given().cookie("stv_session", login("alex")).queryParam("season", SEASON_2025)
                .get("/api/teams/" + TEAM_1).then().statusCode(200)
                .body("lineup.groups.name", contains("Goalies"))
                .body("lineup.noLine.firstName", contains("Vera"));
    }

    @Test
    void lin02ConcurrentAssignmentGivesNo500() {
        for (int round = 0; round < 5; round++) {
            reset();
            List<Integer> status = Concurrently.statusCodes(2, () -> given().cookie("stv_session", petra)
                    .contentType(ContentType.JSON).body(assignment(T1_LINE_2))
                    .put("/api/teams/" + TEAM_1 + "/assignments/" + KIM).statusCode());
            assertThat(status, not(hasItem(500)));
            given().cookie("stv_session", petra).get("/api/teams/" + TEAM_1 + "/groups").then()
                    .body("groups[1].players.firstName", contains("Kim", "Sara", "Nina"));
        }
    }
}
