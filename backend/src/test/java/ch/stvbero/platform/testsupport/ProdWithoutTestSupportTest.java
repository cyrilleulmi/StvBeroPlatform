package ch.stvbero.platform.testsupport;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * F-00, criterion 8: without test support (as in the prod profile, where {@code stv.test-support=false})
 * the test endpoints do not exist.
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
    void testEndpointsAreMissing() {
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

    /** F-07, criterion 2: in the prod profile the app talks to swiss unihockey, not to the simulator. */
    @Test
    void withoutTestSupportAppTalksToSwissUnihockey() {
        Object source = io.quarkus.arc.ClientProxy.unwrap(io.quarkus.arc.Arc.container()
                .instance(ch.stvbero.platform.swissunihockey.SwissUnihockeySource.class).get());
        Object current = io.quarkus.arc.ClientProxy.unwrap(
                ((ch.stvbero.platform.swissunihockey.SuSource) source).current());
        org.junit.jupiter.api.Assertions.assertInstanceOf(ch.stvbero.platform.swissunihockey.SuApiSource.class,
                current);
        // The free interface of the official web components (E-83)
        org.junit.jupiter.api.Assertions.assertEquals("https://wc.swissunihockey.ch",
                org.eclipse.microprofile.config.ConfigProvider.getConfig()
                        .getValue("stv.swiss-unihockey.url", String.class));
        org.junit.jupiter.api.Assertions.assertTrue(io.quarkus.arc.Arc.container()
                .select(ch.stvbero.platform.swissunihockey.SuSimulator.class).isUnsatisfied(),
                "No simulator without test support");
    }

    /**
     * F-19, criteria 8 and 9: in the prod profile e-mails go over SMTP and pushes to the push services, not to the
     * simulator; without configuration (no STV_SMTP_HOST, STV_MAIL_FROM, STV_VAPID_*) both channels are off.
     */
    @Test
    void withoutTestSupportRealChannelsWhichAreOffWithoutConfiguration() {
        Object email = io.quarkus.arc.ClientProxy.unwrap(io.quarkus.arc.Arc.container()
                .instance(ch.stvbero.platform.notification.EmailTransport.class).get());
        Object push = io.quarkus.arc.ClientProxy.unwrap(io.quarkus.arc.Arc.container()
                .instance(ch.stvbero.platform.notification.PushTransport.class).get());
        org.junit.jupiter.api.Assertions.assertInstanceOf(
                ch.stvbero.platform.notification.SmtpEmailTransport.class, email);
        org.junit.jupiter.api.Assertions.assertInstanceOf(
                ch.stvbero.platform.notification.WebPushTransport.class, push);
        org.junit.jupiter.api.Assertions.assertFalse(
                ((ch.stvbero.platform.notification.EmailTransport) email).available());
        org.junit.jupiter.api.Assertions.assertFalse(
                ((ch.stvbero.platform.notification.PushTransport) push).available());
        org.junit.jupiter.api.Assertions.assertNull(
                ((ch.stvbero.platform.notification.PushTransport) push).publicKey());
        org.junit.jupiter.api.Assertions.assertTrue(io.quarkus.arc.Arc.container()
                .select(ch.stvbero.platform.notification.DeliverySimulator.class).isUnsatisfied(),
                "No delivery simulator without test support");
        given().contentType("application/json").body("{\"failing\":[\"EMAIL\"]}")
                .put("/api/test/delivery").then().statusCode(404);
    }

    @Test
    void testEndpointsAreAlsoMissingFromOpenApi() {
        String specification = given().get("/q/openapi?format=json").then().statusCode(200).extract().asString();
        org.junit.jupiter.api.Assertions.assertFalse(specification.contains("/api/test/"), "Test paths in the OpenAPI");
    }

    @Test
    void domainEndpointsStayProtected() {
        given().get("/api/me").then().statusCode(401);
    }
}
