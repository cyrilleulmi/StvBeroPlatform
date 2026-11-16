package ch.stvbero.platform.core.season;

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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasSize;

/** F-01: Saisons anlegen, übernehmen und wechseln (SAI-02, SAI-03, SAI-04, SAI-06, RR-07). */
@QuarkusTest
class SeasonsTest {

    static final String SEASON_2025 = "60000000-0000-4000-8000-000000000001";
    static final String SEASON_2026 = "60000000-0000-4000-8000-000000000002";
    static final String NEW = "{\"name\":\"2027/28\",\"startDate\":\"2027-07-01\",\"endDate\":\"2028-06-30\",\"copyFrom\":\"" + SEASON_2026 + "\"}";

    String alex;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
        alex = login("alex");
    }

    @Test
    void sai01ConcurrentCreateGives409InsteadOf500() {
        for (int round = 0; round < 5; round++) {
            reset();
            List<Integer> status = Concurrently.statuscodes(2, () -> given().cookie("stv_session", alex)
                    .contentType(ContentType.JSON).body(NEW).post("/api/seasons").statusCode());
            assertThat(status, contains(201, 409));
            given().cookie("stv_session", alex).get("/api/seasons").then().statusCode(200)
                    .body("name", contains("2027/28", "2026/27", "2025/26"));
        }
    }

    @Test
    void listNewestFirstWithArchive() {
        given().cookie("stv_session", login("anna")).get("/api/seasons").then().statusCode(200)
                .body("name", contains("2026/27", "2025/26"))
                .body("current", contains(true, false))
                .body("archived", contains(false, true));
    }

    @Test
    void sai04Sai06NewSeasonWithCarryOverCreateAndActivate() {
        String id = given().cookie("stv_session", alex).contentType(ContentType.JSON).body(NEW)
                .post("/api/seasons").then().statusCode(201)
                .body("name", equalTo("2027/28"))
                .body("current", equalTo(false))
                .body("archived", equalTo(false))
                .extract().path("id");

        given().cookie("stv_session", alex).queryParam("season", id).get("/api/teams").then().statusCode(200)
                .body("name", contains("Team 1", "Team 2", "Team 3"));
        given().cookie("stv_session", alex).queryParam("season", id)
                .get("/api/teams/10000000-0000-4000-8000-000000000001").then().statusCode(200)
                .body("coaches.firstName", contains("Petra"))
                // Linien und Zuweisungen werden mit übernommen (SAI-04, LIN-11)
                .body("lineup.groups.name", contains("Linie 1", "Linie 2", "Goalies"))
                .body("lineup.groups[0].players.firstName", contains("Mia", "Lea", "Anna"))
                .body("lineup.groups[0].id", not(equalTo("30000000-0000-4000-8000-000000000001")))
                .body("lineup.groups[2].players.firstName", contains("Jana"))
                .body("lineup.noLine.firstName", contains("Kim"));

        given().cookie("stv_session", alex).post("/api/seasons/" + id + "/activate").then().statusCode(204);

        given().cookie("stv_session", alex).get("/api/seasons").then().statusCode(200)
                .body("name", contains("2027/28", "2026/27", "2025/26"))
                .body("current", contains(true, false, false))
                .body("archived", contains(false, true, true));
        String petra = login("petra");
        given().cookie("stv_session", petra).get("/api/me").then().statusCode(200)
                .body("season.name", equalTo("2027/28"))
                .body("memberships.teamRole", contains("COACH", "PLAYER"))
                .body("memberships[0].seasonId", equalTo(id));
    }

    /** Nachbesserung #21: Die neue Saison übernimmt den aktuellen Namen; umbenennen dort ändert die Vorsaison nicht (SAI-02). */
    @Test
    void sai02TeamNameAppliesPerSeason() {
        String team1 = "10000000-0000-4000-8000-000000000001";
        given().cookie("stv_session", alex).contentType(ContentType.JSON).body("{\"name\":\"Damen 1\"}")
                .put("/api/teams/" + team1).then().statusCode(200);
        String id = given().cookie("stv_session", alex).contentType(ContentType.JSON).body(NEW)
                .post("/api/seasons").then().statusCode(201).extract().path("id");

        given().cookie("stv_session", alex).queryParam("season", id).get("/api/teams").then().statusCode(200)
                .body("name", contains("Damen 1", "Team 2", "Team 3"));

        given().cookie("stv_session", alex).queryParam("season", id).contentType(ContentType.JSON)
                .body("{\"name\":\"Damen A\"}").put("/api/teams/" + team1).then().statusCode(200)
                .body("name", equalTo("Damen A"));
        given().cookie("stv_session", alex).queryParam("season", id).get("/api/teams/" + team1).then().statusCode(200)
                .body("name", equalTo("Damen A"));
        given().cookie("stv_session", alex).get("/api/teams/" + team1).then().statusCode(200)
                .body("name", equalTo("Damen 1"));
        given().cookie("stv_session", alex).queryParam("season", SEASON_2025).get("/api/teams/" + team1).then().statusCode(200)
                .body("name", equalTo("Team 1"));
    }

    @Test
    void withoutCarryOverIsTheNewSeasonEmpty() {
        String id = given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"name\":\"2027/28\",\"startDate\":\"2027-07-01\",\"endDate\":\"2028-06-30\"}")
                .post("/api/seasons").then().statusCode(201).extract().path("id");
        given().cookie("stv_session", alex).queryParam("season", id).get("/api/teams").then().statusCode(200)
                .body("", hasSize(0));
    }

    @Test
    void sai03AlsoOneOldSeasonCanAgainCurrentBecome() {
        given().cookie("stv_session", alex).post("/api/seasons/" + SEASON_2025 + "/activate").then().statusCode(204);
        given().cookie("stv_session", alex).get("/api/seasons").then().statusCode(200)
                .body("current", contains(false, true))
                .body("archived", contains(false, false));
    }

    @Test
    void conflictsAndError() {
        given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"name\":\"2026/27\",\"startDate\":\"2027-07-01\",\"endDate\":\"2028-06-30\"}")
                .post("/api/seasons").then().statusCode(409);
        given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"name\":\"2027\",\"startDate\":\"2027-01-01\",\"endDate\":\"2027-12-31\"}")
                .post("/api/seasons").then().statusCode(409);
        given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"name\":\"2027/28\",\"startDate\":\"2028-07-01\",\"endDate\":\"2027-06-30\"}")
                .post("/api/seasons").then().statusCode(400).body("fields[0].field", equalTo("endDate"));
        given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"name\":\"2027/28\",\"startDate\":\"2027-07-01\",\"endDate\":\"2028-06-30\",\"copyFrom\":\"60000000-0000-4000-8000-000000000099\"}")
                .post("/api/seasons").then().statusCode(404);
        given().cookie("stv_session", alex).post("/api/seasons/60000000-0000-4000-8000-000000000099/activate").then().statusCode(404);
    }

    @Test
    void rr07OnlyAdminsManageSeasons() {
        String marc = login("marc");
        given().cookie("stv_session", marc).contentType(ContentType.JSON).body(NEW)
                .post("/api/seasons").then().statusCode(403);
        given().cookie("stv_session", marc).post("/api/seasons/" + SEASON_2025 + "/activate").then().statusCode(403);
        given().get("/api/seasons").then().statusCode(401);
    }
}
