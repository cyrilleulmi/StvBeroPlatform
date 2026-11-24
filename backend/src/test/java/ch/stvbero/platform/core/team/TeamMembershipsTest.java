package ch.stvbero.platform.core.team;

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
 * Team memberships of the whole club (PTF-16, PTF-17), against the seed of docs/test-data.md: 17 people, Team 1 to 3
 * in the season 2026/27; Vera (17) was in Team 1 in 2025/26 and is in no team now; Petra coaches Team 1 and plays in
 * Team 2; Alex is admin.
 */
@QuarkusTest
class TeamMembershipsTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String TEAM_3 = "10000000-0000-4000-8000-000000000003";
    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String VERA = "20000000-0000-4000-8000-000000000017";
    static final String ARCHIVE = "60000000-0000-4000-8000-000000000001";

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static JsonPath overview(String session) {
        return given().cookie("stv_session", session).get("/api/team-memberships").then().statusCode(200).extract()
                .jsonPath();
    }

    private static Map<String, Object> row(JsonPath overview, String personId) {
        return overview.getMap("people.find { it.person.id == '" + personId + "' }");
    }

    private static List<String> teams(JsonPath overview, String personId) {
        return overview.getList("people.find { it.person.id == '" + personId + "' }.teams.collect { it.teamId + ':' + it.teamRole }");
    }

    private static ValidatableResponse batch(String session, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .post("/api/team-memberships/batch").then();
    }

    @Test
    void ptf16EveryoneWithTheirTeamsNowAndInTheSeasonBefore() {
        JsonPath json = overview(login("petra"));

        assertEquals("2026/27", json.getString("season.name"));
        assertEquals("2025/26", json.getString("previousSeason.name"));
        assertEquals(List.of("Team 1", "Team 2", "Team 3"), json.getList("teams.name"));
        assertEquals(List.of(true, true, true), json.getList("teams.inSeason"));
        assertEquals(List.of(TEAM_1), json.getList("previousTeams.id"));
        assertEquals(17, json.getList("people").size());
        assertTrue(teams(json, PETRA).containsAll(List.of(TEAM_1 + ":COACH", TEAM_2 + ":PLAYER")));
        // Vera is in no team now, and was in Team 1
        assertTrue(teams(json, VERA).isEmpty());
        assertEquals(List.of(TEAM_1),
                json.getList("people.find { it.person.id == '" + VERA + "' }.previousTeams.teamId"));
        // Gender and year of birth come along for sorting and filtering (PTF-15)
        @SuppressWarnings("unchecked")
        Map<String, Object> petra = (Map<String, Object>) row(json, PETRA).get("person");
        assertEquals("FEMALE", petra.get("gender"));
        assertEquals(1987, petra.get("birthYear"));
        assertTrue(json.getBoolean("writable"));
        // Coaches take people out of a team as well (E-103)
        assertTrue(json.getBoolean("canRemove"));
    }

    @Test
    void ptf16OnlyCoachesAndAdminsAndArchivedSeasonsAreReadOnly() {
        given().cookie("stv_session", login("anna")).get("/api/team-memberships").then().statusCode(403);
        given().get("/api/team-memberships").then().statusCode(401);

        JsonPath archive = given().cookie("stv_session", login("petra")).queryParam("season", ARCHIVE)
                .get("/api/team-memberships").then().statusCode(200).extract().jsonPath();
        assertEquals("2025/26", archive.getString("season.name"));
        assertFalse(archive.getBoolean("writable"));
        assertEquals(List.of(TEAM_1 + ":PLAYER"), teams(archive, VERA));
        batch(login("alex"), "{\"add\":[{\"teamId\":\"" + TEAM_1 + "\",\"personId\":\"" + ANNA + "\",\"teamRole\":\"PLAYER\"}]}")
                .statusCode(200);
        given().cookie("stv_session", login("alex")).queryParam("season", ARCHIVE).contentType(ContentType.JSON)
                .body("{\"add\":[{\"teamId\":\"" + TEAM_1 + "\",\"personId\":\"" + ANNA + "\",\"teamRole\":\"PLAYER\"}]}")
                .post("/api/team-memberships/batch").then().statusCode(409);
    }

    @Test
    void ptf17SeveralPeopleJoinATeamAtOnce() {
        String petra = login("petra");
        String body = """
                {"add":[{"teamId":"%s","personId":"%s","teamRole":"PLAYER"},
                        {"teamId":"%s","personId":"%s","teamRole":"PLAYER"},
                        {"teamId":"%s","personId":"%s","teamRole":"PLAYER"}]}"""
                .formatted(TEAM_3, VERA, TEAM_3, ANNA, TEAM_1, ANNA);
        // Vera and Anna are new in Team 3; Anna is in Team 1 already and stays as she is
        batch(petra, body).statusCode(200).body("added", org.hamcrest.Matchers.equalTo(2))
                .body("changed", org.hamcrest.Matchers.equalTo(0)).body("removed", org.hamcrest.Matchers.equalTo(0));
        JsonPath json = overview(petra);
        assertEquals(List.of(TEAM_3 + ":PLAYER"), teams(json, VERA));
        assertTrue(teams(json, ANNA).containsAll(List.of(TEAM_1 + ":PLAYER", TEAM_3 + ":PLAYER")));

        // Again: nothing new. With another team role: the role changes
        batch(petra, body).statusCode(200).body("added", org.hamcrest.Matchers.equalTo(0));
        batch(petra, "{\"add\":[{\"teamId\":\"" + TEAM_3 + "\",\"personId\":\"" + VERA + "\",\"teamRole\":\"COACH\"}]}")
                .statusCode(200).body("changed", org.hamcrest.Matchers.equalTo(1));
        assertEquals(List.of(TEAM_3 + ":COACH"), teams(overview(petra), VERA));
    }

    @Test
    void ptf17MovingOnAndTheBatchIsAllOrNothing() {
        batch(login("anna"), "{\"remove\":[{\"teamId\":\"" + TEAM_1 + "\",\"personId\":\"" + ANNA + "\"}]}").statusCode(403);

        // Moving on: into Team 3 and out of Team 1 in one step, by a coach (E-103)
        String alex = login("petra");
        batch(alex, """
                {"add":[{"teamId":"%s","personId":"%s","teamRole":"PLAYER"}],
                 "remove":[{"teamId":"%s","personId":"%s"},{"teamId":"%s","personId":"%s"}]}"""
                .formatted(TEAM_3, ANNA, TEAM_1, ANNA, TEAM_2, ANNA))
                .statusCode(200).body("added", org.hamcrest.Matchers.equalTo(1))
                // She was not in Team 2: skipped
                .body("removed", org.hamcrest.Matchers.equalTo(1));
        assertEquals(List.of(TEAM_3 + ":PLAYER"), teams(overview(alex), ANNA));

        // An unknown team: nothing of the batch is saved
        batch(alex, """
                {"add":[{"teamId":"%s","personId":"%s","teamRole":"PLAYER"},
                        {"teamId":"10000000-0000-4000-8000-000000000099","personId":"%s","teamRole":"PLAYER"}]}"""
                .formatted(TEAM_2, ANNA, ANNA)).statusCode(404);
        assertEquals(List.of(TEAM_3 + ":PLAYER"), teams(overview(alex), ANNA));
        batch(alex, "{\"add\":[{\"teamId\":\"" + TEAM_2 + "\",\"personId\":\"" + ANNA + "\"}]}").statusCode(400);
    }
}
