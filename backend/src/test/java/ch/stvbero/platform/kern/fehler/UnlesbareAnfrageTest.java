package ch.stvbero.platform.kern.fehler;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static ch.stvbero.platform.kern.anmeldung.AnmeldungTest.anmelden;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.startsWith;

/**
 * Nachbesserung #16: Nicht lesbare Anfragen (ungültiges JSON, falscher Typ, falsches Format) ergeben 400 mit
 * Problem Details und einem deutschen {@code detail}, wie alle anderen Fehler (docs/api/README.md).
 */
@QuarkusTest
class UnlesbareAnfrageTest {

    @Test
    void ungueltigesJsonAnTestEndpunktErgibtProblemDetails() {
        given().contentType(ContentType.JSON).body("{\"schluessel\": ")
                .post("/api/test/anmelden")
                .then().statusCode(400)
                .contentType(startsWith(Fehler.MEDIENTYP))
                .body("status", equalTo(400))
                .body("title", equalTo("Ungültige Anfrage"))
                .body("detail", equalTo("Die Anfrage konnte nicht gelesen werden. Bitte die Eingaben prüfen."));
    }

    @Test
    void ungueltigesJsonAnFachlichenEndpunktErgibtProblemDetails() {
        given().cookie("stv_sitzung", anmelden("alex")).contentType(ContentType.JSON).body("kein json")
                .post("/api/saisons")
                .then().statusCode(400)
                .contentType(startsWith(Fehler.MEDIENTYP))
                .body("status", equalTo(400))
                .body("detail", not(blankOrNullString()));
    }

    @Test
    void falschesDatumsformatNenntDasFeld() {
        given().cookie("stv_sitzung", anmelden("alex")).contentType(ContentType.JSON)
                .body("{\"name\":\"2027/28\",\"von\":\"irgendwann\",\"bis\":\"2028-06-30\"}")
                .post("/api/saisons")
                .then().statusCode(400)
                .contentType(startsWith(Fehler.MEDIENTYP))
                .body("status", equalTo(400))
                .body("felder[0].feld", equalTo("von"))
                .body("felder[0].meldung", equalTo("Ungültiger Wert."));
    }

    @Test
    void falscherTypErgibtProblemDetails() {
        given().cookie("stv_sitzung", anmelden("alex")).contentType(ContentType.JSON)
                .body("[1, 2]")
                .post("/api/saisons")
                .then().statusCode(400)
                .contentType(startsWith(Fehler.MEDIENTYP))
                .body("status", equalTo(400));
    }

    @Test
    void andereFehlerDesFrameworksBleibenUnveraendert() {
        given().get("/api/gibt-es-nicht").then().statusCode(404);
        given().contentType(ContentType.TEXT).body("alex").post("/api/test/anmelden").then().statusCode(415);
        given().delete("/api/saisons").then().statusCode(405);
    }
}
