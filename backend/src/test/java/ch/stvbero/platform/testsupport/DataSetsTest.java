package ch.stvbero.platform.testsupport;

import ch.stvbero.platform.core.mode.Simulation;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
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
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two data sets of the profiles dev and test (docs/test-data.md): the test data set that all automated tests
 * use, and the club data set. The club data set is filled in here without its loader, which would ask the real
 * swiss unihockey: the tests never leave the machine.
 */
@QuarkusTest
class DataSetsTest {

    @Inject
    ClubSeed club;

    @Inject
    Simulation simulation;

    @Inject
    SeedPins pins;

    @Inject
    EntityManager em;

    @AfterEach
    void cleanUp() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    @Test
    void resetLoadsTheTestDataSetWithSimulators() {
        given().post("/api/test/reset").then().statusCode(204);

        given().get("/api/test/dataset").then().statusCode(200)
                .body("dataset", equalTo("TEST")).body("simulated", equalTo(true)).body("loading", equalTo(false))
                .body("routing", equalTo("SIMULATOR"));
        assertTrue(simulation.on());
        given().queryParam("dataset", "NOPE").post("/api/test/reset").then().statusCode(404);
    }

    @Test
    void personsOfTheTestModeLoginComeWithTheirTeams() {
        JsonPath json = given().get("/api/test/persons").then().statusCode(200).extract().jsonPath();

        // Petra coaches Team 1 and plays in Team 2; the admin has no team; Vera is only in the archive
        assertThat(json.getList("find { it.loginKey == 'petra' }.teams.teamName"), contains("Team 1", "Team 2"));
        assertThat(json.getList("find { it.loginKey == 'petra' }.teams.teamRole"), contains("COACH", "PLAYER"));
        assertThat(json.getList("find { it.loginKey == 'alex' }.teams"), empty());
        assertThat(json.getList("find { it.loginKey == 'vera' }.teams"), empty());
        assertThat(json.getList("find { it.loginKey == 'lea' }.teams.teamName"), contains("Team 1", "Team 2"));
    }

    @Test
    void clubDataSetHoldsTheTenTeamsWithInventedPeopleAndThreeLinks() {
        club.reset();
        pins.club();

        given().get("/api/test/clock").then().body("fixed", equalTo(false));
        String alex = login("alex");
        JsonPath teams = given().cookie("stv_session", alex).get("/api/teams").then().statusCode(200)
                .extract().jsonPath();
        assertThat(teams.getList("name"), containsInAnyOrder("Herren KF 3. Liga", "Herren KF 4. Liga II",
                "Damen KF 1. Liga", "Damen KF 3. Liga II", "Junioren A", "Junioren B", "Junioren C", "Junioren D",
                "Junioren E", "Juniorinnen B"));
        // Three teams are linked, the others are left open to try out the link dialog
        Map<String, Object> linked = teams.getMap("findAll { it.suTeamId != null }.collectEntries { [it.name, it.suTeamId] }");
        assertEquals(Map.of("Herren KF 3. Liga", 428216, "Damen KF 1. Liga", 428361, "Junioren A", 428055), linked);

        // Coaches of the test mode login: Reto coaches both men's teams, Petra also plays
        JsonPath persons = given().get("/api/test/persons").then().statusCode(200).extract().jsonPath();
        assertThat(persons.getList("find { it.loginKey == 'reto' }.teams.teamName"),
                contains("Herren KF 3. Liga", "Herren KF 4. Liga II"));
        assertThat(persons.getList("find { it.loginKey == 'petra' }.teams.teamRole"), contains("COACH", "PLAYER"));
        List<String> keys = persons.getList("loginKey");
        assertEquals(keys.size(), keys.stream().distinct().count(), "every person has her own key");
        assertEquals(106, keys.size(), "admin, 9 coaches, 4 teams of 12 and 6 teams of 8 players");

        // Every team trains, and the team with the hand-entered match days works without swiss unihockey
        String reto = login("reto");
        given().cookie("stv_session", reto).get("/api/teams/10000000-0000-4000-8000-000000000002/driver-plan").then()
                .statusCode(200).body("matchDays", hasSize(8))
                .body("matchDays.findAll { it.over }", hasSize(4))
                .body("matchDays.findAll { !it.ready }", empty())
                .body("matchDays[4].cars", hasSize(2))
                .body("teamName", equalTo("Herren KF 4. Liga II"));
        assertEquals(0L, ((Number) em.createNativeQuery("""
                select count(*) from team_season ts join season s on s.id = ts.season_id
                where s.is_current and not exists (
                    select 1 from event_team et join event e on e.id = et.event_id
                    where et.team_id = ts.team_id and e.type = 'PRACTICE')""").getSingleResult()).longValue());
        // The usual meeting point of every team is the home hall (AUF-32)
        given().cookie("stv_session", reto).get("/api/teams/10000000-0000-4000-8000-000000000001").then()
                .body("matchDaySettings.meetingPoint", equalTo(ClubSeed.HOME))
                .body("matchDaySettings.matchDurationMinutes", equalTo(50))
                .body("matchDaySettings.arrivalLeadMinutes", equalTo(70));
        // A pinned «Teamessen» of «Damen KF 1. Liga» shows on the start page of its members (TER-26, E-114)
        JsonPath events = given().cookie("stv_session", login("petra")).get("/api/events").then().statusCode(200)
                .extract().jsonPath();
        assertThat(events.getList("findAll { it.pinned }.title"), contains("Teamessen"));
        assertThat(events.getList("findAll { it.pinned }.teams[0].name"), contains("Damen KF 1. Liga"));
        given().cookie("stv_session", reto).get("/api/events/" + SeedPins.CLUB_TEAM_DINNER).then().statusCode(200)
                .body("pinned", equalTo(true)).body("canEdit", equalTo(true));
    }

    @Test
    void loginKeysAreFirstNamesWithoutAccents() {
        assertEquals("zoe", ClubSeed.key("Zoë"));
        assertEquals("joel", ClubSeed.key("Joël"));
        assertEquals("luca", ClubSeed.key("Luca"));
    }
}
