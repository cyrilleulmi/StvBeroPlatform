package ch.stvbero.platform.core.statistics;

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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/** F-15: attendance statistics (STA-01 to STA-11). Expected values from docs/test-data.md, scenario S44–S46. */
@QuarkusTest
class StatisticsTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String LEA = "20000000-0000-4000-8000-000000000006";
    static final String MIA = "20000000-0000-4000-8000-000000000007";
    static final String KIM = "20000000-0000-4000-8000-000000000011";
    static final String TINA = "20000000-0000-4000-8000-000000000016";
    static final String ALEX = "20000000-0000-4000-8000-000000000001";
    static final String T03 = "40000000-0000-4000-8000-000000000003";
    static final String T07 = "40000000-0000-4000-8000-000000000007";
    static final String T09 = "40000000-0000-4000-8000-000000000009";
    static final String T10 = "40000000-0000-4000-8000-000000000010";

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static ValidatableResponse team(String session, String query) {
        return given().cookie("stv_session", session).get("/api/statistics" + query).then();
    }

    private static ValidatableResponse person(String session, String person, String query) {
        return given().cookie("stv_session", session).get("/api/statistics/persons/" + person + query).then();
    }

    private static void clock(String now) {
        given().contentType(ContentType.JSON).body("{\"now\":\"" + now + "\"}").put("/api/test/clock").then().statusCode(204);
    }

    /** Row of a player in the team statistics, by first name. */
    private static Map<String, Object> row(JsonPath json, String firstName) {
        List<Map<String, Object>> players = json.getList("groups.players.flatten()");
        return players.stream().filter(p -> firstName.equals(((Map<?, ?>) p.get("person")).get("firstName")))
                .findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ STA-03, STA-04, STA-09: numbers of the seed

    @Test
    void sta09AnnaMatchesTheSeed() {
        JsonPath json = team(login("petra"), "").statusCode(200).extract().jsonPath();
        // Default: first team Petra coaches, the whole current season, all types
        assertThat(json.getString("team.id"), equalTo(TEAM_1));
        assertThat(json.getString("from"), equalTo("2026-07-01"));
        assertThat(json.getString("to"), equalTo("2027-06-30"));
        assertThat(json.getString("season.name"), equalTo("2026/27"));
        assertThat(json.getInt("events"), equalTo(26));
        assertThat(json.getInt("attendanceEvents"), equalTo(1));

        Map<String, Object> anna = row(json, "Anna");
        assertThat(anna.get("events"), equalTo(26));
        assertThat(anna.get("attending"), equalTo(2));
        assertThat(anna.get("declined"), equalTo(1));
        assertThat(anna.get("maybe"), equalTo(0));
        assertThat(anna.get("pending"), equalTo(23));
        assertThat(anna.get("attendingRate"), equalTo(8));
        assertThat(anna.get("lateCancellations"), equalTo(1));
        assertThat(anna.get("attendanceEvents"), equalTo(1));
        assertThat(anna.get("present"), equalTo(1));
        assertThat(anna.get("absent"), equalTo(0));
        assertThat(anna.get("excused"), equalTo(0));
        assertThat(anna.get("attendanceRate"), equalTo(100));

        Map<String, Object> mia = row(json, "Mia");
        assertThat(mia.get("attending"), equalTo(1));
        assertThat(mia.get("declined"), equalTo(2));
        assertThat(mia.get("attendingRate"), equalTo(4));
        assertThat(mia.get("excused"), equalTo(1));
        assertThat(mia.get("attendanceRate"), equalTo(0));
    }

    @Test
    void sta05GroupedByLineWithTeamAverage() {
        JsonPath json = team(login("petra"), "?team=" + TEAM_1).statusCode(200).extract().jsonPath();
        assertThat(json.getList("groups.name"), contains("Linie 1", "Linie 2", "Goalies", "Ohne Linie"));
        assertThat(json.getList("groups[0].players.person.firstName"), contains("Mia", "Lea", "Anna"));
        assertThat(json.getList("groups[1].players.person.firstName"), contains("Sara", "Nina"));
        assertThat(json.getList("groups[3].players.person.firstName"), contains("Kim"));
        // Coaches are not part of it
        assertThat(json.getList("groups.players.flatten().person.id"), not(hasItem(PETRA)));
        assertThat(json.getFloat("average.events"), equalTo(24.1f));
        assertThat(json.getInt("average.attendingRate"), equalTo(6));
        assertThat(json.getInt("average.attendanceRate"), equalTo(57));
        assertThat(json.getFloat("average.lateCancellations"), equalTo(0.1f));
    }

    // ------------------------------------------------------------------ STA-07: what counts

    @Test
    void sta07OnlyFromTheDaySheJoined() {
        Map<String, Object> kim = row(team(login("petra"), "").statusCode(200).extract().jsonPath(), "Kim");
        assertThat(kim.get("joinedOn"), equalTo("2026-10-01"));
        assertThat(kim.get("events"), equalTo(13));
        assertThat(kim.get("pending"), equalTo(13));
        assertThat(kim.get("attendingRate"), equalTo(0));
        assertThat(kim.get("absent"), equalTo(1));
        assertThat(kim.get("attendanceRate"), equalTo(0));
    }

    @Test
    void sta07NewMemberCountsFromToday() {
        String alex = login("alex");
        given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"personId\":\"" + TINA + "\",\"teamRole\":\"PLAYER\"}")
                .post("/api/teams/" + TEAM_1 + "/members").then().statusCode(201);
        Map<String, Object> tina = row(team(alex, "?team=" + TEAM_1).statusCode(200).extract().jsonPath(), "Tina");
        assertThat(tina.get("joinedOn"), equalTo("2026-11-16"));
        assertThat(tina.get("events"), equalTo(0));
        assertThat(tina.get("attendingRate"), nullValue());
        assertThat(tina.get("attendanceRate"), nullValue());
    }

    @Test
    void sta07OnlyPastAndNotCancelled() {
        // 25.11.: practices 17.11. (01), 19.11. (02) and match day 22.11. (04) are past; 24.11. (03) is cancelled
        clock("2026-11-25T07:00:00+01:00");
        JsonPath json = team(login("petra"), "").statusCode(200).extract().jsonPath();
        assertThat(json.getInt("events"), equalTo(29));
        List<String> events = person(login("petra"), ANNA, "").statusCode(200).extract().jsonPath().getList("events.id");
        assertThat(events, not(hasItem(T03)));
        assertThat(events.size(), equalTo(29));
    }

    // ------------------------------------------------------------------ STA-02: filters

    @Test
    void sta02PeriodAndType() {
        Map<String, Object> anna = row(team(login("petra"), "?team=" + TEAM_1 + "&from=2026-11-01&to=2026-11-15")
                .statusCode(200).extract().jsonPath(), "Anna");
        assertThat(anna.get("events"), equalTo(4));
        assertThat(anna.get("attending"), equalTo(2));
        assertThat(anna.get("declined"), equalTo(1));
        assertThat(anna.get("pending"), equalTo(1));
        assertThat(anna.get("attendingRate"), equalTo(50));
        assertThat(anna.get("lateCancellations"), equalTo(1));

        JsonPath matchDays = team(login("petra"), "?type=MATCH_DAY").statusCode(200).extract().jsonPath();
        assertThat(matchDays.getString("type"), equalTo("MATCH_DAY"));
        assertThat(matchDays.getInt("events"), equalTo(0));
        assertThat(row(matchDays, "Anna").get("attendingRate"), nullValue());
        assertThat(team(login("petra"), "?type=PRACTICE").extract().jsonPath().getInt("events"), equalTo(26));
    }

    @Test
    void sta02AllTeamsForCoachesAndAdmins() {
        JsonPath json = team(login("marc"), "?team=" + TEAM_1).statusCode(200).extract().jsonPath();
        assertThat(json.getList("teams.name"), contains("Team 1", "Team 2", "Team 3"));
        assertThat(json.getString("team.name"), equalTo("Team 1"));
        // Marc coaches Team 2: that is his default
        assertThat(team(login("marc"), "").extract().jsonPath().getString("team.id"), equalTo(TEAM_2));
        // Alex coaches nothing: first team by name
        assertThat(team(login("alex"), "").statusCode(200).extract().jsonPath().getString("team.id"), equalTo(TEAM_1));
    }

    @Test
    void sta02ArchiveSeasonFromThePeriod() {
        JsonPath json = team(login("alex"), "?team=" + TEAM_1 + "&from=2025-07-01").statusCode(200).extract().jsonPath();
        assertThat(json.getString("season.name"), equalTo("2025/26"));
        assertThat(json.getString("to"), equalTo("2026-06-30"));
        assertThat(json.getInt("events"), equalTo(1));
        assertThat(row(json, "Vera").get("pending"), equalTo(1));
    }

    @Test
    void sta02InvalidPeriod() {
        String petra = login("petra");
        team(petra, "?from=2026-12-01&to=2026-11-01").statusCode(400)
                .body("fields[0].field", equalTo("to"), "fields[0].message", equalTo(StatisticsRules.END_BEFORE_START));
        team(petra, "?from=2026-11-01&to=2027-07-05").statusCode(400).body("fields[0].field", equalTo("to"));
        team(petra, "?from=2020-01-01").statusCode(400).body("fields[0].message", equalTo(StatisticsRules.NO_SEASON));
        team(petra, "?team=" + ANNA).statusCode(404);
    }

    // ------------------------------------------------------------------ STA-06: events of a player

    @Test
    void sta06EventsOfAPlayer() {
        JsonPath json = person(login("petra"), ANNA, "?team=" + TEAM_1).statusCode(200).extract().jsonPath();
        assertThat(json.getInt("statistics.attendingRate"), equalTo(8));
        assertThat(json.getList("events").size(), equalTo(26));
        // Newest first: Thu 12.11. (09), Tue 10.11. (07), Thu 05.11. (10)
        assertThat(json.getList("events.id").subList(0, 3), contains(T09, T07, T10));
        assertThat(json.getString("events[0].response"), equalTo("ATTENDING"));
        assertThat(json.getString("events[0].attendance"), nullValue());
        assertThat(json.getString("events[1].response"), equalTo("DECLINED"));
        assertThat(json.getBoolean("events[1].lateCancellation"), equalTo(true));
        assertThat(json.getString("events[2].attendance"), equalTo("PRESENT"));
        assertThat(json.getString("events[3].response"), equalTo("PENDING"));
        // A team she does not play in
        person(login("petra"), ANNA, "?team=" + TEAM_2).statusCode(404);
    }

    // ------------------------------------------------------------------ STA-08: own numbers only

    @Test
    void sta08PlayersSeeOnlyTheirOwnNumbers() {
        String anna = login("anna");
        team(anna, "").statusCode(403);
        team(anna, "?team=" + TEAM_1).statusCode(403);
        person(anna, MIA, "").statusCode(403);
        JsonPath own = person(anna, ANNA, "").statusCode(200).extract().jsonPath();
        assertThat(own.getString("team.id"), equalTo(TEAM_1));
        assertThat(own.getInt("statistics.events"), equalTo(26));
        assertThat(own.getInt("statistics.attendingRate"), equalTo(8));
        assertThat(own.getInt("statistics.lateCancellations"), equalTo(1));
        assertThat(own.getInt("statistics.attendanceRate"), equalTo(100));
    }

    @Test
    void sta08PlayerInTwoTeamsAndCoaches() {
        JsonPath lea = person(login("lea"), LEA, "?team=" + TEAM_2).statusCode(200).extract().jsonPath();
        assertThat(lea.getList("teams.name"), contains("Team 1", "Team 2"));
        assertThat(lea.getInt("statistics.events"), equalTo(13));
        assertThat(lea.getInt("statistics.attending"), equalTo(1));
        // Petra is coach in Team 1 and player in Team 2: only Team 2
        JsonPath petra = person(login("petra"), PETRA, "").statusCode(200).extract().jsonPath();
        assertThat(petra.getList("teams.id"), contains(TEAM_2));
        // Alex plays nowhere
        JsonPath alex = person(login("alex"), ALEX, "").statusCode(200).extract().jsonPath();
        assertThat(alex.getList("teams"), empty());
        assertThat(alex.getString("team"), nullValue());
        assertThat(alex.getString("statistics"), nullValue());
        // Kim's own numbers match the team statistics
        assertThat(person(login("kim"), KIM, "").extract().jsonPath().getInt("statistics.events"), equalTo(13));
    }

    @Test
    void sta01NotLoggedIn() {
        given().get("/api/statistics").then().statusCode(401);
    }
}
