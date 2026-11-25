package ch.stvbero.platform.attendance.lineup;

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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Special line-ups such as power play and penalty kill (LIN-13 to LIN-17), against the seed of docs/test-data.md:
 * Team 1 with the players Anna, Lea, Mia, Sara, Nina, Jana and Kim and the coach Petra. On match day 04 Anna, Lea,
 * Sara and Jana responded «dabei», Mia «vielleicht», Kim «nicht dabei», Nina not yet; the call-up is in draft.
 */
@QuarkusTest
class SpecialLineupsTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String T01 = "40000000-0000-4000-8000-000000000001";
    static final String T04 = "40000000-0000-4000-8000-000000000004";
    static final String PETRA = person(2);
    static final String ANNA = person(5);
    static final String LEA = person(6);
    static final String MIA = person(7);
    static final String NINA = person(9);
    static final String KIM = person(11);
    static final String EVA = person(12);

    private static String person(int nr) {
        return "20000000-0000-4000-8000-0000000000" + (nr < 10 ? "0" + nr : nr);
    }

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static ValidatableResponse lineups(String session) {
        return given().cookie("stv_session", session).get("/api/teams/" + TEAM_1 + "/special-lineups").then();
    }

    private static ValidatableResponse create(String session, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .post("/api/teams/" + TEAM_1 + "/special-lineups").then();
    }

    private static ValidatableResponse update(String session, String id, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .put("/api/teams/" + TEAM_1 + "/special-lineups/" + id).then();
    }

    private static String place(String label, String person) {
        return "{\"label\":" + (label == null ? "null" : "\"" + label + "\"") + ",\"personId\":"
                + (person == null ? "null" : "\"" + person + "\"") + "}";
    }

    private static String powerPlay(String session) {
        return create(session, "{\"name\":\"Überzahl 1\",\"places\":[" + place("Center", ANNA) + ","
                + place("Flügel links", KIM) + "," + place(null, null) + "]}")
                .statusCode(201).extract().path("id");
    }

    @Test
    void lin13CoachCreatesALineupWithFreeNameAndPlaces() {
        String petra = login("petra");
        lineups(petra).statusCode(200).body("lineups", empty()).body("canEdit", equalTo(true))
                .body("players.firstName", contains("Kim", "Sara", "Mia", "Lea", "Anna", "Nina", "Jana"));

        JsonPath created = create(petra, "{\"name\":\" Überzahl 1 \",\"places\":[" + place("Center", ANNA) + ","
                + place("Flügel links", KIM) + "," + place(" ", null) + "]}")
                .statusCode(201).extract().jsonPath();
        assertEquals("Überzahl 1", created.getString("name"));
        assertThat(created.getList("places.position"), contains(1, 2, 3));
        assertThat(created.getList("places.label"), contains("Center", "Flügel links", null));
        assertEquals("Anna", created.getString("places[0].person.firstName"));
        assertEquals(null, created.getString("places[2].person"));

        // Everyone in the team sees it, in the order of creation
        create(petra, "{\"name\":\"Unterzahl\",\"places\":[" + place(null, MIA) + "," + place(null, ANNA) + "]}")
                .statusCode(201);
        lineups(login("anna")).statusCode(200).body("canEdit", equalTo(false))
                .body("lineups.name", contains("Überzahl 1", "Unterzahl"))
                .body("lineups[1].places.person.firstName", contains("Mia", "Anna"));
    }

    @Test
    void lin15NameNumberOfPlacesAndPlayersChangeInOneRequest() {
        String petra = login("petra");
        String id = powerPlay(petra);

        update(petra, id, "{\"name\":\"Powerplay\",\"places\":[" + place("Spitze", LEA) + "," + place(null, ANNA) + ","
                + place(null, MIA) + "," + place("Hinten", NINA) + "," + place(null, null) + "]}")
                .statusCode(200).body("name", equalTo("Powerplay")).body("places", hasSize(5))
                .body("places[0].label", equalTo("Spitze")).body("places[0].person.firstName", equalTo("Lea"))
                .body("places[3].person.firstName", equalTo("Nina")).body("places[4].person", nullValue());
        // Fewer places
        update(petra, id, "{\"name\":\"Powerplay\",\"places\":[" + place(null, LEA) + "]}").statusCode(200)
                .body("places", hasSize(1));
        lineups(petra).body("lineups", hasSize(1)).body("lineups[0].places", hasSize(1));

        given().cookie("stv_session", petra).delete("/api/teams/" + TEAM_1 + "/special-lineups/" + id).then()
                .statusCode(204);
        lineups(petra).body("lineups", empty());
        given().cookie("stv_session", petra).delete("/api/teams/" + TEAM_1 + "/special-lineups/" + id).then()
                .statusCode(404);
    }

    @Test
    void lin14RulesOfThePlaces() {
        String petra = login("petra");
        powerPlay(petra);
        // A player twice
        create(petra, "{\"name\":\"Box\",\"places\":[" + place(null, ANNA) + "," + place(null, ANNA) + "]}")
                .statusCode(422).body("rule", equalTo("LIN-14")).body("fields[0].field", equalTo("places[1].personId"));
        // Coaches and players of other teams cannot be set
        create(petra, "{\"name\":\"Box\",\"places\":[" + place(null, PETRA) + "]}").statusCode(422);
        create(petra, "{\"name\":\"Box\",\"places\":[" + place(null, EVA) + "]}").statusCode(422);
        // 1 to 10 places, a name, a short label
        create(petra, "{\"name\":\"Box\",\"places\":[]}").statusCode(400);
        StringBuilder eleven = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            eleven.append(i == 0 ? "" : ",").append(place(null, null));
        }
        create(petra, "{\"name\":\"Box\",\"places\":[" + eleven + "]}").statusCode(400);
        create(petra, "{\"name\":\"\",\"places\":[" + place(null, null) + "]}").statusCode(400);
        create(petra, "{\"name\":\"Box\",\"places\":[" + place("x".repeat(31), null) + "]}").statusCode(400);
        // The name is unique in the team; the same player may be in several line-ups
        create(petra, "{\"name\":\"überzahl 1\",\"places\":[" + place(null, null) + "]}").statusCode(422)
                .body("rule", equalTo("LIN-13"));
        create(petra, "{\"name\":\"Überzahl 2\",\"places\":[" + place(null, ANNA) + "]}").statusCode(201);
    }

    @Test
    void lin15OnlyCoachesAndAdminsChangeLineups() {
        String id = powerPlay(login("petra"));
        String anna = login("anna");
        create(anna, "{\"name\":\"Box\",\"places\":[" + place(null, null) + "]}").statusCode(403);
        update(anna, id, "{\"name\":\"Box\",\"places\":[" + place(null, null) + "]}").statusCode(403);
        given().cookie("stv_session", anna).delete("/api/teams/" + TEAM_1 + "/special-lineups/" + id).then()
                .statusCode(403);
        // Eva plays in Team 2 only
        lineups(login("eva")).statusCode(403);
        // Coaches of other teams manage every team (E-69)
        update(login("marc"), id, "{\"name\":\"Box\",\"places\":[" + place(null, null) + "]}").statusCode(200);
    }

    @Test
    void lin16MatchDayShowsTheLineupsWithResponsesAndGaps() {
        String petra = login("petra");
        powerPlay(petra);

        JsonPath json = given().cookie("stv_session", login("anna")).get("/api/events/" + T04 + "/special-lineups")
                .then().statusCode(200).extract().jsonPath();
        assertThat(json.getList("lineups.name"), contains("Überzahl 1"));
        // Anna comes, Kim declined, the third place is empty
        assertThat(json.getList("lineups[0].places.response"), contains("ATTENDING", "DECLINED", null));
        assertThat(json.getList("lineups[0].places.gap"), contains(false, true, false));
        assertThat(json.getList("lineups[0].places.calledUp"), contains(null, null, null));

        // With a published call-up (Anna, Lea, Mia, Sara, Jana), whoever is not called up is a gap as well
        given().cookie("stv_session", petra).post("/api/events/" + T04 + "/call-up/publish").then().statusCode(200);
        json = given().cookie("stv_session", petra).get("/api/events/" + T04 + "/special-lineups").then()
                .statusCode(200).extract().jsonPath();
        assertThat(json.getList("lineups[0].places.calledUp"), contains(true, false, null));
        assertThat(json.getList("lineups[0].places.gap"), contains(false, true, false));

        given().cookie("stv_session", petra).get("/api/events/" + T01 + "/special-lineups").then().statusCode(409);
        given().cookie("stv_session", login("tina")).get("/api/events/" + T04 + "/special-lineups").then()
                .statusCode(403);
    }

    @Test
    void lin17LineupsMoveIntoTheNewSeason() {
        String alex = login("alex");
        powerPlay(login("petra"));
        String season = given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"name\":\"2027/28\",\"startDate\":\"2027-07-01\",\"endDate\":\"2028-06-30\","
                        + "\"copyFrom\":\"60000000-0000-4000-8000-000000000002\"}")
                .post("/api/seasons").then().statusCode(201).extract().path("id");
        given().cookie("stv_session", alex).post("/api/seasons/" + season + "/activate").then().statusCode(204);

        lineups(alex).statusCode(200).body("lineups.name", contains("Überzahl 1"))
                .body("lineups[0].places.label", contains("Center", "Flügel links", null))
                .body("lineups[0].places[0].person.firstName", equalTo("Anna"));
    }
}
