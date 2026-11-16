package ch.stvbero.platform.core.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F-00: login via session cookie, /api/me, /api/tiles, logout. */
@QuarkusTest
public class AuthTest {

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    /** Logs in as a seed person and returns the session cookie. Also used by other tests. */
    public static String login(String loginKey) {
        return given().contentType(ContentType.JSON).body("{\"loginKey\":\"" + loginKey + "\"}")
                .post("/api/test/login").then().statusCode(204).extract().cookie("stv_session");
    }

    @Test
    void withoutSessionGives401AsProblemDetails() {
        given().get("/api/me").then().statusCode(401)
                .contentType("application/problem+json")
                .body("status", equalTo(401))
                .body("title", equalTo("Nicht angemeldet"));
        given().get("/api/tiles").then().statusCode(401);
    }

    @Test
    void invalidSessionGives401() {
        given().cookie("stv_session", "gibt-es-nicht").get("/api/me").then().statusCode(401);
    }

    @Test
    void meReturnsPersonAndCurrentSeason() {
        given().cookie("stv_session", login("anna")).get("/api/me").then().statusCode(200)
                .body("id", equalTo("20000000-0000-4000-8000-000000000005"))
                .body("firstName", equalTo("Anna"))
                .body("lastName", equalTo("Muster"))
                .body("admin", equalTo(false))
                .body("season.name", equalTo("2026/27"))
                .body("season.current", equalTo(true))
                .body("season.archived", equalTo(false))
                .body("memberships.team.name", contains("Team 1"))
                .body("memberships[0].teamRole", equalTo("PLAYER"));
    }

    /** Rework #13: without a current season 409 as Problem Details (SAI-03), also for admins. */
    @Test
    void meWithoutCurrentSeasonGives409() {
        given().delete("/api/test/current-season").then().statusCode(204);

        for (String loginKey : new String[] {"anna", "alex"}) {
            given().cookie("stv_session", login(loginKey)).get("/api/me").then().statusCode(409)
                    .contentType("application/problem+json")
                    .body("status", equalTo(409))
                    .body("title", equalTo("Keine aktuelle Saison"))
                    .body("rule", equalTo("SAI-03"));
        }
    }

    @Test
    void meWithoutSessionGives401EvenWithoutCurrentSeason() {
        given().delete("/api/test/current-season").then().statusCode(204);

        given().get("/api/me").then().statusCode(401);
    }

    @Test
    void adminIsAPropertyOfThePerson() {
        given().cookie("stv_session", login("alex")).get("/api/me").then().statusCode(200)
                .body("admin", equalTo(true))
                .body("memberships", empty());
    }

    @Test
    void nf11TileAttendanceIsRegistered() {
        given().cookie("stv_session", login("anna")).get("/api/tiles").then().statusCode(200)
                .body("id", contains("anwesenheit"))
                .body("[0].title", equalTo("Anwesenheit"))
                .body("[0].route", equalTo("/termine"))
                .body("[0].icon", equalTo("calendar-check"))
                .body("[0].color", equalTo("primary"));
    }

    @Test
    void logoutEndsTheSession() {
        String session = login("anna");

        given().cookie("stv_session", session).post("/api/logout").then().statusCode(204)
                .header("Set-Cookie", containsString("stv_session=;"));

        given().cookie("stv_session", session).get("/api/me").then().statusCode(401);
    }

    @Test
    void sessionShowsWhetherSomeoneIsSignedIn() {
        given().get("/api/session").then().statusCode(200).body("signedIn", equalTo(false));
        given().cookie("stv_session", "gibt-es-nicht").get("/api/session").then().statusCode(200)
                .body("signedIn", equalTo(false));
        given().cookie("stv_session", login("anna")).get("/api/session").then().statusCode(200)
                .body("signedIn", equalTo(true));
    }

    /** Rework #26 K1: logout is idempotent, also with the same session one after the other. */
    @Test
    void loggingOutTwiceWithSameSessionGives204Twice() {
        String session = login("anna");

        for (int i = 0; i < 2; i++) {
            given().cookie("stv_session", session).post("/api/logout").then().statusCode(204)
                    .header("Set-Cookie", containsString("stv_session=;"));
        }
        given().cookie("stv_session", session).get("/api/me").then().statusCode(401);
    }

    /** Rework #26 K1: concurrent logouts with the same cookie never end with 500. */
    @Test
    void concurrentLogoutWithSameSessionAlwaysGives204() throws Exception {
        int parallel = 8;
        for (int round = 0; round < 5; round++) {
            String session = login("anna");
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(parallel);
            try {
                List<Future<Response>> responses = new ArrayList<>();
                for (int i = 0; i < parallel; i++) {
                    responses.add(pool.submit(() -> {
                        start.await();
                        return given().cookie("stv_session", session).post("/api/logout");
                    }));
                }
                start.countDown();
                for (Future<Response> response : responses) {
                    Response result = response.get(30, TimeUnit.SECONDS);
                    assertEquals(204, result.statusCode(), "Status of a concurrent logout");
                    assertTrue(result.header("Set-Cookie").contains("stv_session=;"), "Cookie is deleted");
                }
            } finally {
                pool.shutdownNow();
            }
            given().cookie("stv_session", session).get("/api/me").then().statusCode(401);
        }
    }

    @Test
    void logoutWithoutSessionAlsoWorks() {
        given().post("/api/logout").then().statusCode(204);
    }
}
