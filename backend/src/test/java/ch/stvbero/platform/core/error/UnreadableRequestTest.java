package ch.stvbero.platform.core.error;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.startsWith;

/**
 * Rework #16: unreadable requests (invalid JSON, wrong type, wrong format) give 400 with Problem Details
 * and a German {@code detail}, like all other errors (docs/api/README.md).
 */
@QuarkusTest
class UnreadableRequestTest {

    @Test
    void invalidJsonToTestEndpointGivesProblemDetails() {
        given().contentType(ContentType.JSON).body("{\"loginKey\": ")
                .post("/api/test/login")
                .then().statusCode(400)
                .contentType(startsWith(Problem.MEDIA_TYPE))
                .body("status", equalTo(400))
                .body("title", equalTo("Ungültige Anfrage"))
                .body("detail", equalTo("Die Anfrage konnte nicht gelesen werden. Bitte die Eingaben prüfen."));
    }

    @Test
    void invalidJsonToDomainEndpointGivesProblemDetails() {
        given().cookie("stv_session", login("alex")).contentType(ContentType.JSON).body("kein json")
                .post("/api/seasons")
                .then().statusCode(400)
                .contentType(startsWith(Problem.MEDIA_TYPE))
                .body("status", equalTo(400))
                .body("detail", not(blankOrNullString()));
    }

    @Test
    void wrongDateFormatNamesTheField() {
        given().cookie("stv_session", login("alex")).contentType(ContentType.JSON)
                .body("{\"name\":\"2027/28\",\"startDate\":\"irgendwann\",\"endDate\":\"2028-06-30\"}")
                .post("/api/seasons")
                .then().statusCode(400)
                .contentType(startsWith(Problem.MEDIA_TYPE))
                .body("status", equalTo(400))
                .body("fields[0].field", equalTo("startDate"))
                .body("fields[0].message", equalTo("Ungültiger Wert."));
    }

    @Test
    void wrongTypeGivesProblemDetails() {
        given().cookie("stv_session", login("alex")).contentType(ContentType.JSON)
                .body("[1, 2]")
                .post("/api/seasons")
                .then().statusCode(400)
                .contentType(startsWith(Problem.MEDIA_TYPE))
                .body("status", equalTo(400));
    }

    @Test
    void otherFrameworkErrorsStayUnchanged() {
        given().get("/api/gibt-es-nicht").then().statusCode(404);
        given().contentType(ContentType.TEXT).body("alex").post("/api/test/login").then().statusCode(415);
        given().delete("/api/seasons").then().statusCode(405);
    }
}
