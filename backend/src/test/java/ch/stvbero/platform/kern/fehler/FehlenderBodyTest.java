package ch.stvbero.platform.kern.fehler;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import static ch.stvbero.platform.kern.anmeldung.AnmeldungTest.anmelden;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.containsString;

/**
 * Nachbesserung #31: Eine Anfrage ohne Body an einen Endpunkt mit Pflicht-Body ergibt 400 mit Problem Details,
 * deutschem {@code detail} und ohne englische Meldung oder internen Parameternamen in {@code felder}
 * (docs/api/README.md «Fehler»).
 */
@QuarkusTest
class FehlenderBodyTest {

    @Test
    void saisonOhneBody() {
        pruefen(given().cookie("stv_sitzung", anmelden("alex")).contentType(ContentType.JSON)
                .post("/api/saisons").then());
    }

    @Test
    void testAnmeldenOhneBody() {
        pruefen(given().contentType(ContentType.JSON).post("/api/test/anmelden").then());
    }

    @Test
    void jsonNullAlsBody() {
        pruefen(given().cookie("stv_sitzung", anmelden("alex")).contentType(ContentType.JSON).body("null")
                .post("/api/saisons").then());
    }

    @Test
    void fehlendesFeldImBodyNenntWeiterhinDasFeld() {
        given().cookie("stv_sitzung", anmelden("alex")).contentType(ContentType.JSON)
                .body("{\"von\":\"2027-07-01\",\"bis\":\"2028-06-30\"}")
                .post("/api/saisons")
                .then().statusCode(400)
                .contentType(startsWith(Fehler.MEDIENTYP))
                .body("felder.feld", org.hamcrest.Matchers.hasItem("name"))
                .body("felder.meldung", org.hamcrest.Matchers.everyItem(not(containsString("must"))));
    }

    private static void pruefen(ValidatableResponse antwort) {
        antwort.statusCode(400)
                .contentType(startsWith(Fehler.MEDIENTYP))
                .body("status", equalTo(400))
                .body("title", equalTo("Ungültige Anfrage"))
                .body("detail", equalTo("Die Anfrage enthält keine Daten. Bitte die Eingaben prüfen."))
                .body("felder", anyOf(nullValue(), empty()))
                .body(not(containsString("must not be null")))
                .body(not(containsString("eingabe")));
    }
}
