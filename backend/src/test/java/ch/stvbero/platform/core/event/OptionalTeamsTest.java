package ch.stvbero.platform.core.event;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Teams that are expected and teams that are invited without obligation («freiwillig», TER-15 to TER-17), against
 * the seed of docs/test-data.md: Team 1 has seven players (Anna, Lea, Mia, Sara, Nina, Jana, Kim) and the coach
 * Petra; Team 2 has Petra, Lea, Eva, Lara, Zoe and Ella as players and the coach Marc. Lea and Petra are in both.
 * Default "now": Mon 16.11.2026 06:00.
 */
@QuarkusTest
class OptionalTeamsTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String TEAM_3 = "10000000-0000-4000-8000-000000000003";

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static String body(String optional) {
        return """
                {"type":"PRACTICE","title":"Training","start":"2026-11-19T19:00:00+01:00","end":"2026-11-19T20:30:00+01:00",
                 "teamIds":["%s","%s"],"responseDeadlineHours":24%s}""".formatted(TEAM_1, TEAM_2, optional);
    }

    /** A practice of Team 1 (expected) and Team 2 (invited) on Thu 19.11.2026 19:00. */
    private static String practice() {
        return given().cookie("stv_session", login("petra")).contentType(ContentType.JSON)
                .body(body(",\"optionalTeamIds\":[\"" + TEAM_2 + "\"]")).post("/api/events").then().statusCode(201)
                .body("optionalTeamIds", contains(TEAM_2)).extract().path("id");
    }

    private static ValidatableResponse event(String session, String id) {
        return given().cookie("stv_session", session).get("/api/events/" + id).then();
    }

    private static void respond(String session, String event, String status) {
        given().cookie("stv_session", session).contentType(ContentType.JSON).body("{\"status\":\"" + status + "\"}")
                .put("/api/events/" + event + "/responses/me").then().statusCode(200);
    }

    @Test
    void ter15WhoIsExpectedAndWhoIsInvited() {
        String practice = practice();
        // Anna plays in Team 1: expected. Eva plays in Team 2 only: invited.
        event(login("anna"), practice).statusCode(200).body("myParticipation", equalTo("EXPECTED"));
        event(login("eva"), practice).statusCode(200).body("myParticipation", equalTo("OPTIONAL"))
                .body("optionalTeamIds", contains(TEAM_2));
        // Lea plays in both teams, Petra coaches Team 1 and plays in Team 2: expected
        event(login("lea"), practice).body("myParticipation", equalTo("EXPECTED"));
        event(login("petra"), practice).body("myParticipation", equalTo("EXPECTED"));
        // Marc coaches the invited team only; Alex is in no team
        event(login("marc"), practice).body("myParticipation", equalTo("OPTIONAL"));
        event(login("alex"), practice).body("myParticipation", nullValue());
        // The list carries it as well (TER-17)
        given().cookie("stv_session", login("eva")).get("/api/events").then().statusCode(200)
                .body("find { it.id == '" + practice + "' }.myParticipation", equalTo("OPTIONAL"))
                .body("find { it.id == '40000000-0000-4000-8000-000000000001' }.myParticipation", equalTo("EXPECTED"));
    }

    @Test
    void ter16InvitedPlayersCountOnlyOnceTheyRespond() {
        String practice = practice();
        // Seven players of Team 1 are pending; of Team 2 only Lea and Petra count, because they are also in Team 1
        // or… Petra coaches Team 1, so only Lea: the players of Team 2 alone are not counted.
        event(login("petra"), practice).body("counts.pending", equalTo(7)).body("counts.attending", equalTo(0));

        respond(login("eva"), practice, "ATTENDING");
        respond(login("zoe"), practice, "DECLINED");
        respond(login("anna"), practice, "ATTENDING");
        event(login("petra"), practice).body("counts.attending", equalTo(2)).body("counts.declined", equalTo(1))
                .body("counts.pending", equalTo(6));

        ValidatableResponse overview = given().cookie("stv_session", login("petra"))
                .get("/api/events/" + practice + "/overview").then().statusCode(200);
        overview.body("teams.team.name", contains("Team 1", "Team 2")).body("teams.optional", contains(false, true))
                .body("teams[0].counts.pending", equalTo(6)).body("teams[0].counts.attending", equalTo(1))
                // Team 2: Eva and Zoe responded; Petra, Lea, Lara and Ella are not counted as pending
                .body("teams[1].counts.attending", equalTo(1)).body("teams[1].counts.declined", equalTo(1))
                .body("teams[1].counts.pending", equalTo(0))
                .body("total.attending", equalTo(2)).body("total.pending", equalTo(6))
                // Everyone of the invited team is still listed
                .body("teams[1].groups.entries.flatten()", hasSize(6));
    }

    @Test
    void ter16AnInvitedTeamHasNoWarnings() {
        String practice = practice();
        // Team 1 has too few players «dabei» (minimum 6) and no goalie; Team 2 would have the same, but is invited
        given().cookie("stv_session", login("petra")).get("/api/events/" + practice + "/overview").then()
                .body("warnings.findAll { it.team.name == 'Team 2' }", empty())
                .body("warnings.findAll { it.team.name == 'Team 1' }.size()", equalTo(2));
        event(login("petra"), practice).body("warningCount", equalTo(2));
    }

    @Test
    void ter16InvitedPeopleGetNoReminder() {
        String practice = practice();
        // The response deadline is Wed 18.11. 19:00; the reminder goes out 12 hours before
        given().contentType(ContentType.JSON).body("{\"now\":\"2026-11-18T08:00:00+01:00\"}").put("/api/test/clock")
                .then().statusCode(204);

        // Anna (Team 1) is reminded, Eva (Team 2 only) is not; Lea (both teams) is
        given().cookie("stv_session", login("anna")).get("/api/notifications").then()
                .body("findAll { it.type == 'REMINDER' && it.eventId == '" + practice + "' }", hasSize(1));
        given().cookie("stv_session", login("lea")).get("/api/notifications").then()
                .body("findAll { it.type == 'REMINDER' && it.eventId == '" + practice + "' }", hasSize(1));
        given().cookie("stv_session", login("eva")).get("/api/notifications").then()
                .body("findAll { it.type == 'REMINDER' && it.eventId == '" + practice + "' }", empty());
        given().cookie("stv_session", login("marc")).get("/api/notifications").then()
                .body("findAll { it.type == 'REMINDER' && it.eventId == '" + practice + "' }", empty());
    }

    @Test
    void ter15AtLeastOneTeamIsExpected() {
        String petra = login("petra");
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(body(",\"optionalTeamIds\":[\"" + TEAM_1 + "\",\"" + TEAM_2 + "\"]")).post("/api/events").then()
                .statusCode(400).body("fields[0].field", equalTo("optionalTeamIds"));
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(body(",\"optionalTeamIds\":[\"" + TEAM_3 + "\"]")).post("/api/events").then().statusCode(400);
        // Without the field every team is expected; saving again without it makes everyone expected again
        String practice = practice();
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(body(""))
                .put("/api/events/" + practice).then().statusCode(200).body("optionalTeamIds", empty());
        event(login("eva"), practice).body("myParticipation", equalTo("EXPECTED"));
    }

    @Test
    void ter15ASeriesHandsItOnToItsEvents() {
        String petra = login("petra");
        String series = given().cookie("stv_session", petra).contentType(ContentType.JSON).body("""
                        {"type":"PRACTICE","title":"Donnerstagstraining","teamIds":["%s","%s"],"weekday":"THURSDAY",
                         "startTime":"19:00","endTime":"20:30","validFrom":"2026-11-19","validUntil":"2026-12-03",
                         "optionalTeamIds":["%s"]}""".formatted(TEAM_1, TEAM_2, TEAM_2))
                .post("/api/series").then().statusCode(201).body("optionalTeamIds", contains(TEAM_2))
                .extract().path("id");

        given().cookie("stv_session", login("eva")).get("/api/events").then().statusCode(200)
                .body("findAll { it.seriesId == '" + series + "' }.myParticipation",
                        contains("OPTIONAL", "OPTIONAL", "OPTIONAL"))
                .body("findAll { it.seriesId == '" + series + "' }.optionalTeamIds.flatten().unique()", contains(TEAM_2));
        given().cookie("stv_session", petra).get("/api/series/" + series).then().statusCode(200)
                .body("optionalTeamIds", contains(TEAM_2));
    }

    @Test
    void participationOfAPerson() {
        UUID a = new UUID(0, 1);
        UUID b = new UUID(0, 2);
        assertEquals(null, Participation.of(List.of(), Set.of(a)));
        assertEquals(Participation.EXPECTED, Participation.of(List.of(a), Set.of()));
        assertEquals(Participation.OPTIONAL, Participation.of(List.of(a), Set.of(a)));
        assertEquals(Participation.EXPECTED, Participation.of(List.of(a, b), Set.of(a)));
        assertEquals(Participation.OPTIONAL, Participation.of(List.of(a, b), Set.of(a, b)));

        assertEquals(List.of(), Participation.check(List.of(a, b), null));
        assertEquals(List.of(b), Participation.check(List.of(a, b), List.of(b, b)));
        assertThrows(RuntimeException.class, () -> Participation.check(List.of(a, b), List.of(a, b)));
        assertThrows(RuntimeException.class, () -> Participation.check(List.of(a), List.of(b)));
    }
}
