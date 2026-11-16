package ch.stvbero.platform.core.error;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.containsString;

/**
 * Rework #31: a request without a body to an endpoint with a required body gives 400 with Problem Details,
 * a German {@code detail} and no English message or internal parameter name in {@code fields}
 * (docs/api/README.md, errors).
 */
@QuarkusTest
class MissingBodyTest {

    @Test
    void seasonWithoutBody() {
        check(given().cookie("stv_session", login("alex")).contentType(ContentType.JSON)
                .post("/api/seasons").then());
    }

    @Test
    void testLoginWithoutBody() {
        check(given().contentType(ContentType.JSON).post("/api/test/login").then());
    }

    @Test
    void jsonNullAsBody() {
        check(given().cookie("stv_session", login("alex")).contentType(ContentType.JSON).body("null")
                .post("/api/seasons").then());
    }

    @Test
    void missingFieldInBodyIsStillNamed() {
        given().cookie("stv_session", login("alex")).contentType(ContentType.JSON)
                .body("{\"startDate\":\"2027-07-01\",\"endDate\":\"2028-06-30\"}")
                .post("/api/seasons")
                .then().statusCode(400)
                .contentType(startsWith(Problem.MEDIA_TYPE))
                .body("fields.field", org.hamcrest.Matchers.hasItem("name"))
                .body("fields.message", org.hamcrest.Matchers.everyItem(not(containsString("must"))));
    }

    private static void check(ValidatableResponse response) {
        response.statusCode(400)
                .contentType(startsWith(Problem.MEDIA_TYPE))
                .body("status", equalTo(400))
                .body("title", equalTo("Ungültige Anfrage"))
                .body("detail", equalTo("Die Anfrage enthält keine Daten. Bitte die Eingaben prüfen."))
                .body("fields", anyOf(nullValue(), empty()))
                .body(not(containsString("must not be null")))
                .body(not(containsString("input")));
    }
}
