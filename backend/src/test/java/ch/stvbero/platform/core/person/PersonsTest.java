package ch.stvbero.platform.core.person;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

/** F-01: person administration (PTF-01, PTF-11, RR-04, RR-07). */
@QuarkusTest
class PersonsTest {

    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String ALEX = "20000000-0000-4000-8000-000000000001";

    String alex;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
        alex = login("alex");
    }

    @Test
    void listIsSortedByLastNameAndSearchable() {
        given().cookie("stv_session", alex).get("/api/persons").then().statusCode(200)
                .body("", hasSize(17))
                .body("[0].lastName", equalTo("Admin"))
                .body("[1].lastName", equalTo("Baumann"));
        given().cookie("stv_session", alex).queryParam("search", "kel").get("/api/persons").then().statusCode(200)
                .body("firstName", contains("Petra"));
        given().cookie("stv_session", alex).queryParam("search", "petra keller").get("/api/persons").then().statusCode(200)
                .body("id", contains(PETRA));
    }

    @Test
    void ptf11CreateAndUpdatePerson() {
        String id = given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"firstName\":\" Nora \",\"lastName\":\"Lang\",\"email\":\"\"}")
                .post("/api/persons").then().statusCode(201)
                .body("firstName", equalTo("Nora"))
                .body("email", nullValue())
                .body("admin", equalTo(false))
                .extract().path("id");

        given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"firstName\":\"Nora\",\"lastName\":\"Lang-Meier\",\"email\":\"nora@example.ch\",\"admin\":false}")
                .put("/api/persons/" + id).then().statusCode(200)
                .body("lastName", equalTo("Lang-Meier"))
                .body("email", equalTo("nora@example.ch"));

        given().cookie("stv_session", alex).get("/api/persons/" + id).then().statusCode(200)
                .body("lastName", equalTo("Lang-Meier"))
                .body("memberships", hasSize(0));
    }

    @Test
    void ptf15GenderAndYearOfBirthAreOptional() {
        // The seed: Petra is a woman born 1987, Alex has no details
        given().cookie("stv_session", alex).get("/api/persons").then().statusCode(200)
                .body("find { it.id == '" + PETRA + "' }.gender", equalTo("FEMALE"))
                .body("find { it.id == '" + PETRA + "' }.birthYear", equalTo(1987))
                .body("find { it.id == '" + ALEX + "' }.gender", nullValue());

        String id = given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"firstName\":\"Nico\",\"lastName\":\"Lang\",\"gender\":\"MALE\",\"birthYear\":2009}")
                .post("/api/persons").then().statusCode(201)
                .body("gender", equalTo("MALE"))
                .body("birthYear", equalTo(2009))
                .extract().path("id");
        given().cookie("stv_session", alex).get("/api/persons/" + id).then().statusCode(200)
                .body("gender", equalTo("MALE"))
                .body("birthYear", equalTo(2009));

        // Without them they are not given
        given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"firstName\":\"Nico\",\"lastName\":\"Lang\"}")
                .put("/api/persons/" + id).then().statusCode(200)
                .body("gender", nullValue())
                .body("birthYear", nullValue());

        // A year outside 1900 to 2100 and an unknown gender are refused
        given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"firstName\":\"Nico\",\"lastName\":\"Lang\",\"birthYear\":1800}")
                .put("/api/persons/" + id).then().statusCode(400)
                .body("fields.field", contains("birthYear"));
        given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"firstName\":\"Nico\",\"lastName\":\"Lang\",\"gender\":\"OTHER\"}")
                .put("/api/persons/" + id).then().statusCode(400);
    }

    @Test
    void personDetailShowsMembershipsOfAllSeasons() {
        given().cookie("stv_session", alex).get("/api/persons/" + PETRA).then().statusCode(200)
                .body("memberships.team.name", contains("Team 1", "Team 2"))
                .body("memberships.teamRole", contains("COACH", "PLAYER"));
        given().cookie("stv_session", alex).get("/api/persons/20000000-0000-4000-8000-000000000017").then().statusCode(200)
                .body("memberships.seasonId", contains("60000000-0000-4000-8000-000000000001"));
    }

    @Test
    void invalidInputGives400WithFields() {
        given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"firstName\":\"\",\"lastName\":\"Lang\",\"email\":\"kein-mail\"}")
                .post("/api/persons").then().statusCode(400)
                .body("fields.field", contains("email", "firstName"));
    }

    @Test
    void rr04LastAdminStaysAdmin() {
        given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"firstName\":\"Alex\",\"lastName\":\"Admin\",\"admin\":false}")
                .put("/api/persons/" + ALEX).then().statusCode(409)
                .body("rule", equalTo("RR-04"));
    }

    @Test
    void unknownPersonGives404() {
        given().cookie("stv_session", alex).get("/api/persons/20000000-0000-4000-8000-000000000099").then().statusCode(404);
    }

    @Test
    void rr07PlayersGet403() {
        String anna = login("anna");
        given().cookie("stv_session", anna).get("/api/persons").then().statusCode(403)
                .contentType("application/problem+json")
                .body("title", equalTo("Keine Berechtigung"));
        given().cookie("stv_session", anna).get("/api/persons/" + PETRA).then().statusCode(403);
        given().cookie("stv_session", anna).contentType(ContentType.JSON)
                .body("{\"firstName\":\"X\",\"lastName\":\"Y\"}")
                .post("/api/persons").then().statusCode(403);
        given().cookie("stv_session", anna).contentType(ContentType.JSON)
                .body("{\"firstName\":\"Petra\",\"lastName\":\"Keller\"}")
                .put("/api/persons/" + PETRA).then().statusCode(403);
        given().cookie("stv_session", alex).get("/api/persons").then().statusCode(200)
                .body("findAll { it.admin }.firstName", hasItem("Alex"))
                .body("findAll { it.admin }", hasSize(1));
    }

    /** F-12: coaches manage persons, but only admins grant or remove the admin flag (RR-04, E-69). */
    @Test
    void coachesManagePersonsButNotTheAdminFlag() {
        String petra = login("petra");
        given().cookie("stv_session", petra).get("/api/persons").then().statusCode(200);
        given().cookie("stv_session", petra).get("/api/persons/" + ALEX).then().statusCode(200);
        String id = given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"firstName\":\"Nora\",\"lastName\":\"Neu\"}")
                .post("/api/persons").then().statusCode(201).body("admin", equalTo(false))
                .extract().path("id");
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"firstName\":\"Nora\",\"lastName\":\"Neu\",\"admin\":false}")
                .put("/api/persons/" + id).then().statusCode(200);
        // Granting the admin flag: 403, on create and on update (criterion 4)
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"firstName\":\"X\",\"lastName\":\"Y\",\"admin\":true}")
                .post("/api/persons").then().statusCode(403).body("title", equalTo("Keine Berechtigung"));
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"firstName\":\"Petra\",\"lastName\":\"Keller\",\"admin\":true}")
                .put("/api/persons/" + PETRA).then().statusCode(403);
        // Removing it: 403. Editing an admin without the flag keeps it.
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"firstName\":\"Alex\",\"lastName\":\"Admin\",\"admin\":false}")
                .put("/api/persons/" + ALEX).then().statusCode(403);
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"firstName\":\"Alexandra\",\"lastName\":\"Admin\",\"email\":\"alex@example.ch\"}")
                .put("/api/persons/" + ALEX).then().statusCode(200).body("admin", equalTo(true));
        given().cookie("stv_session", alex).get("/api/persons").then()
                .body("findAll { it.admin }", hasSize(1));
    }
}
