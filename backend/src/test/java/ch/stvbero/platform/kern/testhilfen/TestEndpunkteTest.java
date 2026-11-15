package ch.stvbero.platform.kern.testhilfen;

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

/** F-00, Kriterien 8 und 10: Test-Endpunkte im Profil test, injizierte Uhr. */
@QuarkusTest
class TestEndpunkteTest {

    @Inject
    Clock clock;

    @AfterEach
    void aufraeumen() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    @Test
    void resetSetztUhrAufStandard() {
        given().contentType(ContentType.JSON).body("{\"jetzt\":\"2030-01-01T12:00:00+01:00\"}")
                .put("/api/test/uhr").then().statusCode(204);

        given().post("/api/test/reset").then().statusCode(204);

        given().get("/api/test/uhr").then().statusCode(200)
                .body("jetzt", equalTo("2026-11-16T06:00:00+01:00"))
                .body("fest", equalTo(true));
    }

    @Test
    void uhrSetzenVeraendertJetztImGanzenBackend() {
        given().contentType(ContentType.JSON).body("{\"jetzt\":\"2026-11-16T19:00:00+01:00\"}")
                .put("/api/test/uhr").then().statusCode(204);

        assertEquals(Instant.parse("2026-11-16T18:00:00Z"), clock.instant());
        given().get("/api/test/uhr").then().body("jetzt", equalTo("2026-11-16T19:00:00+01:00"));
    }

    @Test
    void uhrLoesenVerwendetEchteZeit() {
        given().delete("/api/test/uhr").then().statusCode(204);

        given().get("/api/test/uhr").then().statusCode(200).body("fest", equalTo(false));
        long abstand = Math.abs(clock.instant().toEpochMilli() - System.currentTimeMillis());
        assertEquals(true, abstand < 5_000, "Abstand zur Systemzeit: " + abstand);
    }

    @Test
    void uhrSetzenOhneZeitpunktIstUngueltig() {
        given().contentType(ContentType.JSON).body("{}").put("/api/test/uhr").then().statusCode(400).contentType("application/problem+json").body("felder[0].feld", org.hamcrest.Matchers.equalTo("jetzt"));
    }

    @Test
    void anmeldenSetztSitzungsCookie() {
        String cookie = given().contentType(ContentType.JSON).body("{\"schluessel\":\"petra\"}")
                .post("/api/test/anmelden").then().statusCode(204)
                .header("Set-Cookie", containsString("stv_sitzung="))
                .header("Set-Cookie", containsString("HttpOnly"))
                .extract().cookie("stv_sitzung");

        given().cookie("stv_sitzung", cookie).get("/api/ich").then().statusCode(200)
                .body("vorname", equalTo("Petra"));
    }

    @Test
    void anmeldenMitUnbekanntemSchluesselGibt404() {
        given().contentType(ContentType.JSON).body("{\"schluessel\":\"niemand\"}")
                .post("/api/test/anmelden").then().statusCode(404)
                .contentType("application/problem+json")
                .body("detail", containsString("niemand"));
    }

    @Test
    void personenListetAlleSeedPersonenSortiert() {
        given().get("/api/test/personen").then().statusCode(200)
                .body("$", hasSize(17))
                .body("[0].nachname", equalTo("Admin"))
                .body("[0].schluessel", equalTo("alex"))
                .body("[0].admin", equalTo(true))
                .body("find { it.schluessel == 'petra' }.id", equalTo("20000000-0000-4000-8000-000000000002"));
    }
}
