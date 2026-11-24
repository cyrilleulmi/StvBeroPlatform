package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.testsupport.Seed;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

/**
 * Calendar view (KAL-01 to KAL-04) against the seed of docs/test-data.md. Lea is a player in Team 1 and Team 2,
 * Petra coach of Team 1 and player in Team 2, Alex admin without a team.
 */
@QuarkusTest
class CalendarViewTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String TEAM_3 = "10000000-0000-4000-8000-000000000003";
    /** Match day Sun 22.11.2026, Team 1. */
    static final String EVENT_04 = "40000000-0000-4000-8000-000000000004";
    /** Match day Sat 28.11.2026, Team 2. */
    static final String EVENT_05 = "40000000-0000-4000-8000-000000000005";
    /** Past practice Tue 10.11.2026, Team 1 + 2. */
    static final String EVENT_07 = "40000000-0000-4000-8000-000000000007";
    /** November grid: Mon 26.10. to Sun 06.12.2026. */
    static final String NOVEMBER = "from=2026-10-26&to=2026-12-06";

    static String seriesEvent(int series, String date) {
        return UUID.nameUUIDFromBytes(("termin-" + series + "-" + date).getBytes(StandardCharsets.UTF_8)).toString();
    }

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static ValidatableResponse view(String session, String query) {
        return given().cookie("stv_session", session).get("/api/events/calendar?" + query).then();
    }

    @Test
    void kal03PlayerInTwoTeamsSeesBothTogether() {
        view(login("lea"), NOVEMBER).statusCode(200)
                .body("from", equalTo("2026-10-26")).body("to", equalTo("2026-12-06"))
                .body("teams.name", contains("Team 1", "Team 2"))
                .body("teams.myTeamRole", contains("PLAYER", "PLAYER"))
                .body("defaultTeamIds", contains(TEAM_1, TEAM_2))
                .body("teamIds", contains(TEAM_1, TEAM_2))
                .body("allTeams", equalTo(false)).body("canShowAllTeams", equalTo(false))
                // past and upcoming together, in order of time
                .body("events.id", hasItems(EVENT_07, EVENT_04, EVENT_05))
                .body("events.id", not(hasItem(seriesEvent(3, "2026-11-18"))))
                .body("events[0].start", equalTo("2026-10-27T18:30:00+01:00"));
    }

    @Test
    void kal02PlayerChoosesOneOfHerTeams() {
        view(login("lea"), NOVEMBER + "&team=" + TEAM_2).statusCode(200)
                .body("teamIds", contains(TEAM_2))
                .body("events.id", hasItem(EVENT_05))
                .body("events.id", not(hasItem(EVENT_04)));
    }

    @Test
    void kal04PlayerMayNotShowOtherTeams() {
        String lea = login("lea");
        view(lea, NOVEMBER + "&team=" + TEAM_3).statusCode(403).body("rule", equalTo("RR-07"));
        view(lea, NOVEMBER + "&all=true").statusCode(403);
    }

    @Test
    void kal04CoachStartsWithHerTeamAndMayShowAll() {
        String petra = login("petra");
        view(petra, NOVEMBER).statusCode(200)
                .body("teams.name", contains("Team 1", "Team 2", "Team 3"))
                .body("teams.myTeamRole", contains("COACH", "PLAYER", null))
                .body("defaultTeamIds", contains(TEAM_1))
                .body("teamIds", contains(TEAM_1))
                .body("canShowAllTeams", equalTo(true))
                .body("events.id", hasItem(EVENT_04))
                .body("events.id", not(hasItem(EVENT_05)));
        view(petra, NOVEMBER + "&all=true").statusCode(200)
                .body("allTeams", equalTo(true))
                .body("teamIds", hasSize(3))
                .body("events.id", hasItems(EVENT_04, EVENT_05, seriesEvent(3, "2026-11-18")));
        view(petra, NOVEMBER + "&team=" + TEAM_3).statusCode(200)
                .body("events.id", hasItem(seriesEvent(3, "2026-11-18")))
                .body("events.id", not(hasItem(EVENT_04)));
    }

    @Test
    void kal04AdminWithoutTeamSeesAllTeams() {
        view(login("alex"), NOVEMBER).statusCode(200)
                .body("defaultTeamIds", hasSize(3))
                .body("events.id", hasItems(EVENT_04, EVENT_05));
    }

    @Test
    void kal01EventsOfThePeriodOnly() {
        view(login("anna"), "from=2026-11-22&to=2026-11-22").statusCode(200)
                .body("events.id", contains(EVENT_04))
                .body("events[0].myResponse.status", equalTo("ATTENDING"));
        view(login("anna"), "from=2026-11-01&to=2026-12-31").statusCode(400);
        view(login("anna"), "to=2026-11-30").statusCode(400);
        given().get("/api/events/calendar?" + NOVEMBER).then().statusCode(401);
    }

    @Test
    void abs07OwnAbsencesInThePeriod() {
        view(login("sara"), "from=2026-11-30&to=2027-01-10").statusCode(200)
                .body("absences", hasSize(1))
                .body("absences[0].id", equalTo(Seed.SARA_ABSENCE_ID))
                .body("absences[0].declinedPractices", greaterThanOrEqualTo(2));
        view(login("sara"), NOVEMBER).statusCode(200).body("absences", empty());
        // Nobody sees the absences of others
        view(login("petra"), "from=2026-11-30&to=2027-01-10").statusCode(200).body("absences", empty());
    }
}
