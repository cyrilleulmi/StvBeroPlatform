package ch.stvbero.platform.core.league;

import ch.stvbero.platform.core.swissunihockey.SyncJob;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** F-17: league page of a team (SUH, SUH-09) against the simulator. IDs from docs/test-data.md. */
@QuarkusTest
class LeagueTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String T04 = "40000000-0000-4000-8000-000000000004";

    @Inject
    SyncJob job;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static ValidatableResponse league(String person, String team) {
        return given().cookie("stv_session", login(person)).get("/api/teams/" + team + "/league").then();
    }

    private static ValidatableResponse refresh(String person, String team) {
        return given().cookie("stv_session", login(person)).post("/api/teams/" + team + "/league/refresh").then();
    }

    private static void presetSimulator(String body) {
        given().contentType(ContentType.JSON).body(body).put("/api/test/swiss-unihockey/matches").then()
                .statusCode(204);
    }

    private static final String OTHER_LEAGUE = """
            {"suTeamId":900001,"matches":[],"league":{"league":"Herren Kleinfeld 2. Liga","group":"Gruppe 3",
             "table":[{"rank":1,"suTeamId":900001,"team":"Team 1","matches":1,"wins":1,"draws":0,"losses":0,
                       "goalsFor":5,"goalsAgainst":3,"points":2},
                      {"rank":2,"team":"UHC Neu","matches":1,"wins":0,"draws":0,"losses":1,
                       "goalsFor":3,"goalsAgainst":5,"points":0}],
             "matches":[{"suMatchId":7000001,"startTime":"2026-11-14T10:00:00+01:00","homeTeam":"Team 1",
                         "awayTeam":"UHC Neu","hall":"Sporthalle Nord","location":"Beromünster","result":"5:3"}]}}""";

    // ------------------------------------------------------------------ Criteria 2 to 5, 7: seed of Team 1

    @Test
    void f17SeedTableWithEightTeamsOwnTeamMarked() {
        league("anna", TEAM_1).statusCode(200)
                .body("linked", equalTo(true))
                .body("teamName", equalTo("Team 1"))
                .body("league", equalTo("Herren Kleinfeld 3. Liga"))
                .body("group", equalTo("Gruppe 12"))
                .body("loadedAt", equalTo("2026-11-16T04:30:00+01:00"))
                .body("failedAt", nullValue())
                .body("table", hasSize(8))
                .body("table.rank", contains(1, 2, 3, 4, 5, 6, 7, 8))
                .body("table.findAll { it.own }.team", contains("Team 1"))
                .body("table[1].points", equalTo(9))
                .body("table[1].goalsFor", equalTo(31))
                .body("table[1].goalsAgainst", equalTo(22))
                .body("table[1].wins", equalTo(4))
                .body("table[1].draws", equalTo(1))
                .body("table[1].losses", equalTo(1));
    }

    @Test
    void f17UpcomingMatchesLinkToMatchDay() {
        league("anna", TEAM_1).statusCode(200)
                .body("upcoming.suMatchId", contains(5000001, 5000002, 5000017, 5000018))
                .body("upcoming[0].opponent", equalTo("UHC Musterdorf"))
                .body("upcoming[0].home", equalTo(true))
                .body("upcoming[0].hall", equalTo("Sporthalle Seefeld"))
                .body("upcoming[0].startTime", equalTo("2026-11-22T09:30:00+01:00"))
                .body("upcoming[0].eventId", equalTo(T04))
                .body("upcoming[1].opponent", equalTo("Unihockey Beispielstadt"))
                .body("upcoming[1].home", equalTo(false))
                .body("upcoming[1].eventId", equalTo(T04))
                .body("upcoming[2].eventId", nullValue());
    }

    @Test
    void f17LastFiveResults() {
        league("anna", TEAM_1).statusCode(200)
                .body("results.suMatchId", contains(5000016, 5000015, 5000014, 5000013, 5000012))
                .body("results[0].result", equalTo("6:5"))
                .body("results[0].homeTeam", equalTo("Team 1"))
                .body("results[0].awayTeam", equalTo("Unihockey Bergdorf"))
                .body("results[1].home", equalTo(false));
    }

    // ------------------------------------------------------------------ Criterion 1: not linked

    @Test
    void f17TeamWithoutLink() {
        league("marc", TEAM_2).statusCode(200)
                .body("linked", equalTo(false)).body("teamName", equalTo("Team 2"))
                .body("table", hasSize(0)).body("upcoming", hasSize(0)).body("loadedAt", nullValue());
        refresh("alex", TEAM_2).statusCode(409).body("rule", equalTo("SUH-02"));
    }

    // ------------------------------------------------------------------ Criterion 10: who sees it

    @Test
    void f17EveryoneWhoSeesTheTeam() {
        league("anna", TEAM_1).statusCode(200);   // player of Team 1
        league("petra", TEAM_1).statusCode(200);  // coach of Team 1
        league("marc", TEAM_1).statusCode(200);   // coach of Team 2 (coaches see all teams, E-69)
        league("alex", TEAM_1).statusCode(200);   // admin
        league("eva", TEAM_1).statusCode(403);    // player of Team 2 only
        given().get("/api/teams/" + TEAM_1 + "/league").then().statusCode(401);
        league("alex", "10000000-0000-4000-8000-000000000999").statusCode(404);
    }

    // ------------------------------------------------------------------ Criterion 6: reload by hand

    @Test
    void f17AdminReloads() {
        presetSimulator(OTHER_LEAGUE);
        league("anna", TEAM_1).body("league", equalTo("Herren Kleinfeld 3. Liga")); // cache, not live

        refresh("alex", TEAM_1).statusCode(200)
                .body("league", equalTo("Herren Kleinfeld 2. Liga"))
                .body("group", equalTo("Gruppe 3"))
                .body("table", hasSize(2))
                .body("table[0].own", equalTo(true))
                .body("results.result", contains("5:3"))
                .body("upcoming", hasSize(0))
                .body("loadedAt", equalTo("2026-11-16T06:00:00+01:00"));
        league("anna", TEAM_1).body("league", equalTo("Herren Kleinfeld 2. Liga"));
    }

    @Test
    void f17OnlyAdminsReload() {
        refresh("petra", TEAM_1).statusCode(403);
        refresh("anna", TEAM_1).statusCode(403);
        given().post("/api/teams/" + TEAM_1 + "/league/refresh").then().statusCode(401);
    }

    // ------------------------------------------------------------------ Criterion 9: swiss unihockey unavailable

    @Test
    void suh09UnavailableKeepsLastData() {
        presetSimulator("{\"suTeamId\":900001,\"unavailable\":true,\"matches\":[]}");
        refresh("alex", TEAM_1).statusCode(502).body("rule", equalTo("SUH-09"))
                .body("detail", equalTo(LeagueService.UNAVAILABLE));
        league("anna", TEAM_1).statusCode(200)
                .body("table", hasSize(8))
                .body("loadedAt", equalTo("2026-11-16T04:30:00+01:00"))
                .body("failedAt", equalTo("2026-11-16T06:00:00+01:00"));

        // The next successful load removes the hint
        presetSimulator("{\"suTeamId\":900001,\"unavailable\":false,\"matches\":[]}");
        refresh("alex", TEAM_1).statusCode(200).body("failedAt", nullValue()).body("table", hasSize(8));
    }

    @Test
    void suh09NeverLoaded() {
        String alex = login("alex");
        // Linking loads the league right away (SUH-02); swiss unihockey is down, so nothing was ever loaded
        presetSimulator("{\"suTeamId\":900002,\"unavailable\":true,\"matches\":[]}");
        given().cookie("stv_session", alex).contentType(ContentType.JSON).body("{\"suTeamId\":900002}")
                .post("/api/teams/" + TEAM_2 + "/swiss-unihockey/links").then().statusCode(201)
                .body("links[0].lastSyncOk", equalTo(false));
        league("marc", TEAM_2).statusCode(200)
                .body("linked", equalTo(true)).body("loadedAt", nullValue()).body("table", hasSize(0));

        // No data to keep showing: the message must not claim otherwise (test observation round 0)
        refresh("alex", TEAM_2).statusCode(502).body("rule", equalTo("SUH-09"))
                .body("detail", equalTo(LeagueService.UNAVAILABLE_NEVER_LOADED));
        league("marc", TEAM_2).statusCode(200)
                .body("loadedAt", nullValue()).body("failedAt", equalTo("2026-11-16T06:00:00+01:00"));
    }

    // ------------------------------------------------------------------ Criterion 6: daily sync

    @Test
    void f17DailySyncLoadsTheLeague() {
        presetSimulator(OTHER_LEAGUE);
        assertEquals(1, job.refreshLeagues());
        league("anna", TEAM_1).body("league", equalTo("Herren Kleinfeld 2. Liga"));

        presetSimulator("{\"suTeamId\":900001,\"unavailable\":true,\"matches\":[]}");
        assertEquals(0, job.refreshLeagues());
        league("anna", TEAM_1).body("league", equalTo("Herren Kleinfeld 2. Liga"))
                .body("failedAt", equalTo("2026-11-16T06:00:00+01:00"));
    }

    // ------------------------------------------------------------------ Simulator without a league view

    @Test
    void f17SimulatorWithoutLeagueViewKeepsIt() {
        presetSimulator("{\"suTeamId\":900001,\"matches\":[]}");
        refresh("alex", TEAM_1).statusCode(200).body("table", hasSize(8));
    }
}
