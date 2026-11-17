package ch.stvbero.platform.core.testsupport;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** F-00, criteria 8 and 10: test endpoints in the test profile, injected clock. */
@QuarkusTest
class TestEndpointsTest {

    @Inject
    Clock clock;

    @AfterEach
    void cleanUp() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    @Test
    void resetSetsClockToDefault() {
        given().contentType(ContentType.JSON).body("{\"now\":\"2030-01-01T12:00:00+01:00\"}")
                .put("/api/test/clock").then().statusCode(204);

        given().post("/api/test/reset").then().statusCode(204);

        given().get("/api/test/clock").then().statusCode(200)
                .body("now", equalTo("2026-11-16T06:00:00+01:00"))
                .body("fixed", equalTo(true));
    }

    @Test
    void settingClockChangesNowInWholeBackend() {
        given().contentType(ContentType.JSON).body("{\"now\":\"2026-11-16T19:00:00+01:00\"}")
                .put("/api/test/clock").then().statusCode(204);

        assertEquals(Instant.parse("2026-11-16T18:00:00Z"), clock.instant());
        given().get("/api/test/clock").then().body("now", equalTo("2026-11-16T19:00:00+01:00"));
    }

    @Test
    void releasingClockUsesRealTime() {
        given().delete("/api/test/clock").then().statusCode(204);

        given().get("/api/test/clock").then().statusCode(200).body("fixed", equalTo(false));
        long gap = Math.abs(clock.instant().toEpochMilli() - System.currentTimeMillis());
        assertEquals(true, gap < 5_000, "Gap to system time: " + gap);
    }

    @Test
    void settingClockWithoutInstantIsInvalid() {
        given().contentType(ContentType.JSON).body("{}").put("/api/test/clock").then().statusCode(400).contentType("application/problem+json").body("fields[0].field", org.hamcrest.Matchers.equalTo("now"));
    }

    @Test
    void loginSetsSessionCookie() {
        String cookie = given().contentType(ContentType.JSON).body("{\"loginKey\":\"petra\"}")
                .post("/api/test/login").then().statusCode(204)
                .header("Set-Cookie", containsString("stv_session="))
                .header("Set-Cookie", containsString("HttpOnly"))
                .extract().cookie("stv_session");

        given().cookie("stv_session", cookie).get("/api/me").then().statusCode(200)
                .body("firstName", equalTo("Petra"));
    }

    @Test
    void loginWithUnknownKeyGives404() {
        given().contentType(ContentType.JSON).body("{\"loginKey\":\"niemand\"}")
                .post("/api/test/login").then().statusCode(404)
                .contentType("application/problem+json")
                .body("detail", containsString("niemand"));
    }

    @Test
    void removedCurrentSeasonIsRestoredByReset() {
        given().delete("/api/test/current-season").then().statusCode(204);
        given().cookie("stv_session", alexLogin()).get("/api/seasons").then().statusCode(200)
                .body("$", hasSize(2))
                .body("findAll { it.current }", hasSize(0));

        given().post("/api/test/reset").then().statusCode(204);

        given().cookie("stv_session", alexLogin()).get("/api/seasons").then().statusCode(200)
                .body("findAll { it.current }.name", org.hamcrest.Matchers.contains("2026/27"));
    }

    private static String alexLogin() {
        return given().contentType(ContentType.JSON).body("{\"loginKey\":\"alex\"}")
                .post("/api/test/login").then().statusCode(204).extract().cookie("stv_session");
    }

    @Test
    void personsListsAllSeedPersonsSorted() {
        given().get("/api/test/persons").then().statusCode(200)
                .body("$", hasSize(17))
                .body("[0].lastName", equalTo("Admin"))
                .body("[0].loginKey", equalTo("alex"))
                .body("[0].admin", equalTo(true))
                .body("find { it.loginKey == 'petra' }.id", equalTo("20000000-0000-4000-8000-000000000002"));
    }
}
