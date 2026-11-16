package ch.stvbero.platform.core.swissunihockey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * F-07: swiss unihockey integration (SUH-02 to SUH-10, BEN-06, S12) against the simulator. IDs from docs/test-data.md.
 */
@QuarkusTest
class SwissUnihockeyTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String T04 = "40000000-0000-4000-8000-000000000004";
    static final String T05 = "40000000-0000-4000-8000-000000000005";
    static final String MATCH_02 = "50000000-0000-4000-8000-000000000002";

    static final String MATCH_01_SU = """
            {"suMatchId":5000001,"startTime":"2026-11-22T09:30:00+01:00","homeTeam":"Team 1",
             "awayTeam":"UHC Musterdorf","hall":"Sporthalle Seefeld","location":"Musterdorf",
             "mapsUrl":"https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf"}""";

    @Inject
    SwissUnihockeySource source;

    @Inject
    SyncJob job;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static void presetSimulator(String body) {
        given().contentType(ContentType.JSON).body(body).put("/api/test/swiss-unihockey/matches").then().statusCode(204);
    }

    private static String match02(String time) {
        return """
                {"suMatchId":5000002,"startTime":"2026-11-22T%s:00+01:00","homeTeam":"Unihockey Beispielstadt",
                 "awayTeam":"Team 1","hall":"Sporthalle Seefeld","location":"Musterdorf"}""".formatted(time);
    }

    private static ValidatableResponse sync(String session, String team) {
        return given().cookie("stv_session", session).post("/api/teams/" + team + "/swiss-unihockey/sync").then();
    }

    private static ValidatableResponse link(String session, String team, String suTeamId) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON)
                .body("{\"suTeamId\":" + suTeamId + "}").put("/api/teams/" + team + "/swiss-unihockey").then();
    }

    private static JsonPath event(String id) {
        return given().cookie("stv_session", login("alex")).get("/api/events/" + id).then().statusCode(200)
                .extract().jsonPath();
    }

    private static List<Map<String, Object>> newNotifications() {
        List<Map<String, Object>> all = given().get("/api/test/notifications").then().statusCode(200)
                .extract().jsonPath().getList("$");
        return all.stream().filter(notification -> !((String) notification.get("id")).startsWith("90000000")).toList();
    }

    // ------------------------------------------------------------------ Criterion 1: link (SUH-02)

    @Test
    void suh02AdminLinksTeamAndTriggersSync() {
        String alex = login("alex");
        link(alex, TEAM_2, "900002").statusCode(200)
                .body("id", equalTo(TEAM_2)).body("name", equalTo("Team 2")).body("suTeamId", equalTo(900002));
        given().cookie("stv_session", alex).get("/api/teams/" + TEAM_2).then().body("suTeamId", equalTo(900002));

        presetSimulator("{\"suTeamId\":900002,\"matches\":[{\"suMatchId\":6000001,"
                + "\"startTime\":\"2026-12-05T13:00:00+01:00\",\"homeTeam\":\"UHC Fünf\",\"awayTeam\":\"Team 2\","
                + "\"hall\":\"Turnhalle Weid\",\"location\":\"Beispielstadt\"}]}");
        sync(alex, TEAM_2).statusCode(200).body("created", equalTo(1)).body("affectedEvents", hasSize(1));

        link(alex, TEAM_2, "null").statusCode(200).body("suTeamId", nullValue());
        sync(alex, TEAM_2).statusCode(409).body("rule", equalTo("SUH-02"));
    }

    @Test
    void suh02OnlyAdmins() {
        String petra = login("petra");
        link(petra, TEAM_1, "900009").statusCode(403);
        sync(petra, TEAM_1).statusCode(403);
        given().post("/api/teams/" + TEAM_1 + "/swiss-unihockey/sync").then().statusCode(401);
    }

    @Test
    void suh02TeamIdUniqueAndPositive() {
        String alex = login("alex");
        link(alex, TEAM_2, "900001").statusCode(409).body("rule", equalTo("SUH-02"));
        link(alex, TEAM_2, "0").statusCode(400).body("fields[0].field", equalTo("suTeamId"));
        link(alex, TEAM_1, "900001").statusCode(200);
        link(alex, "10000000-0000-4000-8000-000000000999", "900005").statusCode(404);
        sync(alex, "10000000-0000-4000-8000-000000000999").statusCode(404);
    }

    // ------------------------------------------------------------------ Criterion 2: simulator in dev/test

    @Test
    void inTestProfileAppTalksToTheSimulator() {
        assertInstanceOf(SuSimulator.class, io.quarkus.arc.ClientProxy.unwrap(source));
    }

    @Test
    void afterResetEverythingIsInSync() {
        sync(login("alex"), TEAM_1).statusCode(200)
                .body("created", equalTo(0)).body("changed", equalTo(0)).body("missing", equalTo(0))
                .body("unchanged", equalTo(2)).body("affectedEvents", hasSize(0));
        assertThat(newNotifications(), hasSize(0));
    }

    // ------------------------------------------------------------------ Criterion 3: form match days (SUH-04)

    @Test
    void suh04MatchesOnSameDayInSameHallFormOneMatchDay() {
        presetSimulator("""
                {"suTeamId":900001,"matches":[%s,%s,
                 {"suMatchId":5000020,"startTime":"2026-12-06T10:00:00+01:00","homeTeam":"Team 1",
                  "awayTeam":"UHC Drei","hall":"Sporthalle Nord","location":"Beromünster"},
                 {"suMatchId":5000021,"startTime":"2026-12-06T12:30:00+01:00","homeTeam":"UHC Vier",
                  "awayTeam":"Team 1","hall":"Sporthalle Nord","location":"Beromünster","result":null},
                 {"suMatchId":5000022,"startTime":"2026-12-06T15:00:00+01:00","homeTeam":"Team 1",
                  "awayTeam":"UHC Fünf","hall":"Turnhalle Weid","location":"Beispielstadt"},
                 {"suMatchId":5000023,"startTime":"2026-12-13T10:00:00+01:00","homeTeam":"UHC Sechs",
                  "awayTeam":"Team 1","hall":"Sporthalle Nord","location":"Beromünster","latitude":47.2,"longitude":8.2}]}"""
                .formatted(MATCH_01_SU, match02("11:45")));

        List<String> events = sync(login("alex"), TEAM_1).statusCode(200)
                .body("created", equalTo(4)).body("unchanged", equalTo(2))
                .extract().jsonPath().getList("affectedEvents");
        assertThat(events, hasSize(3));

        JsonPath tournament = event(events.get(0));
        assertEquals("MATCH_DAY", tournament.getString("type"));
        assertEquals("Spieltag", tournament.getString("title"));
        assertThat(tournament.getList("teams.id"), contains(TEAM_1));
        assertThat(tournament.getList("matches.opponent"), contains("UHC Drei", "UHC Vier"));
        assertThat(tournament.getList("matches.home"), contains(true, false));
        assertThat(tournament.getList("matches.source"), everyItem(equalTo("SWISS_UNIHOCKEY")));
        assertEquals("Sporthalle Nord", tournament.getString("location.name"));
        assertEquals("Beromünster", tournament.getString("location.address"));
        // SUH-07: Google Maps link generated automatically
        assertEquals("https://www.google.com/maps/search/?api=1&query=Sporthalle+Nord+Beromünster"
                .replace("ü", "%C3%BC"), tournament.getString("location.mapsUrl"));
        assertEquals("2026-12-06T10:00:00+01:00", tournament.getString("start"));
        assertEquals("2026-12-06T13:45:00+01:00", tournament.getString("end"));

        JsonPath otherHall = event(events.get(1));
        assertThat(otherHall.getList("matches.opponent"), contains("UHC Fünf"));
        JsonPath otherDay = event(events.get(2));
        assertThat(otherDay.getList("matches.opponent"), contains("UHC Sechs"));
        assertEquals("https://www.google.com/maps/search/?api=1&query=47.2%2C8.2", otherDay.getString("location.mapsUrl"));

        // Syncing again changes nothing
        sync(login("alex"), TEAM_1).statusCode(200).body("created", equalTo(0)).body("unchanged", equalTo(6));
    }

    @Test
    void suh04NewMatchIsAddedToTheExistingMatchDay() {
        presetSimulator("""
                {"suTeamId":900001,"matches":[%s,%s,
                 {"suMatchId":5000030,"startTime":"2026-11-22T14:00:00+01:00","homeTeam":"Team 1",
                  "awayTeam":"UHC Drei","hall":"Sporthalle Seefeld","location":"Musterdorf"}]}"""
                .formatted(MATCH_01_SU, match02("11:45")));
        sync(login("alex"), TEAM_1).statusCode(200).body("created", equalTo(1))
                .body("affectedEvents", contains(T04));
        JsonPath matchDay = event(T04);
        assertThat(matchDay.getList("matches.opponent"), contains("UHC Musterdorf", "Unihockey Beispielstadt", "UHC Drei"));
        assertEquals("2026-11-22T15:15:00+01:00", matchDay.getString("end"));
    }

    // ------------------------------------------------------------------ Criterion 4: S12 (SUH-05, SUH-06, BEN-06)

    @Test
    void s12ReschedulingIsAppliedOwnDataStays() {
        String alex = login("alex");
        JsonPath before = event(T04);
        List<Object> callUpBefore = given().cookie("stv_session", alex).get("/api/events/" + T04 + "/call-up")
                .then().statusCode(200).extract().jsonPath().getList("groups");

        presetSimulator("{\"suTeamId\":900001,\"matches\":[" + MATCH_01_SU + "," + match02("12:15") + "]}");
        sync(alex, TEAM_1).statusCode(200)
                .body("changed", equalTo(1)).body("unchanged", equalTo(1)).body("created", equalTo(0))
                .body("affectedEvents", contains(T04));

        JsonPath after = event(T04);
        assertThat(after.getList("matches.startTime"),
                contains("2026-11-22T09:30:00+01:00", "2026-11-22T12:15:00+01:00"));
        assertThat(after.getList("matches.id"), hasItem(MATCH_02));
        // SUH-06: own data unchanged
        assertEquals(before.getString("meetingPoint.time"), after.getString("meetingPoint.time"));
        assertEquals(before.getString("meetingPoint.location"), after.getString("meetingPoint.location"));
        assertEquals("Weisse Trikots mitnehmen", after.getString("notes"));
        assertEquals(before.getString("start"), after.getString("start"));
        assertEquals("2026-11-22T13:30:00+01:00", after.getString("end"));
        assertEquals(before.getMap("counts"), after.getMap("counts"), "Responses stay");
        assertEquals(callUpBefore, given().cookie("stv_session", alex).get("/api/events/" + T04 + "/call-up")
                .then().extract().jsonPath().getList("groups"), "Call-up stays");

        // BEN-06: all members of Team 1 (Petra, Anna, Lea, Mia, Sara, Nina, Jana, Kim), not Team 2
        List<Map<String, Object>> created = newNotifications();
        assertThat(created.stream().map(notification -> ((Map<?, ?>) notification.get("recipient")).get("firstName")).toList(),
                containsInAnyOrder("Petra", "Anna", "Lea", "Mia", "Sara", "Nina", "Jana", "Kim"));
        assertThat(created.stream().map(notification -> notification.get("type")).toList(), everyItem(equalTo("MATCH_CHANGED")));
        assertThat(created.stream().map(notification -> notification.get("eventId")).toList(), everyItem(equalTo(T04)));
        assertThat(created.stream().map(notification -> notification.get("text")).toList(), everyItem(equalTo(
                "Spieltag So 22.11. wurde geändert: Spiel gegen Unihockey Beispielstadt neu um 12:15.")));

        // Second sync: nothing left to do, no further notification
        sync(alex, TEAM_1).statusCode(200).body("changed", equalTo(0)).body("unchanged", equalTo(2));
        assertThat(newNotifications(), hasSize(8));
    }

    @Test
    void suh05RescheduledFirstMatchMovesTheMatchDay() {
        presetSimulator("""
                {"suTeamId":900001,"matches":[
                 {"suMatchId":5000001,"startTime":"2026-11-22T10:00:00+01:00","homeTeam":"Team 1",
                  "awayTeam":"UHC Musterdorf","hall":"Sporthalle Seefeld","location":"Musterdorf"},%s]}"""
                .formatted(match02("11:45")));
        sync(login("alex"), TEAM_1).statusCode(200).body("changed", equalTo(1));
        JsonPath matchDay = event(T04);
        assertEquals("2026-11-22T10:00:00+01:00", matchDay.getString("start"));
        assertEquals(true, matchDay.getBoolean("rescheduled"));
        assertEquals("2026-11-22T08:00:00+01:00", matchDay.getString("meetingPoint.time"), "Meeting point stays (SUH-06)");
    }

    @Test
    void suh05MatchMovedToOtherDayBecomesItsOwnMatchDay() {
        presetSimulator("""
                {"suTeamId":900001,"matches":[%s,
                 {"suMatchId":5000002,"startTime":"2026-11-29T11:45:00+01:00","homeTeam":"Unihockey Beispielstadt",
                  "awayTeam":"Team 1","hall":"Turnhalle Weid","location":"Beispielstadt"}]}""".formatted(MATCH_01_SU));
        List<String> affected = sync(login("alex"), TEAM_1).statusCode(200).body("changed", equalTo(1))
                .extract().jsonPath().getList("affectedEvents");
        assertThat(affected, hasSize(2));
        assertThat(event(T04).getList("matches.opponent"), contains("UHC Musterdorf"));
        String newEvent = affected.stream().filter(id -> !id.equals(T04)).findFirst().orElseThrow();
        JsonPath newMatchDay = event(newEvent);
        assertThat(newMatchDay.getList("matches.id"), contains(MATCH_02));
        assertEquals("Turnhalle Weid", newMatchDay.getString("location.name"));
        assertThat(newNotifications().stream().map(notification -> notification.get("text")).distinct().toList(), contains(
                "Spieltag So 22.11. wurde geändert: Spiel gegen Unihockey Beispielstadt neu am So 29.11. 11:45, "
                        + "neu in Turnhalle Weid."));
    }

    private static final String MATCH_01_29 = """
            {"suMatchId":5000001,"startTime":"2026-11-29T09:30:00+01:00","homeTeam":"Team 1",
             "awayTeam":"UHC Musterdorf","hall":"Sporthalle Seefeld","location":"Musterdorf"}""";
    private static final String MATCH_02_29 = """
            {"suMatchId":5000002,"startTime":"2026-11-29T11:45:00+01:00","homeTeam":"Unihockey Beispielstadt",
             "awayTeam":"Team 1","hall":"Sporthalle Seefeld","location":"Musterdorf"}""";

    @Test
    void suh05WholeMatchDayRescheduledStaysAMatchDay() {
        checkWholeMatchDayRescheduled(MATCH_01_29 + "," + MATCH_02_29);
    }

    @Test
    void suh05WholeMatchDayRescheduledInReverseOrder() {
        checkWholeMatchDayRescheduled(MATCH_02_29 + "," + MATCH_01_29);
    }

    private void checkWholeMatchDayRescheduled(String matches) {
        String alex = login("alex");
        JsonPath before = event(T04);
        List<Object> callUpBefore = given().cookie("stv_session", alex).get("/api/events/" + T04 + "/call-up")
                .then().statusCode(200).extract().jsonPath().getList("groups");

        presetSimulator("{\"suTeamId\":900001,\"matches\":[" + matches + "]}");
        sync(alex, TEAM_1).statusCode(200).body("changed", equalTo(2)).body("created", equalTo(0))
                .body("affectedEvents", contains(T04));

        JsonPath after = event(T04);
        assertThat(after.getList("matches.startTime"),
                contains("2026-11-29T09:30:00+01:00", "2026-11-29T11:45:00+01:00"));
        assertThat(after.getList("matches.id"), hasItem(MATCH_02));
        assertEquals("2026-11-29T09:30:00+01:00", after.getString("start"));
        assertEquals(true, after.getBoolean("rescheduled"));
        assertEquals("Sporthalle Seefeld", after.getString("location.name"));
        // SUH-06: own data stays with the existing match day
        assertEquals(before.getString("meetingPoint.time"), after.getString("meetingPoint.time"));
        assertEquals(before.getString("meetingPoint.location"), after.getString("meetingPoint.location"));
        assertEquals("Weisse Trikots mitnehmen", after.getString("notes"));
        assertEquals(before.getMap("counts"), after.getMap("counts"), "Responses stay");
        assertEquals(callUpBefore, given().cookie("stv_session", alex).get("/api/events/" + T04 + "/call-up")
                .then().extract().jsonPath().getList("groups"), "Call-up stays");

        // No additional match day: a second sync changes nothing and creates nothing (SUH-04)
        sync(alex, TEAM_1).statusCode(200).body("changed", equalTo(0)).body("created", equalTo(0))
                .body("unchanged", equalTo(2));
        assertThat(newNotifications().stream().map(notification -> notification.get("eventId")).distinct().toList(),
                contains(T04));
    }

    @Test
    void suh08ResultIsAppliedWithoutNotification() {
        presetSimulator("{\"suTeamId\":900001,\"matches\":[" + MATCH_01_SU.replace("\"hall\"", "\"result\":\"5:3\",\"hall\"")
                + "," + match02("11:45") + "]}");
        sync(login("alex"), TEAM_1).statusCode(200).body("changed", equalTo(1));
        assertThat(event(T04).getList("matches.result"), contains("5:3", null));
        assertThat(newNotifications(), hasSize(0));
    }

    // ------------------------------------------------------------------ Criterion 5: match missing

    @Test
    void missingMatchIsMarkedNotDeleted() {
        String alex = login("alex");
        presetSimulator("{\"suTeamId\":900001,\"matches\":[" + MATCH_01_SU + "]}");
        sync(alex, TEAM_1).statusCode(200).body("missing", equalTo(1)).body("unchanged", equalTo(1))
                .body("affectedEvents", contains(T04));
        JsonPath matchDay = event(T04);
        assertThat(matchDay.getList("matches.missingAtSu"), contains(false, true));
        assertThat(matchDay.getList("matches.id"), hasItem(MATCH_02));

        // Now the coach may delete it (SUH-06)
        given().cookie("stv_session", login("petra")).delete("/api/events/" + T04 + "/matches/" + MATCH_02)
                .then().statusCode(204);
    }

    @Test
    void matchDeliveredAgainIsNoLongerMarked() {
        String alex = login("alex");
        presetSimulator("{\"suTeamId\":900001,\"matches\":[" + MATCH_01_SU + "]}");
        sync(alex, TEAM_1).statusCode(200);
        given().post("/api/test/reset").then().statusCode(204);
        presetSimulator("{\"suTeamId\":900001,\"matches\":[" + MATCH_01_SU + "]}");
        sync(alex = login("alex"), TEAM_1).statusCode(200);
        presetSimulator("{\"suTeamId\":900001,\"matches\":[" + MATCH_01_SU + "," + match02("11:45") + "]}");
        sync(alex, TEAM_1).statusCode(200).body("changed", equalTo(1)).body("missing", equalTo(0));
        assertThat(event(T04).getList("matches.missingAtSu"), everyItem(equalTo(false)));
    }

    // ------------------------------------------------------------------ Criterion 6: unavailable (SUH-09)

    @Test
    void suh09UnavailableChangesNothing() {
        String alex = login("alex");
        JsonPath before = event(T04);
        presetSimulator("{\"suTeamId\":900001,\"unavailable\":true,\"matches\":[" + MATCH_01_SU + "]}");

        sync(alex, TEAM_1).statusCode(502).contentType("application/problem+json")
                .body("rule", equalTo("SUH-09"))
                .body("detail", equalTo(SuSync.UNAVAILABLE));

        JsonPath after = event(T04);
        assertEquals(before.getList("matches"), after.getList("matches"));
        assertThat(newNotifications(), hasSize(0));
        // The app keeps working normally
        given().cookie("stv_session", login("anna")).get("/api/events").then().statusCode(200);
        given().cookie("stv_session", alex).get("/api/teams").then().statusCode(200);
    }

    // ------------------------------------------------------------------ Criterion 7: daily job (SUH-03)

    @Test
    void suh03DailyJobSyncsAllLinkedTeams() {
        String alex = login("alex");
        link(alex, TEAM_2, "900002").statusCode(200);
        presetSimulator("{\"suTeamId\":900002,\"unavailable\":true,\"matches\":[]}");
        presetSimulator("{\"suTeamId\":900001,\"matches\":[" + MATCH_01_SU + "," + match02("12:15") + "]}");

        // Team 2 fails, Team 1 still runs
        assertEquals(1, job.syncAll());
        assertThat(event(T04).getList("matches.startTime"), hasItem("2026-11-22T12:15:00+01:00"));
    }

    // ------------------------------------------------------------------ SUH-10, scope

    @Test
    void suh10ManuallyCreatedMatchDaysStayUntouched() {
        String alex = login("alex");
        link(alex, TEAM_2, "900002").statusCode(200);
        JsonPath before = event(T05);
        // swiss unihockey delivers a match for Team 2 on the same day in the same hall as the manual event 05
        presetSimulator("{\"suTeamId\":900002,\"matches\":[{\"suMatchId\":6000005,"
                + "\"startTime\":\"2026-11-28T15:00:00+01:00\",\"homeTeam\":\"Team 2\",\"awayTeam\":\"UHC Fünf\","
                + "\"hall\":\"Turnhalle Weid\",\"location\":\"Beispielstadt\"}]}");
        List<String> affected = sync(alex, TEAM_2).statusCode(200).body("created", equalTo(1))
                .extract().jsonPath().getList("affectedEvents");
        assertThat(affected, not(hasItem(T05)));
        JsonPath after = event(T05);
        assertEquals(before.getList("matches"), after.getList("matches"));
        assertEquals(before.getString("end"), after.getString("end"));
    }

    @Test
    void syncWithoutCurrentSeasonGives409() {
        given().delete("/api/test/current-season").then().statusCode(204);
        sync(login("alex"), TEAM_1).statusCode(409).body("rule", equalTo("SAI-03"));
    }

    @Test
    void simulatorPresetIsValidated() {
        given().contentType(ContentType.JSON).body("{\"suTeamId\":900001,\"matches\":[{\"suMatchId\":1}]}")
                .put("/api/test/swiss-unihockey/matches").then().statusCode(400);
    }
}
