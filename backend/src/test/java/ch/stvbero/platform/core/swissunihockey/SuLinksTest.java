package ch.stvbero.platform.core.swissunihockey;

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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Links to swiss unihockey per season (SUH-02, SUH-11), warnings for changed and missing matches (SUH-12), the
 * state of the connection (SUH-13) and the usual meeting point of new match days (AUF-32), against the simulator.
 * IDs from docs/test-data.md.
 */
@QuarkusTest
class SuLinksTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String LINK_1 = "c0000000-0000-4000-8000-000000000001";
    static final String T04 = "40000000-0000-4000-8000-000000000004";
    static final String MATCH_01 = "50000000-0000-4000-8000-000000000001";
    static final String MATCH_02 = "50000000-0000-4000-8000-000000000002";
    static final String MATCH_01_SU = """
            {"suMatchId":5000001,"startTime":"2026-11-22T09:30:00+01:00","homeTeam":"Team 1",
             "awayTeam":"UHC Musterdorf","hall":"Sporthalle Seefeld","location":"Musterdorf",
             "mapsUrl":"https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf"}""";

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static void presetSimulator(String body) {
        given().contentType(ContentType.JSON).body(body).put("/api/test/swiss-unihockey/matches").then().statusCode(204);
    }

    private static String match02(String time, String opponent, String hall) {
        return """
                {"suMatchId":5000002,"startTime":"2026-11-22T%s:00+01:00","homeTeam":"%s",
                 "awayTeam":"Team 1","hall":"%s","location":"Musterdorf"}""".formatted(time, opponent, hall);
    }

    private static void deliver(String match02) {
        presetSimulator("{\"suTeamId\":900001,\"matches\":[" + MATCH_01_SU + "," + match02 + "]}");
    }

    private static ValidatableResponse sync(String session, String team) {
        return given().cookie("stv_session", session).post("/api/teams/" + team + "/swiss-unihockey/sync").then();
    }

    private static ValidatableResponse link(String session, String team, int suTeamId) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON)
                .body("{\"suTeamId\":" + suTeamId + "}").post("/api/teams/" + team + "/swiss-unihockey/links").then();
    }

    private static ValidatableResponse connection(String session, String team) {
        return given().cookie("stv_session", session).get("/api/teams/" + team + "/swiss-unihockey").then();
    }

    private static ValidatableResponse confirm(String session, String match) {
        return given().cookie("stv_session", session)
                .post("/api/events/" + T04 + "/matches/" + match + "/confirm-su-change").then();
    }

    private static JsonPath event(String session, String id) {
        return given().cookie("stv_session", session).get("/api/events/" + id).then().statusCode(200)
                .extract().jsonPath();
    }

    private static List<String> warningTypes(String session) {
        return given().cookie("stv_session", session).get("/api/events/" + T04 + "/overview").then().statusCode(200)
                .extract().jsonPath().getList("warnings.type");
    }

    // ------------------------------------------------------------------ Link dialog (SUH-02)

    @Test
    void suh02TeamsOfTheClubWithWhatTheyAreLinkedTo() {
        given().cookie("stv_session", login("petra")).get("/api/swiss-unihockey/teams").then().statusCode(200)
                .body("suTeamId", contains(900001, 900002, 900003))
                .body("name", contains("Herren KF 3. Liga", "Damen KF 2. Liga", "Junioren B"))
                .body("[0].linkedTeamId", equalTo(TEAM_1)).body("[0].linkedTeamName", equalTo("Team 1"))
                .body("[1].linkedTeamId", nullValue());
        // Players have no use for the dialog (RR-07)
        given().cookie("stv_session", login("anna")).get("/api/swiss-unihockey/teams").then().statusCode(403);
        given().get("/api/swiss-unihockey/teams").then().statusCode(401);
    }

    @Test
    void suh09TeamListUnavailableIsReportedAndLinkingByIdStillWorks() {
        String alex = login("alex");
        given().contentType(ContentType.JSON).body("{\"unavailable\":true}").put("/api/test/swiss-unihockey/club")
                .then().statusCode(204);
        given().cookie("stv_session", alex).get("/api/swiss-unihockey/teams").then().statusCode(502)
                .body("rule", equalTo("SUH-09")).body("detail", equalTo(SuLinks.CLUB_UNAVAILABLE));
        // Without the list the name is unknown; the link itself works
        link(alex, TEAM_2, 900002).statusCode(201).body("links[0].name", nullValue())
                .body("links[0].suTeamId", equalTo(900002));
    }

    @Test
    void suh02ConnectionIsVisibleToTheTeamAndManagedByCoachesAndAdmins() {
        connection(login("anna"), TEAM_1).statusCode(200).body("canManage", equalTo(false))
                .body("teamName", equalTo("Team 1")).body("links.id", contains(LINK_1))
                .body("links[0].suTeamId", equalTo(900001)).body("links[0].name", equalTo("Herren KF 3. Liga"))
                .body("links[0].lastSyncOk", equalTo(true))
                .body("links[0].lastSyncAt", equalTo("2026-11-16T04:30:00+01:00"));
        connection(login("marc"), TEAM_1).statusCode(200).body("canManage", equalTo(true));
        // Eva plays only in Team 2
        connection(login("eva"), TEAM_1).statusCode(403);
        given().cookie("stv_session", login("anna")).delete("/api/teams/" + TEAM_1 + "/swiss-unihockey/links/" + LINK_1)
                .then().statusCode(403);
    }

    // ------------------------------------------------------------------ Several links (SUH-11)

    @Test
    void suh11ATeamCanHaveSeveralLinksAndTheSyncTakesAllMatches() {
        String petra = login("petra");
        presetSimulator("""
                {"suTeamId":900003,"matches":[{"suMatchId":7000001,"startTime":"2026-12-13T10:00:00+01:00",
                 "homeTeam":"Team 1 Cup","awayTeam":"UHC Pokal","hall":"Cuphalle","location":"Pokalwil"}]}""");
        // Linking loads the matches right away
        link(petra, TEAM_1, 900003).statusCode(201).body("links.suTeamId", contains(900001, 900003))
                .body("links[1].name", equalTo("Junioren B")).body("links.lastSyncOk", everyItem(equalTo(true)));
        List<Map<String, Object>> matchDays = given().cookie("stv_session", petra)
                .queryParam("type", "MATCH_DAY").queryParam("view", "upcoming").get("/api/events").then()
                .statusCode(200).extract().jsonPath().getList("findAll { it.location?.name == 'Cuphalle' }");
        assertThat(matchDays, hasSize(1));
        JsonPath cup = event(petra, (String) matchDays.get(0).get("id"));
        // The own team has another name in the matches of the second link; home and away are still right
        assertThat(cup.getList("matches.opponent"), contains("UHC Pokal"));
        assertThat(cup.getList("matches.home"), contains(true));
        // The first link stays the one of the team (league page, F-17)
        given().cookie("stv_session", petra).get("/api/teams/" + TEAM_1).then().body("suTeamId", equalTo(900001));
        // A sync without changes leaves all three matches as they are
        sync(petra, TEAM_1).statusCode(200).body("unchanged", equalTo(3)).body("created", equalTo(0))
                .body("missing", equalTo(0));
        // One team at swiss unihockey belongs to one team only
        link(petra, TEAM_2, 900003).statusCode(409).body("rule", equalTo("SUH-02"));
    }

    @Test
    void suh11WithoutAnyLinkLeftUpcomingImportedMatchesAreMarkedMissing() {
        String petra = login("petra");
        given().cookie("stv_session", petra).delete("/api/teams/" + TEAM_1 + "/swiss-unihockey/links/" + LINK_1)
                .then().statusCode(204);
        connection(petra, TEAM_1).statusCode(200).body("links", empty());
        JsonPath matchDay = event(petra, T04);
        assertThat(matchDay.getList("matches.missingAtSu"), contains(true, true));
        assertThat(warningTypes(petra), hasItem("MATCH_MISSING"));
        // Now a coach can remove them
        given().cookie("stv_session", petra).delete("/api/events/" + T04 + "/matches/" + MATCH_02).then()
                .statusCode(204);
        given().cookie("stv_session", petra).delete("/api/teams/" + TEAM_1 + "/swiss-unihockey/links/" + LINK_1)
                .then().statusCode(404);
    }

    // ------------------------------------------------------------------ Warnings (SUH-12)

    @Test
    void suh12ChangedMatchShowsBeforeAndAfterUntilACoachConfirms() {
        String petra = login("petra");
        int before = event(petra, T04).getInt("warningCount");
        deliver(match02("12:15", "UHC Drei", "Sporthalle Seefeld"));
        sync(petra, TEAM_1).statusCode(200).body("changed", equalTo(1));

        JsonPath matchDay = event(petra, T04);
        assertEquals("2026-11-16T06:00:00+01:00", matchDay.getString("matches[1].suChange.changedAt"));
        assertThat(matchDay.getList("matches[1].suChange.changes.field"), contains("START_TIME", "OPPONENT"));
        assertThat(matchDay.getList("matches[1].suChange.changes.before"),
                contains("So 22.11. 11:45", "Unihockey Beispielstadt"));
        assertThat(matchDay.getList("matches[1].suChange.changes.after"), contains("So 22.11. 12:15", "UHC Drei"));
        assertThat(matchDay.get("matches[0].suChange"), nullValue());
        // The warning counts in the event list and shows in the overview, for coaches and admins
        assertEquals(before + 1, matchDay.getInt("warningCount"));
        assertThat(warningTypes(petra), hasItem("MATCH_CHANGED"));
        assertThat(given().cookie("stv_session", login("anna")).get("/api/events/" + T04 + "/overview").then()
                .statusCode(200).extract().jsonPath().get("warnings"), nullValue());

        // Players cannot confirm; a coach can, once
        confirm(login("anna"), MATCH_02).statusCode(403);
        confirm(petra, MATCH_01).statusCode(409).body("rule", equalTo("SUH-12"));
        confirm(petra, MATCH_02).statusCode(200).body("suChange", nullValue())
                .body("startTime", equalTo("2026-11-22T12:15:00+01:00")).body("opponent", equalTo("UHC Drei"));
        confirm(petra, MATCH_02).statusCode(409);
        assertEquals(before, event(petra, T04).getInt("warningCount"));
        assertThat(warningTypes(petra), not(hasItem("MATCH_CHANGED")));
    }

    @Test
    void suh12SecondChangeKeepsTheFirstBeforeAndChangingBackClearsTheWarning() {
        String petra = login("petra");
        deliver(match02("12:15", "Unihockey Beispielstadt", "Sporthalle Seefeld"));
        sync(petra, TEAM_1).statusCode(200);
        deliver(match02("13:00", "Unihockey Beispielstadt", "Sporthalle Seefeld"));
        sync(petra, TEAM_1).statusCode(200);
        JsonPath twice = event(petra, T04);
        assertThat(twice.getList("matches[1].suChange.changes.before"), contains("So 22.11. 11:45"));
        assertThat(twice.getList("matches[1].suChange.changes.after"), contains("So 22.11. 13:00"));

        deliver(match02("11:45", "Unihockey Beispielstadt", "Sporthalle Seefeld"));
        sync(petra, TEAM_1).statusCode(200);
        assertThat(event(petra, T04).get("matches[1].suChange"), nullValue());
        assertThat(warningTypes(petra), not(hasItem("MATCH_CHANGED")));
    }

    @Test
    void suh12MissingMatchShowsAWarning() {
        String petra = login("petra");
        presetSimulator("{\"suTeamId\":900001,\"matches\":[" + MATCH_01_SU + "]}");
        sync(petra, TEAM_1).statusCode(200).body("missing", equalTo(1));

        assertThat(event(petra, T04).getList("matches.missingAtSu"), contains(false, true));
        assertThat(warningTypes(petra), hasItem("MATCH_MISSING"));
        List<Map<String, Object>> warnings = given().cookie("stv_session", petra)
                .get("/api/events/" + T04 + "/overview").then().extract().jsonPath()
                .getList("warnings.findAll { it.type == 'MATCH_MISSING' }");
        assertEquals(1, warnings.get(0).get("count"));
    }

    // ------------------------------------------------------------------ State of the connection (SUH-13)

    @Test
    void suh13FailedSyncIsShownUntilTheNextOneWorks() {
        String petra = login("petra");
        given().cookie("stv_session", petra).get("/api/swiss-unihockey/status").then().statusCode(200)
                .body("linkedTeams", equalTo(1)).body("failures", empty())
                .body("lastSyncAt", equalTo("2026-11-16T04:30:00+01:00"));

        presetSimulator("{\"suTeamId\":900001,\"unavailable\":true,\"matches\":[]}");
        sync(petra, TEAM_1).statusCode(502).body("rule", equalTo("SUH-09"));
        // Nothing changed, the app keeps working, and it says so
        assertThat(event(petra, T04).getList("matches.id"), containsInAnyOrder(MATCH_01, MATCH_02));
        given().cookie("stv_session", petra).get("/api/swiss-unihockey/status").then().statusCode(200)
                .body("failures", hasSize(1)).body("failures[0].teamId", equalTo(TEAM_1))
                .body("failures[0].teamName", equalTo("Team 1")).body("failures[0].suTeamId", equalTo(900001))
                .body("failures[0].message", equalTo(SuSync.FAILED))
                .body("failures[0].at", equalTo("2026-11-16T06:00:00+01:00"));
        connection(petra, TEAM_1).body("links[0].lastSyncOk", equalTo(false))
                .body("links[0].lastSyncError", notNullValue());
        given().cookie("stv_session", login("anna")).get("/api/swiss-unihockey/status").then().statusCode(403);

        deliver(match02("11:45", "Unihockey Beispielstadt", "Sporthalle Seefeld"));
        sync(petra, TEAM_1).statusCode(200);
        given().cookie("stv_session", petra).get("/api/swiss-unihockey/status").then().body("failures", empty())
                .body("lastSyncAt", equalTo("2026-11-16T06:00:00+01:00"));
        connection(petra, TEAM_1).body("links[0].lastSyncOk", equalTo(true))
                .body("links[0].lastSyncError", nullValue());
    }

    // ------------------------------------------------------------------ Usual meeting point (AUF-32)

    @Test
    void auf32NewMatchDayStartsWithTheUsualMeetingPointAndNoTime() {
        String petra = login("petra");
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"matchDurationMinutes\":60,\"arrivalLeadMinutes\":45,\"meetingPoint\":\" Schulhaus Dorf \"}")
                .put("/api/teams/" + TEAM_1 + "/match-day-settings").then().statusCode(200)
                .body("meetingPoint", equalTo("Schulhaus Dorf"));
        presetSimulator("{\"suTeamId\":900001,\"matches\":[" + MATCH_01_SU + ","
                + match02("11:45", "Unihockey Beispielstadt", "Sporthalle Seefeld") + ","
                + "{\"suMatchId\":5000040,\"startTime\":\"2026-12-06T10:00:00+01:00\",\"homeTeam\":\"Team 1\","
                + "\"awayTeam\":\"UHC Drei\",\"hall\":\"Turnhalle Weid\",\"location\":\"Beispielstadt\"}]}");
        String created = sync(petra, TEAM_1).statusCode(200).body("created", equalTo(1)).extract()
                .jsonPath().getString("affectedEvents[0]");

        JsonPath matchDay = event(petra, created);
        assertEquals("Schulhaus Dorf", matchDay.getString("meetingPoint.location"));
        assertThat(matchDay.get("meetingPoint.time"), nullValue());
        // The end follows the match duration of the team (TML-02)
        assertEquals("2026-12-06T11:00:00+01:00", matchDay.getString("end"));
        // The existing match day keeps its own meeting point (SUH-06)
        assertEquals("Parkplatz Sporthalle Nord", event(petra, T04).getString("meetingPoint.location"));
    }
}
