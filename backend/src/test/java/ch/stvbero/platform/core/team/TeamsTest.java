package ch.stvbero.platform.core.team;

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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/** F-01: Teams und Teamzugehörigkeiten (PTF-03, PTF-04, PTF-07, PTF-12, SAI-02, RR-04, RR-07). */
@QuarkusTest
class TeamsTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String TEAM_3 = "10000000-0000-4000-8000-000000000003";
    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String TINA = "20000000-0000-4000-8000-000000000016";
    static final String VERA = "20000000-0000-4000-8000-000000000017";
    static final String SEASON_2025 = "60000000-0000-4000-8000-000000000001";

    String alex;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
        alex = login("alex");
    }

    @Test
    void ptf12PetraSeesBothTeamsWithHerTeamRole() {
        String petra = login("petra");
        given().cookie("stv_session", petra).get("/api/teams").then().statusCode(200)
                .body("name", contains("Team 1", "Team 2"))
                .body("myTeamRole", contains("COACH", "PLAYER"));
        given().cookie("stv_session", petra).get("/api/me").then().statusCode(200)
                .body("memberships.team.name", contains("Team 1", "Team 2"))
                .body("memberships.teamRole", contains("COACH", "PLAYER"));
    }

    @Test
    void adminSeesAllTeamsTheSeason() {
        given().cookie("stv_session", alex).get("/api/teams").then().statusCode(200)
                .body("name", contains("Team 1", "Team 2", "Team 3"))
                .body("[0].suTeamId", equalTo(900001))
                .body("[0].myTeamRole", nullValue());
        given().cookie("stv_session", alex).queryParam("season", SEASON_2025).get("/api/teams").then().statusCode(200)
                .body("name", contains("Team 1"));
    }

    @Test
    void ptf07CoachesAndPlayersSeparate() {
        String anna = login("anna");
        given().cookie("stv_session", anna).get("/api/teams/" + TEAM_1).then().statusCode(200)
                .body("name", equalTo("Team 1"))
                .body("myTeamRole", equalTo("PLAYER"))
                .body("coaches.firstName", contains("Petra"))
                // Ab F-02 nach Linie: Linien nach Reihenfolge, Goalies, dann «Ohne Linie» (LIN-06, LIN-09)
                .body("lineup.groups.name", contains("Linie 1", "Linie 2", "Goalies"))
                .body("lineup.groups[0].players.lastName", contains("Frei", "Graf", "Muster"))
                .body("lineup.groups[1].players.lastName", contains("Brun", "Roth"))
                .body("lineup.groups[2].players.lastName", contains("Wyss"))
                .body("lineup.noLine.lastName", contains("Baumann"))
                .body("lineup.groups.players.flatten().firstName", not(hasItem("Petra")));
        given().cookie("stv_session", anna).get("/api/teams/" + TEAM_2).then().statusCode(403);
    }

    @Test
    void ptf04ConcurrentAddGives409InsteadOf500() {
        for (int round = 0; round < 5; round++) {
            reset();
            List<Integer> status = Concurrently.statuscodes(2, () -> given().cookie("stv_session", alex)
                    .contentType(ContentType.JSON)
                    .body("{\"personId\":\"" + TINA + "\",\"teamRole\":\"COACH\"}")
                    .post("/api/teams/" + TEAM_1 + "/members").statusCode());
            assertThat(status, contains(201, 409));
            given().cookie("stv_session", alex).get("/api/teams/" + TEAM_1).then().statusCode(200)
                    .body("coaches.firstName.findAll { it == 'Tina' }", hasSize(1));
        }
    }

    @Test
    void ptf04SecondAddIsRejected() {
        given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"personId\":\"" + TINA + "\",\"teamRole\":\"COACH\"}")
                .post("/api/teams/" + TEAM_1 + "/members").then().statusCode(201)
                .body("team.name", equalTo("Team 1"))
                .body("person.firstName", equalTo("Tina"))
                .body("teamRole", equalTo("COACH"))
                .body("seasonId", equalTo("60000000-0000-4000-8000-000000000002"))
                .body("groupId", nullValue());

        given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"personId\":\"" + TINA + "\",\"teamRole\":\"PLAYER\"}")
                .post("/api/teams/" + TEAM_1 + "/members").then().statusCode(409)
                .body("rule", equalTo("PTF-04"))
                .body("detail", equalTo("Tina Bucher ist schon in diesem Team (Coach). Pro Team gibt es nur eine Funktion."));
    }

    @Test
    void ptf03PersonInSeveralTeams() {
        given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"personId\":\"" + ANNA + "\",\"teamRole\":\"COACH\"}")
                .post("/api/teams/" + TEAM_3 + "/members").then().statusCode(201);
        given().cookie("stv_session", login("anna")).get("/api/teams").then().statusCode(200)
                .body("name", contains("Team 1", "Team 3"))
                .body("myTeamRole", contains("PLAYER", "COACH"));
    }

    @Test
    void teamRoleUpdateAndRemove() {
        given().cookie("stv_session", alex).contentType(ContentType.JSON).body("{\"teamRole\":\"COACH\"}")
                .put("/api/teams/" + TEAM_1 + "/members/" + ANNA).then().statusCode(200)
                .body("teamRole", equalTo("COACH"));
        given().cookie("stv_session", alex).get("/api/teams/" + TEAM_1).then().statusCode(200)
                .body("coaches.firstName", contains("Petra", "Anna"))
                .body("lineup.noLine.firstName", not(hasItem("Anna")));

        given().cookie("stv_session", alex).delete("/api/teams/" + TEAM_1 + "/members/" + ANNA).then().statusCode(204);
        given().cookie("stv_session", alex).delete("/api/teams/" + TEAM_1 + "/members/" + ANNA).then().statusCode(404);
        given().cookie("stv_session", login("anna")).get("/api/teams").then().statusCode(200).body("", empty());
    }

    @Test
    void teamCreateAndRename() {
        String id = given().cookie("stv_session", alex).contentType(ContentType.JSON).body("{\"name\":\"Team 4\"}")
                .post("/api/teams").then().statusCode(201)
                .body("name", equalTo("Team 4"))
                .extract().path("id");
        given().cookie("stv_session", alex).contentType(ContentType.JSON).body("{\"name\":\"Damen 4\"}")
                .put("/api/teams/" + id).then().statusCode(200).body("name", equalTo("Damen 4"));
        given().cookie("stv_session", alex).get("/api/teams").then().statusCode(200)
                .body("name", contains("Damen 4", "Team 1", "Team 2", "Team 3"));
        given().cookie("stv_session", alex).contentType(ContentType.JSON).body("{\"name\":\"\"}")
                .post("/api/teams").then().statusCode(400).body("fields[0].field", equalTo("name"));
    }

    @Test
    void sai02ArchiveIsReadableButNotChangeable() {
        given().cookie("stv_session", alex).queryParam("season", SEASON_2025).get("/api/teams/" + TEAM_1).then().statusCode(200)
                .body("coaches", empty())
                .body("lineup.noLine.firstName", contains("Vera"));

        given().cookie("stv_session", alex).queryParam("season", SEASON_2025).contentType(ContentType.JSON)
                .body("{\"personId\":\"" + ANNA + "\",\"teamRole\":\"PLAYER\"}")
                .post("/api/teams/" + TEAM_1 + "/members").then().statusCode(409)
                .body("rule", equalTo("SAI-02"));
        given().cookie("stv_session", alex).queryParam("season", SEASON_2025).contentType(ContentType.JSON)
                .body("{\"teamRole\":\"COACH\"}")
                .put("/api/teams/" + TEAM_1 + "/members/" + VERA).then().statusCode(409);
        given().cookie("stv_session", alex).queryParam("season", SEASON_2025)
                .delete("/api/teams/" + TEAM_1 + "/members/" + VERA).then().statusCode(409);
        given().cookie("stv_session", alex).queryParam("season", SEASON_2025).contentType(ContentType.JSON)
                .body("{\"name\":\"Team X\"}").post("/api/teams").then().statusCode(409);
    }

    /** Nachbesserung #21: Umbenennen in der aktuellen Saison lässt das Archiv unverändert (SAI-02). */
    @Test
    void sai02RenameChangedTheArchiveNot() {
        given().cookie("stv_session", alex).contentType(ContentType.JSON).body("{\"name\":\"Damen 1\"}")
                .put("/api/teams/" + TEAM_1).then().statusCode(200).body("name", equalTo("Damen 1"));

        // Aktuelle Saison: neuer Name
        given().cookie("stv_session", alex).get("/api/teams/" + TEAM_1).then().statusCode(200)
                .body("name", equalTo("Damen 1"));
        given().cookie("stv_session", alex).get("/api/teams").then().statusCode(200)
                .body("name", contains("Damen 1", "Team 2", "Team 3"));
        // Archiv: weiterhin der damalige Name
        given().cookie("stv_session", alex).queryParam("season", SEASON_2025).get("/api/teams/" + TEAM_1).then().statusCode(200)
                .body("name", equalTo("Team 1"));
        given().cookie("stv_session", alex).queryParam("season", SEASON_2025).get("/api/teams").then().statusCode(200)
                .body("name", contains("Team 1"));
        // Personendetail: Zugehörigkeit im Archiv mit dem damaligen Namen (Vera war nur 2025/26 in Team 1)
        given().cookie("stv_session", alex).get("/api/persons/" + VERA).then().statusCode(200)
                .body("memberships.team.name", contains("Team 1"))
                .body("memberships.seasonId", contains(SEASON_2025));
        // Mein Bereich zeigt den aktuellen Namen
        given().cookie("stv_session", login("petra")).get("/api/me").then().statusCode(200)
                .body("memberships.team.name", contains("Damen 1", "Team 2"));
    }

    @Test
    void sai02ArchiveLetsItselfNotRename() {
        given().cookie("stv_session", alex).queryParam("season", SEASON_2025).contentType(ContentType.JSON)
                .body("{\"name\":\"Team X\"}").put("/api/teams/" + TEAM_1).then().statusCode(409)
                .body("rule", equalTo("SAI-02"));
        given().cookie("stv_session", alex).queryParam("season", SEASON_2025).contentType(ContentType.JSON)
                .body("{\"name\":\"Team X\"}").put("/api/teams/" + TEAM_2).then().statusCode(404);
        given().cookie("stv_session", alex).queryParam("season", SEASON_2025).get("/api/teams/" + TEAM_1).then().statusCode(200)
                .body("name", equalTo("Team 1"));
    }

    @Test
    void teamTheItInTheSeasonNotGives() {
        given().cookie("stv_session", alex).queryParam("season", SEASON_2025).get("/api/teams/" + TEAM_2).then().statusCode(404);
        given().cookie("stv_session", alex).get("/api/teams/10000000-0000-4000-8000-000000000099").then().statusCode(404);
    }

    @Test
    void rr07OnlyAdminsManageTeams() {
        String petra = login("petra");
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{\"name\":\"Team 9\"}")
                .post("/api/teams").then().statusCode(403);
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{\"name\":\"Team 9\"}")
                .put("/api/teams/" + TEAM_1).then().statusCode(403);
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"personId\":\"" + TINA + "\",\"teamRole\":\"PLAYER\"}")
                .post("/api/teams/" + TEAM_1 + "/members").then().statusCode(403);
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{\"teamRole\":\"COACH\"}")
                .put("/api/teams/" + TEAM_1 + "/members/" + ANNA).then().statusCode(403);
        given().cookie("stv_session", petra).delete("/api/teams/" + TEAM_1 + "/members/" + ANNA).then().statusCode(403);
        given().cookie("stv_session", petra).get("/api/teams/" + TEAM_3).then().statusCode(403);
    }
}
