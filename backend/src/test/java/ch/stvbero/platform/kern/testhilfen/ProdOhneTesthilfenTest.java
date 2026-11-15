package ch.stvbero.platform.kern.testhilfen;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * F-00, Kriterium 8: Ohne Test-Hilfen (wie im Profil prod, dort ist {@code stv.testhilfen=false})
 * existieren die Test-Endpunkte nicht.
 */
@QuarkusTest
@TestProfile(ProdOhneTesthilfenTest.WieProd.class)
class ProdOhneTesthilfenTest {

    public static class WieProd implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("stv.testhilfen", "false");
        }
    }

    @Test
    void testEndpunkteFehlen() {
        given().post("/api/test/reset").then().statusCode(404);
        given().get("/api/test/uhr").then().statusCode(404);
        given().contentType("application/json").body("{\"jetzt\":\"2026-11-16T19:00:00+01:00\"}")
                .put("/api/test/uhr").then().statusCode(404);
        given().delete("/api/test/uhr").then().statusCode(404);
        given().contentType("application/json").body("{\"schluessel\":\"alex\"}")
                .post("/api/test/anmelden").then().statusCode(404);
        given().get("/api/test/personen").then().statusCode(404);
    }

    @Test
    void testEndpunkteFehlenAuchInDerOpenApi() {
        String spezifikation = given().get("/q/openapi?format=json").then().statusCode(200).extract().asString();
        org.junit.jupiter.api.Assertions.assertFalse(spezifikation.contains("/api/test/"), "Test-Pfade in der OpenAPI");
    }

    @Test
    void fachlicheEndpunkteBleibenGeschuetzt() {
        given().get("/api/ich").then().statusCode(401);
    }
}
