package ch.stvbero.platform.core.driver;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
 * Proposed driver list (DRV-19), driving settings (DRV-18) and travelling on one's own (DRV-20, ANM-13), against
 * the seed of docs/test-data.md: match day 04 of Team 1 with Petra's full car (Anna, Lea, Mia) and Sara's empty
 * car with three seats; Jana responded «dabei» and has no seat. Default "now": Mon 16.11.2026 06:00.
 */
@QuarkusTest
class DriverProposalTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String T01 = "40000000-0000-4000-8000-000000000001";
    static final String T04 = "40000000-0000-4000-8000-000000000004";
    static final String PETRA = person(2);
    static final String ANNA = person(5);
    static final String LEA = person(6);
    static final String MIA = person(7);
    static final String SARA = person(8);
    static final String NINA = person(9);
    static final String JANA = person(10);
    static final String KIM = person(11);
    static final String EVA = person(12);

    private static String person(int nr) {
        return "20000000-0000-4000-8000-0000000000" + (nr < 10 ? "0" + nr : nr);
    }

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static ValidatableResponse list(String session, String event) {
        return given().cookie("stv_session", session).get("/api/events/" + event + "/driver-list").then();
    }

    private static ValidatableResponse propose(String session, String event) {
        return given().cookie("stv_session", session).post("/api/events/" + event + "/driver-list/proposal").then();
    }

    private static ValidatableResponse apply(String session, String event, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .post("/api/events/" + event + "/driver-list/proposal/apply").then();
    }

    private static ValidatableResponse individual(String session, String event, String person, boolean on) {
        var request = given().cookie("stv_session", session);
        String path = "/api/events/" + event + "/driver-list/individual/" + person;
        return (on ? request.put(path) : request.delete(path)).then();
    }

    private static ValidatableResponse driving(String session, String person, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .put("/api/persons/" + person + "/driving").then();
    }

    private static ValidatableResponse respond(String session, String event, String person, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .put("/api/events/" + event + "/responses/" + (person == null ? "me" : person)).then();
    }

    /** A match day of Team 1 on 06.12.2026 with meeting point and venue, without cars. */
    private static String createMatchDay(String session) {
        String body = """
                {"type":"MATCH_DAY","title":"Spieltag","start":"2026-12-06T10:00:00+01:00","end":"2026-12-06T14:00:00+01:00",
                 "teamIds":["%s"],"location":{"name":"Sporthalle Seefeld"},
                 "meetingPoint":{"time":"2026-12-06T08:00:00+01:00","location":"Parkplatz Sporthalle Nord"}}"""
                .formatted(TEAM_1);
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .post("/api/events").then().statusCode(201).extract().path("id");
    }

    // ------------------------------------------------------------------ Driving settings (DRV-18)

    @Test
    void drv18EveryoneCanDriveWithFivePlacesByDefault() {
        String anna = login("anna");
        given().cookie("stv_session", anna).get("/api/persons/" + ANNA + "/driving").then().statusCode(200)
                .body("canDrive", equalTo(true)).body("carSeats", equalTo(5));
        // Five places are the driver and four others
        list(anna, T04).statusCode(200).body("defaultSeats", equalTo(4));

        driving(anna, ANNA, "{\"canDrive\":true,\"carSeats\":7}").statusCode(200).body("carSeats", equalTo(7));
        list(anna, T04).body("defaultSeats", equalTo(6));
        driving(anna, ANNA, "{\"canDrive\":false,\"carSeats\":5}").statusCode(200).body("canDrive", equalTo(false));

        driving(anna, ANNA, "{\"canDrive\":true,\"carSeats\":1}").statusCode(400);
        driving(anna, ANNA, "{\"canDrive\":true,\"carSeats\":10}").statusCode(400);
        driving(anna, ANNA, "{\"carSeats\":5}").statusCode(400);
    }

    @Test
    void drv18CoachesChangeItForEveryonePlayersOnlyForThemselves() {
        driving(login("anna"), LEA, "{\"canDrive\":false,\"carSeats\":5}").statusCode(403);
        given().cookie("stv_session", login("anna")).get("/api/persons/" + LEA + "/driving").then().statusCode(403);
        driving(login("petra"), LEA, "{\"canDrive\":false,\"carSeats\":4}").statusCode(200);
        driving(login("alex"), person(99), "{\"canDrive\":false,\"carSeats\":4}").statusCode(404);

        // The season plan lists who can drive
        JsonPath plan = given().cookie("stv_session", login("anna")).get("/api/teams/" + TEAM_1 + "/driver-plan")
                .then().statusCode(200).extract().jsonPath();
        assertEquals(false, plan.getBoolean("drivers.find { it.person.firstName == 'Lea' }.canDrive"));
        assertEquals(4, plan.getInt("drivers.find { it.person.firstName == 'Lea' }.carSeats"));
        assertEquals(false, plan.getBoolean("drivers.find { it.person.firstName == 'Lea' }.canChange"));
        assertEquals(true, plan.getBoolean("drivers.find { it.person.firstName == 'Anna' }.canChange"));
        assertEquals(true, given().cookie("stv_session", login("petra")).get("/api/teams/" + TEAM_1 + "/driver-plan")
                .then().extract().jsonPath().getBoolean("drivers.find { it.person.firstName == 'Lea' }.canChange"));
    }

    // ------------------------------------------------------------------ Proposal (DRV-19)

    @Test
    void drv19ProposalFillsTheCarsThatExist() {
        String petra = login("petra");
        JsonPath json = propose(petra, T04).statusCode(200).extract().jsonPath();

        // Petra's car is full; Jana gets a seat in Sara's empty car. Nobody else has to drive.
        assertThat(json.getList("cars.driver.firstName"), contains("Sara", "Petra"));
        assertThat(json.getList("cars.existing"), contains(true, true));
        assertThat(json.getList("cars[0].passengers.firstName"), contains("Jana"));
        assertThat(json.getList("cars[0].added.firstName"), contains("Jana"));
        assertThat(json.getList("cars[1].added"), empty());
        assertThat(json.getList("withoutSeat"), empty());
        assertEquals(true, json.getBoolean("changes"));
        // Nothing is changed yet
        list(petra, T04).body("withoutSeat.firstName", contains("Jana"));

        String saraCar = list(petra, T04).extract().path("cars.find { it.driver.firstName == 'Sara' }.id");
        apply(petra, T04, "{\"cars\":[{\"driverId\":\"" + SARA + "\",\"seats\":3,\"passengerIds\":[\"" + JANA + "\"]}]}")
                .statusCode(200).body("withoutSeat", empty())
                .body("cars.find { it.id == '" + saraCar + "' }.passengers.firstName", contains("Jana"));
        // Jana and Sara are told (DRV-11); a second proposal has nothing to add
        given().cookie("stv_session", login("jana")).get("/api/notifications").then()
                .body("findAll { it.type == 'DRIVER_LIST_CHANGED' }", hasSize(1));
        propose(petra, T04).statusCode(200).body("changes", equalTo(false));
    }

    @Test
    void drv19ProposalChoosesDriversFairlyAndSeatsEveryone() {
        String petra = login("petra");
        String matchDay = createMatchDay(petra);
        // Seven players and the coach come. Sara and Petra already drive on match day 04.
        for (String player : List.of(ANNA, LEA, MIA, SARA, NINA, JANA, KIM)) {
            respond(petra, matchDay, player, "{\"status\":\"ATTENDING\"}").statusCode(200);
        }
        respond(petra, matchDay, null, "{\"status\":\"ATTENDING\"}").statusCode(200);
        // Kim (Baumann) cannot drive; Lea (Graf) has a small car with three places
        driving(petra, KIM, "{\"canDrive\":false,\"carSeats\":5}").statusCode(200);
        driving(petra, LEA, "{\"canDrive\":true,\"carSeats\":3}").statusCode(200);

        JsonPath json = propose(petra, matchDay).statusCode(200).extract().jsonPath();

        // Eight people. By name: Baumann (cannot drive), Brun (1 trip), Frei, Graf, Keller (1 trip), Muster, Roth,
        // Wyss. Mia Frei drives first (four seats): seven left for four. Lea Graf drives (two seats): six left for
        // six seats.
        assertThat(json.getList("cars.driver.firstName"), contains("Mia", "Lea"));
        assertThat(json.getList("cars.existing"), contains(false, false));
        assertThat(json.getList("cars.seats"), contains(4, 2));
        assertThat(json.getList("cars[0].passengers.firstName"), contains("Kim", "Sara", "Petra", "Anna"));
        assertThat(json.getList("cars[1].passengers.firstName"), contains("Nina", "Jana"));
        assertThat(json.getList("withoutSeat"), empty());

        apply(petra, matchDay, """
                {"cars":[{"driverId":"%s","seats":4,"passengerIds":["%s","%s","%s","%s"]},
                         {"driverId":"%s","seats":2,"passengerIds":["%s","%s"]}]}"""
                .formatted(MIA, KIM, SARA, PETRA, ANNA, LEA, NINA, JANA))
                .statusCode(200).body("cars", hasSize(2)).body("withoutSeat", empty())
                .body("cars.find { it.driver.firstName == 'Mia' }.freeSeats", equalTo(0));
    }

    @Test
    void drv19WithoutEnoughDriversTheProposalSaysWhoIsLeft() {
        String petra = login("petra");
        String matchDay = createMatchDay(petra);
        for (String player : List.of(ANNA, LEA, MIA)) {
            respond(petra, matchDay, player, "{\"status\":\"ATTENDING\"}").statusCode(200);
            driving(petra, player, "{\"canDrive\":false,\"carSeats\":5}").statusCode(200);
        }
        propose(petra, matchDay).statusCode(200).body("cars", empty()).body("changes", equalTo(false))
                .body("withoutSeat.firstName", contains("Mia", "Lea", "Anna"));
    }

    @Test
    void drv19MembersCoachesAndAdminsProposeAndApply() {
        // «Alle verteilen» in «Plätze zuweisen»: every member of the team (DRV-32)
        propose(login("anna"), T04).statusCode(200);
        apply(login("anna"), T04, "{\"cars\":[]}").statusCode(200);
        propose(login("eva"), T04).statusCode(403);
        propose(login("petra"), T01).statusCode(409);
        // A car that would be too full is refused, and nothing of the request is entered
        apply(login("petra"), T04, "{\"cars\":[{\"driverId\":\"" + NINA + "\",\"seats\":1,\"passengerIds\":[\""
                + JANA + "\",\"" + KIM + "\"]}]}").statusCode(409);
        list(login("petra"), T04).body("cars", hasSize(2)).body("withoutSeat.firstName", contains("Jana"));
        apply(login("petra"), T04, "{\"cars\":[{\"driverId\":\"" + NINA + "\",\"seats\":0,\"passengerIds\":[]}]}")
                .statusCode(400);
    }

    // ------------------------------------------------------------------ Travelling on one's own (DRV-20, ANM-13)

    @Test
    void drv20SomeoneWhoTravelsOnHerOwnNeedsNoSeat() {
        String jana = login("jana");
        individual(jana, T04, JANA, true).statusCode(200)
                .body("individual.firstName", contains("Jana")).body("withoutSeat", empty());
        // Everyone who sees the responses sees it (ANM-13)
        given().cookie("stv_session", login("anna")).get("/api/events/" + T04 + "/overview").then()
                .body("teams[0].groups.entries.flatten().find { it.person.firstName == 'Jana' }.response.travelsIndividually",
                        equalTo(true));
        given().cookie("stv_session", jana).get("/api/events/" + T04).then()
                .body("myResponse.travelsIndividually", equalTo(true));
        // The proposal leaves her out
        propose(login("petra"), T04).body("changes", equalTo(false)).body("withoutSeat", empty());

        individual(jana, T04, JANA, false).statusCode(200).body("individual", empty())
                .body("withoutSeat.firstName", contains("Jana"));
        given().cookie("stv_session", jana).get("/api/events/" + T04).then()
                .body("myResponse.travelsIndividually", nullValue());
    }

    @Test
    void drv20SeatAndCarAndTheMark() {
        String petra = login("petra");
        String anna = login("anna");
        // Anna sits in Petra's car: marked, she leaves her seat, and Petra is told
        individual(anna, T04, ANNA, true).statusCode(200).body("individual.firstName", contains("Anna"))
                .body("cars.find { it.driver.firstName == 'Petra' }.passengers.firstName", contains("Mia", "Lea"));
        given().cookie("stv_session", petra).get("/api/notifications").then()
                .body("findAll { it.type == 'DRIVER_LIST_CHANGED' }", hasSize(1));
        // Taking a seat again removes the mark
        String petraCar = list(anna, T04).extract().path("cars.find { it.driver.firstName == 'Petra' }.id");
        given().cookie("stv_session", anna).put("/api/events/" + T04 + "/driver-list/cars/" + petraCar
                + "/passengers/" + ANNA).then().statusCode(200).body("individual", empty());

        // Sara drives: she cannot travel on her own until her car is removed
        individual(login("sara"), T04, SARA, true).statusCode(409).body("rule", equalTo("DRV-20"));
        // Players mark themselves only; coaches mark others, who are told
        individual(anna, T04, JANA, true).statusCode(403);
        individual(petra, T04, JANA, true).statusCode(200).body("individual.firstName", contains("Jana"));
        given().cookie("stv_session", login("jana")).get("/api/notifications").then()
                .body("findAll { it.type == 'DRIVER_LIST_CHANGED' }", hasSize(1));
        // Eva plays in Team 2 only
        individual(petra, T04, EVA, true).statusCode(422);
        individual(petra, T01, ANNA, true).statusCode(409);
    }

    @Test
    void anm13TheMarkComesAndGoesWithTheResponse() {
        String nina = login("nina");
        respond(nina, T04, null, "{\"status\":\"ATTENDING\",\"travelsIndividually\":true}").statusCode(200)
                .body("travelsIndividually", equalTo(true));
        list(nina, T04).body("individual.firstName", contains("Nina")).body("withoutSeat.firstName", contains("Jana"));

        // «dabei» again without the field leaves the mark; «nicht dabei» removes it
        respond(nina, T04, null, "{\"status\":\"ATTENDING\"}").statusCode(200).body("travelsIndividually", equalTo(true));
        respond(nina, T04, null, "{\"status\":\"DECLINED\"}").statusCode(200).body("travelsIndividually", nullValue());
        list(nina, T04).body("individual", empty());

        // Explicitly off; and a coach sets it on behalf
        respond(nina, T04, null, "{\"status\":\"ATTENDING\",\"travelsIndividually\":true}").statusCode(200);
        respond(nina, T04, null, "{\"status\":\"ATTENDING\",\"travelsIndividually\":false}").statusCode(200)
                .body("travelsIndividually", nullValue());
        respond(login("petra"), T04, NINA, "{\"status\":\"ATTENDING\",\"travelsIndividually\":true}").statusCode(200)
                .body("travelsIndividually", equalTo(true));

        // A practice has no driver list: the field is ignored
        respond(nina, T01, null, "{\"status\":\"ATTENDING\",\"travelsIndividually\":true}").statusCode(200)
                .body("travelsIndividually", nullValue());
        // A driver stays a driver
        respond(login("sara"), T04, null, "{\"status\":\"ATTENDING\",\"travelsIndividually\":true}").statusCode(200)
                .body("travelsIndividually", nullValue());
        list(nina, T04).body("cars.driver.firstName", contains("Sara", "Petra"));
    }
}
