package ch.stvbero.platform.core.testsupport;

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
@TestProfile(ProdWithoutTestSupportTest.LikeProd.class)
class ProdWithoutTestSupportTest {

    public static class LikeProd implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("stv.test-support", "false");
        }
    }

    @Test
    void testEndpointsMissing() {
        given().post("/api/test/reset").then().statusCode(404);
        given().get("/api/test/clock").then().statusCode(404);
        given().contentType("application/json").body("{\"now\":\"2026-11-16T19:00:00+01:00\"}")
                .put("/api/test/clock").then().statusCode(404);
        given().delete("/api/test/clock").then().statusCode(404);
        given().contentType("application/json").body("{\"loginKey\":\"alex\"}")
                .post("/api/test/login").then().statusCode(404);
        given().get("/api/test/persons").then().statusCode(404);
        given().delete("/api/test/current-season").then().statusCode(404);
        given().contentType("application/json").body("{\"suTeamId\":900001,\"matches\":[]}")
                .put("/api/test/swiss-unihockey/matches").then().statusCode(404);
    }

    /** F-07, Kriterium 2: Im Profil prod spricht die App mit swiss unihockey, nicht mit dem Simulator. */
    @Test
    void withoutTestSupportSpeaksTheAppWithSwissUnihockey() {
        Object source = io.quarkus.arc.ClientProxy.unwrap(io.quarkus.arc.Arc.container()
                .instance(ch.stvbero.platform.core.swissunihockey.SwissUnihockeySource.class).get());
        org.junit.jupiter.api.Assertions.assertInstanceOf(ch.stvbero.platform.core.swissunihockey.SuApiSource.class,
                source);
        org.junit.jupiter.api.Assertions.assertEquals("https://api-v2.swissunihockey.ch/api",
                org.eclipse.microprofile.config.ConfigProvider.getConfig()
                        .getValue("stv.swiss-unihockey.url", String.class));
        org.junit.jupiter.api.Assertions.assertTrue(io.quarkus.arc.Arc.container()
                .select(ch.stvbero.platform.core.swissunihockey.SuSimulator.class).isUnsatisfied(),
                "Kein Simulator ohne Test-Hilfen");
    }

    @Test
    void testEndpointsMissingAlsoInTheOpenApi() {
        String specification = given().get("/q/openapi?format=json").then().statusCode(200).extract().asString();
        org.junit.jupiter.api.Assertions.assertFalse(specification.contains("/api/test/"), "Test-Pfade in der OpenAPI");
    }

    @Test
    void domainEndpointsStayProtected() {
        given().get("/api/me").then().statusCode(401);
    }
}
