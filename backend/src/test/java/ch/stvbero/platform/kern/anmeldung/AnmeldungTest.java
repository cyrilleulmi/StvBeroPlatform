package ch.stvbero.platform.kern.anmeldung;

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
public class AnmeldungTest {

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    /** Meldet als Seed-Person an und liefert das Sitzungs-Cookie. Auch für andere Tests. */
    public static String anmelden(String schluessel) {
        return given().contentType(ContentType.JSON).body("{\"schluessel\":\"" + schluessel + "\"}")
                .post("/api/test/anmelden").then().statusCode(204).extract().cookie("stv_sitzung");
    }

    @Test
    void ohneSitzungGibtEs401AlsProblemDetails() {
        given().get("/api/ich").then().statusCode(401)
                .contentType("application/problem+json")
                .body("status", equalTo(401))
                .body("title", equalTo("Nicht angemeldet"));
        given().get("/api/kacheln").then().statusCode(401);
    }

    @Test
    void ungueltigeSitzungGibt401() {
        given().cookie("stv_sitzung", "gibt-es-nicht").get("/api/ich").then().statusCode(401);
    }

    @Test
    void ichLiefertPersonUndAktuelleSaison() {
        given().cookie("stv_sitzung", anmelden("anna")).get("/api/ich").then().statusCode(200)
                .body("id", equalTo("20000000-0000-4000-8000-000000000005"))
                .body("vorname", equalTo("Anna"))
                .body("nachname", equalTo("Muster"))
                .body("admin", equalTo(false))
                .body("saison.name", equalTo("2026/27"))
                .body("saison.aktuell", equalTo(true))
                .body("saison.archiv", equalTo(false))
                .body("zugehoerigkeiten.team.name", contains("Team 1"))
                .body("zugehoerigkeiten[0].funktion", equalTo("SPIELERIN"));
    }

    /** Nachbesserung #13: ohne aktuelle Saison 409 als Problem Details (SAI-03), auch für Admins. */
    @Test
    void ichOhneAktuelleSaisonGibt409() {
        given().delete("/api/test/aktuelle-saison").then().statusCode(204);

        for (String schluessel : new String[] {"anna", "alex"}) {
            given().cookie("stv_sitzung", anmelden(schluessel)).get("/api/ich").then().statusCode(409)
                    .contentType("application/problem+json")
                    .body("status", equalTo(409))
                    .body("title", equalTo("Keine aktuelle Saison"))
                    .body("regel", equalTo("SAI-03"));
        }
    }

    @Test
    void ichOhneSitzungGibtAuchOhneAktuelleSaison401() {
        given().delete("/api/test/aktuelle-saison").then().statusCode(204);

        given().get("/api/ich").then().statusCode(401);
    }

    @Test
    void adminIstEigenschaftDerPerson() {
        given().cookie("stv_sitzung", anmelden("alex")).get("/api/ich").then().statusCode(200)
                .body("admin", equalTo(true))
                .body("zugehoerigkeiten", empty());
    }

    @Test
    void nf11KachelAnwesenheitIstRegistriert() {
        given().cookie("stv_sitzung", anmelden("anna")).get("/api/kacheln").then().statusCode(200)
                .body("id", contains("anwesenheit"))
                .body("[0].titel", equalTo("Anwesenheit"))
                .body("[0].route", equalTo("/termine"))
                .body("[0].icon", equalTo("calendar-check"))
                .body("[0].farbe", equalTo("primary"));
    }

    @Test
    void abmeldenBeendetDieSitzung() {
        String sitzung = anmelden("anna");

        given().cookie("stv_sitzung", sitzung).post("/api/abmelden").then().statusCode(204)
                .header("Set-Cookie", containsString("stv_sitzung=;"));

        given().cookie("stv_sitzung", sitzung).get("/api/ich").then().statusCode(401);
    }

    @Test
    void sitzungZeigtObJemandAngemeldetIst() {
        given().get("/api/sitzung").then().statusCode(200).body("angemeldet", equalTo(false));
        given().cookie("stv_sitzung", "gibt-es-nicht").get("/api/sitzung").then().statusCode(200)
                .body("angemeldet", equalTo(false));
        given().cookie("stv_sitzung", anmelden("anna")).get("/api/sitzung").then().statusCode(200)
                .body("angemeldet", equalTo(true));
    }

    /** Nachbesserung #26 K1: Abmelden ist idempotent, auch mit derselben Sitzung nacheinander. */
    @Test
    void zweimalAbmeldenMitDerselbenSitzungGibtZweimal204() {
        String sitzung = anmelden("anna");

        for (int i = 0; i < 2; i++) {
            given().cookie("stv_sitzung", sitzung).post("/api/abmelden").then().statusCode(204)
                    .header("Set-Cookie", containsString("stv_sitzung=;"));
        }
        given().cookie("stv_sitzung", sitzung).get("/api/ich").then().statusCode(401);
    }

    /** Nachbesserung #26 K1: gleichzeitige Abmeldungen mit demselben Cookie enden nie mit 500. */
    @Test
    void gleichzeitigAbmeldenMitDerselbenSitzungGibtImmer204() throws Exception {
        int parallel = 8;
        for (int runde = 0; runde < 5; runde++) {
            String sitzung = anmelden("anna");
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(parallel);
            try {
                List<Future<Response>> antworten = new ArrayList<>();
                for (int i = 0; i < parallel; i++) {
                    antworten.add(pool.submit(() -> {
                        start.await();
                        return given().cookie("stv_sitzung", sitzung).post("/api/abmelden");
                    }));
                }
                start.countDown();
                for (Future<Response> antwort : antworten) {
                    Response r = antwort.get(30, TimeUnit.SECONDS);
                    assertEquals(204, r.statusCode(), "Status einer gleichzeitigen Abmeldung");
                    assertTrue(r.header("Set-Cookie").contains("stv_sitzung=;"), "Cookie wird gelöscht");
                }
            } finally {
                pool.shutdownNow();
            }
            given().cookie("stv_sitzung", sitzung).get("/api/ich").then().statusCode(401);
        }
    }

    @Test
    void abmeldenOhneSitzungGehtAuch() {
        given().post("/api/abmelden").then().statusCode(204);
    }
}
