package ch.stvbero.platform.driver;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Timeline of a match day (TML-01 to TML-07) and the match day settings of a team (TML-02, TML-03), against the
 * seed of docs/test-data.md: match day 04 with matches at 09:30 and 11:45, meeting point 08:00 «Parkplatz
 * Sporthalle Nord», simulated route 23.4 km and 26 minutes. Default "now": Mon 16.11.2026 06:00.
 */
@QuarkusTest
class TimelineTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String T01 = "40000000-0000-4000-8000-000000000001";
    static final String T04 = "40000000-0000-4000-8000-000000000004";
    static final String T05 = "40000000-0000-4000-8000-000000000005";
    static final String MATCH_01 = "50000000-0000-4000-8000-000000000001";

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static ValidatableResponse timeline(String session, String event) {
        return given().cookie("stv_session", session).get("/api/events/" + event + "/timeline").then();
    }

    private static ValidatableResponse apply(String session, String event) {
        return given().cookie("stv_session", session).post("/api/events/" + event + "/timeline/apply-departure").then();
    }

    private static ValidatableResponse settings(String session, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .put("/api/teams/" + TEAM_1 + "/match-day-settings").then();
    }

    private static String createMatchDay(String session, String meetingPoint) {
        String mp = meetingPoint == null ? "" : ",\"meetingPoint\":{\"time\":null,\"location\":\"" + meetingPoint + "\"}";
        String body = """
                {"type":"MATCH_DAY","title":"Spieltag","start":"2026-12-06T10:00:00+01:00","end":"2026-12-06T14:00:00+01:00",
                 "teamIds":["%s"],"location":{"name":"Sporthalle Seefeld"}%s}""".formatted(TEAM_1, mp);
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .post("/api/events").then().statusCode(201).extract().path("id");
    }

    @Test
    void tml01TimelineOfMatchDay04() {
        JsonPath json = timeline(login("anna"), T04).statusCode(200).extract().jsonPath();

        assertThat(json.getList("items.kind"), contains("DEPARTURE", "ARRIVAL", "WARM_UP", "MATCH", "BREAK", "MATCH",
                "COOL_DOWN", "RETURN"));
        assertThat(json.getList("items.start"), contains(
                "2026-11-22T08:00:00+01:00", "2026-11-22T08:26:00+01:00", "2026-11-22T08:26:00+01:00",
                "2026-11-22T09:30:00+01:00", "2026-11-22T10:20:00+01:00", "2026-11-22T11:45:00+01:00",
                "2026-11-22T12:35:00+01:00", "2026-11-22T13:21:00+01:00"));
        // Time before the first match, match, time between the matches, match, cool down
        assertThat(json.getList("items.findAll { it.end != null }.minutes"), contains(64, 50, 85, 50, 20));
        assertEquals(MATCH_01, json.getString("items[3].matchId"));
        assertEquals("UHC Musterdorf", json.getString("items[3].opponent"));
        assertEquals(true, json.getBoolean("items[3].home"));
        assertEquals("2026-11-22T08:00:00+01:00", json.getString("departure"));
        assertEquals(false, json.getBoolean("departureSuggested"));
        assertEquals(26, json.getInt("driveMinutes"));
        assertEquals(23.4, json.getDouble("distanceKm"));
        assertEquals(50, json.getInt("matchDurationMinutes"));
        assertEquals(70, json.getInt("arrivalLeadMinutes"));
        assertEquals(20, json.getInt("coolDownMinutes"));
        // 09:30 minus 70 minutes: the team wants to be there at 08:20, but arrives at 08:26
        assertEquals("2026-11-22T08:20:00+01:00", json.getString("arriveBy"));
        assertEquals("2026-11-22T07:50:00+01:00", json.getString("suggestedDeparture"));
        assertThat(json.getList("hints"), contains("ARRIVES_LATE"));
        assertEquals(false, json.getBoolean("canApplySuggestion"), "players cannot change the meeting time");
    }

    @Test
    void tml04CoachTakesOverTheSuggestedDeparture() {
        String petra = login("petra");
        timeline(petra, T04).statusCode(200).body("canApplySuggestion", equalTo(true));
        apply(login("anna"), T04).statusCode(403);

        apply(petra, T04).statusCode(200)
                .body("departure", equalTo("2026-11-22T07:50:00+01:00")).body("departureSuggested", equalTo(false))
                .body("hints", empty()).body("canApplySuggestion", equalTo(false))
                .body("items[2].minutes", equalTo(74));
        // The meeting time of the match day is now the suggestion
        given().cookie("stv_session", petra).get("/api/events/" + T04).then()
                .body("meetingPoint.time", equalTo("2026-11-22T07:50:00+01:00"))
                .body("meetingPoint.location", equalTo("Parkplatz Sporthalle Nord"));
    }

    /** Switches the automatic departure of the variant «Spieltag» of Team 1 on or off (VAR-08). */
    private static void autoDeparture(String session, boolean on) {
        String variant = ch.stvbero.platform.attendance.variant.VariantRules.standardId(java.util.UUID.fromString(TEAM_1),
                ch.stvbero.platform.attendance.variant.VariantKind.MATCH_DAY).toString();
        given().cookie("stv_session", session).contentType(ContentType.JSON)
                .body("{\"name\":\"Spieltag\",\"color\":\"VIOLET\",\"autoDeparture\":" + on + "}")
                .put("/api/teams/" + TEAM_1 + "/event-variants/" + variant).then().statusCode(200)
                .body("autoDeparture", equalTo(on));
    }

    private static void addMatch(String session, String matchDay, String time) {
        given().cookie("stv_session", session).contentType(ContentType.JSON)
                .body("{\"opponent\":\"UHC Drei\",\"startTime\":\"2026-12-06T" + time + ":00+01:00\",\"home\":false}")
                .post("/api/events/" + matchDay + "/matches").then().statusCode(201);
    }

    @Test
    void tml04WithoutMeetingTimeTheTimelineShowsTheSuggestion() {
        String petra = login("petra");
        // Without the automatic departure the meeting time stays open until a coach sets it
        autoDeparture(petra, false);
        // Meeting point without a time, as on match days created by the sync (AUF-32)
        String matchDay = createMatchDay(petra, "Parkplatz Sporthalle Nord");
        addMatch(petra, matchDay, "10:00");
        given().cookie("stv_session", petra).get("/api/events/" + matchDay).then()
                .body("meetingPoint.time", nullValue()).body("meetingPoint.location", equalTo("Parkplatz Sporthalle Nord"));

        // 10:00 - 70 - 26 = 08:24, rounded down to 08:20
        timeline(petra, matchDay).statusCode(200)
                .body("departure", equalTo("2026-12-06T08:20:00+01:00")).body("departureSuggested", equalTo(true))
                .body("departureAuto", equalTo(false))
                .body("canApplySuggestion", equalTo(true)).body("hints", empty())
                .body("items.kind", contains("DEPARTURE", "ARRIVAL", "WARM_UP", "MATCH", "COOL_DOWN", "RETURN"));
    }

    @Test
    void tml09TheMeetingTimeFollowsTheSuggestedDepartureByItself() {
        String petra = login("petra");
        String matchDay = createMatchDay(petra, "Parkplatz Sporthalle Nord");
        // Without a first match there is nothing to set yet
        given().cookie("stv_session", petra).get("/api/events/" + matchDay).then()
                .body("meetingPoint.time", nullValue()).body("variant.autoDeparture", equalTo(true));

        addMatch(petra, matchDay, "10:00");
        // 10:00 - 70 - 26 = 08:24, rounded down to 08:20: set by itself, for everyone
        given().cookie("stv_session", login("anna")).get("/api/events/" + matchDay).then().statusCode(200)
                .body("meetingPoint.time", equalTo("2026-12-06T08:20:00+01:00"));
        timeline(petra, matchDay).statusCode(200)
                .body("departure", equalTo("2026-12-06T08:20:00+01:00")).body("departureSuggested", equalTo(false))
                .body("departureAuto", equalTo(true)).body("canApplySuggestion", equalTo(false));

        // An earlier match moves the meeting time along
        addMatch(petra, matchDay, "09:00");
        timeline(petra, matchDay).body("departure", equalTo("2026-12-06T07:20:00+01:00"))
                .body("departureAuto", equalTo(true));
        // Nobody is notified about an automatic time
        given().cookie("stv_session", login("anna")).get("/api/notifications").then()
                .body("findAll { it.eventId == '" + matchDay + "' }", empty());
    }

    @Test
    void tml09ATimeEnteredByHandIsNeverOverwritten() {
        String petra = login("petra");
        String matchDay = createMatchDay(petra, "Parkplatz Sporthalle Nord");
        addMatch(petra, matchDay, "10:00");
        String body = """
                {"type":"MATCH_DAY","title":"Spieltag","start":"2026-12-06T10:00:00+01:00","end":"2026-12-06T14:00:00+01:00",
                 "teamIds":["%s"],"location":{"name":"Sporthalle Seefeld"},
                 "meetingPoint":{"time":%s,"location":"Parkplatz Sporthalle Nord"}}""";

        given().cookie("stv_session", petra).get("/api/events/" + matchDay).then()
                .body("meetingPoint.time", equalTo("2026-12-06T08:20:00+01:00"));
        // Saving the form with the automatic time unchanged keeps it automatic
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(body.formatted(TEAM_1, "\"2026-12-06T08:20:00+01:00\""))
                .put("/api/events/" + matchDay).then().statusCode(200);
        timeline(petra, matchDay).body("departureAuto", equalTo(true));

        // A coach enters 08:00 by hand: it stays, also when a match is added
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(body.formatted(TEAM_1, "\"2026-12-06T08:00:00+01:00\""))
                .put("/api/events/" + matchDay).then().statusCode(200)
                .body("meetingPoint.time", equalTo("2026-12-06T08:00:00+01:00"));
        addMatch(petra, matchDay, "09:00");
        timeline(petra, matchDay).body("departure", equalTo("2026-12-06T08:00:00+01:00"))
                .body("departureAuto", equalTo(false)).body("suggestedDeparture", equalTo("2026-12-06T07:20:00+01:00"))
                .body("canApplySuggestion", equalTo(true));

        // Emptying the time hands it back to the automatic
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(body.formatted(TEAM_1, "null"))
                .put("/api/events/" + matchDay).then().statusCode(200)
                .body("meetingPoint.time", equalTo("2026-12-06T07:20:00+01:00"));
        timeline(petra, matchDay).body("departureAuto", equalTo(true));
    }

    @Test
    void tml09TakingOverTheSuggestionHandsTheTimeBackToTheAutomatic() {
        String petra = login("petra");
        // Match day 04 is «swiss unihockey Spieltag» with 08:00 entered by hand; the suggestion is 07:50
        timeline(petra, T04).body("departureAuto", equalTo(false)).body("canApplySuggestion", equalTo(true));
        apply(petra, T04).statusCode(200).body("departure", equalTo("2026-11-22T07:50:00+01:00"))
                .body("departureAuto", equalTo(true));
    }

    @Test
    void tml02SettingsOfTheTeamShapeTheTimeline() {
        String petra = login("petra");
        settings(petra, "{\"matchDurationMinutes\":60,\"arrivalLeadMinutes\":45,\"meetingPoint\":null}")
                .statusCode(200).body("matchDurationMinutes", equalTo(60)).body("arrivalLeadMinutes", equalTo(45))
                .body("meetingPoint", nullValue());

        timeline(petra, T04).statusCode(200)
                .body("matchDurationMinutes", equalTo(60)).body("arrivalLeadMinutes", equalTo(45))
                // 09:30 - 45 = 08:45; minus 26 minutes = 08:19, rounded down to 08:15
                .body("arriveBy", equalTo("2026-11-22T08:45:00+01:00"))
                .body("suggestedDeparture", equalTo("2026-11-22T08:15:00+01:00"))
                .body("hints", empty())
                .body("items.findAll { it.kind == 'MATCH' }.end",
                        contains("2026-11-22T10:30:00+01:00", "2026-11-22T12:45:00+01:00"))
                .body("items.find { it.kind == 'BREAK' }.minutes", equalTo(75));
        // Coaches and admins see the settings on the team, players do not
        given().cookie("stv_session", petra).get("/api/teams/" + TEAM_1).then()
                .body("matchDaySettings.matchDurationMinutes", equalTo(60));
        given().cookie("stv_session", login("anna")).get("/api/teams/" + TEAM_1).then()
                .body("matchDaySettings", nullValue());
    }

    @Test
    void tml02SettingsAreForCoachesAndAdminsAndHaveLimits() {
        settings(login("anna"), "{\"matchDurationMinutes\":60,\"arrivalLeadMinutes\":45}").statusCode(403);
        // Marc coaches another team: coaches manage all teams (E-69)
        settings(login("marc"), "{\"matchDurationMinutes\":60,\"arrivalLeadMinutes\":45}").statusCode(200);
        settings(login("alex"), "{\"matchDurationMinutes\":5,\"arrivalLeadMinutes\":45}").statusCode(400)
                .body("fields[0].field", equalTo("matchDurationMinutes"));
        settings(login("alex"), "{\"matchDurationMinutes\":50,\"arrivalLeadMinutes\":241}").statusCode(400)
                .body("fields[0].field", equalTo("arrivalLeadMinutes"));
        settings(login("alex"), "{\"arrivalLeadMinutes\":45}").statusCode(400);
        // Defaults of a team: 50 minutes per match, 70 minutes before the first match
        given().cookie("stv_session", login("alex")).get("/api/teams/10000000-0000-4000-8000-000000000003").then()
                .body("matchDaySettings.matchDurationMinutes", equalTo(50))
                .body("matchDaySettings.arrivalLeadMinutes", equalTo(70))
                .body("matchDaySettings.meetingPoint", nullValue());
    }

    @Test
    void tml07WithoutMeetingPointOrRouteTheTimelineSaysWhatIsMissing() {
        String petra = login("petra");
        String matchDay = createMatchDay(petra, null);
        timeline(petra, matchDay).statusCode(200)
                .body("hints", contains("NO_MATCHES", "MEETING_POINT_MISSING")).body("items", empty())
                .body("canApplySuggestion", equalTo(false));
        apply(petra, matchDay).statusCode(409).body("rule", equalTo("TML-04"));

        // The routing service is down: the matches are still laid out, without departure and return
        given().contentType(ContentType.JSON).body("{\"unavailable\":true}").put("/api/test/routing").then()
                .statusCode(204);
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("""
                        {"type":"MATCH_DAY","title":"Spieltag","start":"2026-11-22T09:30:00+01:00",
                         "end":"2026-11-22T13:00:00+01:00","teamIds":["%s"],
                         "location":{"name":"Sporthalle Seefeld","address":"Musterdorf"},
                         "meetingPoint":{"time":null,"location":"Bahnhof"}}""".formatted(TEAM_1))
                .put("/api/events/" + T04).then().statusCode(200);
        timeline(petra, T04).statusCode(200)
                .body("hints", contains("DRIVE_TIME_UNKNOWN"))
                .body("items.kind", contains("MATCH", "BREAK", "MATCH", "COOL_DOWN"))
                .body("departure", nullValue()).body("suggestedDeparture", nullValue());
    }

    @Test
    void tml01OnlyMatchDaysHaveATimelineAndOnlyTheTeamSeesIt() {
        timeline(login("petra"), T01).statusCode(409).body("rule", equalTo("DRV-01"));
        // Tina plays only in Team 3
        timeline(login("tina"), T04).statusCode(403);
        timeline(login("marc"), T05).statusCode(200)
                // One match at 14:00, meeting time 12:30, 16 minutes to «Turnhalle Weid»
                .body("items.kind", contains("DEPARTURE", "ARRIVAL", "WARM_UP", "MATCH", "COOL_DOWN", "RETURN"))
                .body("items[5].start", equalTo("2026-11-28T15:26:00+01:00"));
        given().get("/api/events/" + T04 + "/timeline").then().statusCode(401);
        timeline(login("alex"), "40000000-0000-4000-8000-000000000999").statusCode(404);
    }
}
