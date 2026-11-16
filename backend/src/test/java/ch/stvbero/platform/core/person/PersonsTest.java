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

/** F-01: Personenverwaltung (PTF-01, PTF-11, RR-04, RR-07). */
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
    void listByLastNameWithSearch() {
        given().cookie("stv_session", alex).get("/api/persons").then().statusCode(200)
                .body("", hasSize(17))
                .body("[0].nachname", equalTo("Admin"))
                .body("[1].nachname", equalTo("Baumann"));
        given().cookie("stv_session", alex).queryParam("search", "kel").get("/api/persons").then().statusCode(200)
                .body("firstName", contains("Petra"));
        given().cookie("stv_session", alex).queryParam("search", "petra keller").get("/api/persons").then().statusCode(200)
                .body("id", contains(PETRA));
    }

    @Test
    void ptf11PersonCreateAndUpdate() {
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
    void personDetailShowsMembershipsAllSeasons() {
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
    void rr07NotAdminsGet403() {
        String petra = login("petra");
        given().cookie("stv_session", petra).get("/api/persons").then().statusCode(403)
                .contentType("application/problem+json")
                .body("title", equalTo("Keine Berechtigung"));
        given().cookie("stv_session", petra).get("/api/persons/" + PETRA).then().statusCode(403);
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"firstName\":\"X\",\"lastName\":\"Y\"}")
                .post("/api/persons").then().statusCode(403);
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"firstName\":\"Petra\",\"lastName\":\"Keller\",\"admin\":true}")
                .put("/api/persons/" + PETRA).then().statusCode(403);
        given().cookie("stv_session", alex).get("/api/persons").then().statusCode(200)
                .body("findAll { it.admin }.firstName", hasItem("Alex"))
                .body("findAll { it.admin }", hasSize(1));
    }
}
