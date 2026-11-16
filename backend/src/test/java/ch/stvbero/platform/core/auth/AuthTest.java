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

/** F-00: Anmeldung über Sitzungs-Cookie, /api/ich, /api/kacheln, Abmelden. */
@QuarkusTest
public class AuthTest {

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    /** Meldet als Seed-Person an und liefert das Sitzungs-Cookie. Auch für andere Tests. */
    public static String login(String loginKey) {
        return given().contentType(ContentType.JSON).body("{\"loginKey\":\"" + loginKey + "\"}")
                .post("/api/test/login").then().statusCode(204).extract().cookie("stv_session");
    }

    @Test
    void withoutSessionGivesIt401AsProblemDetails() {
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
    void meDeliversPersonAndCurrentSeason() {
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

    /** Nachbesserung #13: ohne aktuelle Saison 409 als Problem Details (SAI-03), auch für Admins. */
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
    void meWithoutSessionGivesAlsoWithoutCurrentSeason401() {
        given().delete("/api/test/current-season").then().statusCode(204);

        given().get("/api/me").then().statusCode(401);
    }

    @Test
    void adminIsPropertyThePerson() {
        given().cookie("stv_session", login("alex")).get("/api/me").then().statusCode(200)
                .body("admin", equalTo(true))
                .body("memberships", empty());
    }

    @Test
    void nf11TileAttendanceIsRegistered() {
        given().cookie("stv_session", login("anna")).get("/api/tiles").then().statusCode(200)
                .body("id", contains("anwesenheit"))
                .body("[0].titel", equalTo("Anwesenheit"))
                .body("[0].route", equalTo("/termine"))
                .body("[0].icon", equalTo("calendar-check"))
                .body("[0].farbe", equalTo("primary"));
    }

    @Test
    void logoutEndedTheSession() {
        String session = login("anna");

        given().cookie("stv_session", session).post("/api/logout").then().statusCode(204)
                .header("Set-Cookie", containsString("stv_sitzung=;"));

        given().cookie("stv_session", session).get("/api/me").then().statusCode(401);
    }

    @Test
    void sessionShowsWhetherSomeoneSignedInIs() {
        given().get("/api/session").then().statusCode(200).body("signedIn", equalTo(false));
        given().cookie("stv_session", "gibt-es-nicht").get("/api/session").then().statusCode(200)
                .body("signedIn", equalTo(false));
        given().cookie("stv_session", login("anna")).get("/api/session").then().statusCode(200)
                .body("signedIn", equalTo(true));
    }

    /** Nachbesserung #26 K1: Abmelden ist idempotent, auch mit derselben Sitzung nacheinander. */
    @Test
    void twiceLogoutWithSameSessionGivesTwice204() {
        String session = login("anna");

        for (int i = 0; i < 2; i++) {
            given().cookie("stv_session", session).post("/api/logout").then().statusCode(204)
                    .header("Set-Cookie", containsString("stv_sitzung=;"));
        }
        given().cookie("stv_session", session).get("/api/me").then().statusCode(401);
    }

    /** Nachbesserung #26 K1: gleichzeitige Abmeldungen mit demselben Cookie enden nie mit 500. */
    @Test
    void concurrentLogoutWithSameSessionGivesAlways204() throws Exception {
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
                    Response r = response.get(30, TimeUnit.SECONDS);
                    assertEquals(204, r.statusCode(), "Status einer gleichzeitigen Abmeldung");
                    assertTrue(r.header("Set-Cookie").contains("stv_sitzung=;"), "Cookie wird gelöscht");
                }
            } finally {
                pool.shutdownNow();
            }
            given().cookie("stv_session", session).get("/api/me").then().statusCode(401);
        }
    }

    @Test
    void logoutWithoutSessionWorksAlso() {
        given().post("/api/logout").then().statusCode(204);
    }
}
