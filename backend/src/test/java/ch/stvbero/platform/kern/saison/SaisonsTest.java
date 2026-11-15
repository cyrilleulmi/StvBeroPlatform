package ch.stvbero.platform.kern.saison;

import ch.stvbero.platform.kern.fehler.Gleichzeitig;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ch.stvbero.platform.kern.anmeldung.AnmeldungTest.anmelden;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasSize;

/** F-01: Saisons anlegen, übernehmen und wechseln (SAI-02, SAI-03, SAI-04, SAI-06, RR-07). */
@QuarkusTest
class SaisonsTest {

    static final String SAISON_2025 = "60000000-0000-4000-8000-000000000001";
    static final String SAISON_2026 = "60000000-0000-4000-8000-000000000002";
    static final String NEUE = "{\"name\":\"2027/28\",\"von\":\"2027-07-01\",\"bis\":\"2028-06-30\",\"uebernehmenAus\":\"" + SAISON_2026 + "\"}";

    String alex;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
        alex = anmelden("alex");
    }

    @Test
    void sai01GleichzeitigesAnlegenErgibt409Statt500() {
        for (int runde = 0; runde < 5; runde++) {
            reset();
            List<Integer> status = Gleichzeitig.statuscodes(2, () -> given().cookie("stv_sitzung", alex)
                    .contentType(ContentType.JSON).body(NEUE).post("/api/saisons").statusCode());
            assertThat(status, contains(201, 409));
            given().cookie("stv_sitzung", alex).get("/api/saisons").then().statusCode(200)
                    .body("name", contains("2027/28", "2026/27", "2025/26"));
        }
    }

    @Test
    void listeNeuesteZuerstMitArchiv() {
        given().cookie("stv_sitzung", anmelden("anna")).get("/api/saisons").then().statusCode(200)
                .body("name", contains("2026/27", "2025/26"))
                .body("aktuell", contains(true, false))
                .body("archiv", contains(false, true));
    }

    @Test
    void sai04Sai06NeueSaisonMitUebernahmeAnlegenUndAktivieren() {
        String id = given().cookie("stv_sitzung", alex).contentType(ContentType.JSON).body(NEUE)
                .post("/api/saisons").then().statusCode(201)
                .body("name", equalTo("2027/28"))
                .body("aktuell", equalTo(false))
                .body("archiv", equalTo(false))
                .extract().path("id");

        given().cookie("stv_sitzung", alex).queryParam("saison", id).get("/api/teams").then().statusCode(200)
                .body("name", contains("Team 1", "Team 2", "Team 3"));
        given().cookie("stv_sitzung", alex).queryParam("saison", id)
                .get("/api/teams/10000000-0000-4000-8000-000000000001").then().statusCode(200)
                .body("coaches.vorname", contains("Petra"))
                // Linien und Zuweisungen werden mit übernommen (SAI-04, LIN-11)
                .body("einteilung.gruppen.name", contains("Linie 1", "Linie 2", "Goalies"))
                .body("einteilung.gruppen[0].spielerinnen.vorname", contains("Mia", "Lea", "Anna"))
                .body("einteilung.gruppen[0].id", not(equalTo("30000000-0000-4000-8000-000000000001")))
                .body("einteilung.gruppen[2].spielerinnen.vorname", contains("Jana"))
                .body("einteilung.ohneLinie.vorname", contains("Kim"));

        given().cookie("stv_sitzung", alex).post("/api/saisons/" + id + "/aktivieren").then().statusCode(204);

        given().cookie("stv_sitzung", alex).get("/api/saisons").then().statusCode(200)
                .body("name", contains("2027/28", "2026/27", "2025/26"))
                .body("aktuell", contains(true, false, false))
                .body("archiv", contains(false, true, true));
        String petra = anmelden("petra");
        given().cookie("stv_sitzung", petra).get("/api/ich").then().statusCode(200)
                .body("saison.name", equalTo("2027/28"))
                .body("zugehoerigkeiten.funktion", contains("COACH", "SPIELERIN"))
                .body("zugehoerigkeiten[0].saisonId", equalTo(id));
    }

    /** Nachbesserung #21: Die neue Saison übernimmt den aktuellen Namen; umbenennen dort ändert die Vorsaison nicht (SAI-02). */
    @Test
    void sai02TeamnameGiltProSaison() {
        String team1 = "10000000-0000-4000-8000-000000000001";
        given().cookie("stv_sitzung", alex).contentType(ContentType.JSON).body("{\"name\":\"Damen 1\"}")
                .put("/api/teams/" + team1).then().statusCode(200);
        String id = given().cookie("stv_sitzung", alex).contentType(ContentType.JSON).body(NEUE)
                .post("/api/saisons").then().statusCode(201).extract().path("id");

        given().cookie("stv_sitzung", alex).queryParam("saison", id).get("/api/teams").then().statusCode(200)
                .body("name", contains("Damen 1", "Team 2", "Team 3"));

        given().cookie("stv_sitzung", alex).queryParam("saison", id).contentType(ContentType.JSON)
                .body("{\"name\":\"Damen A\"}").put("/api/teams/" + team1).then().statusCode(200)
                .body("name", equalTo("Damen A"));
        given().cookie("stv_sitzung", alex).queryParam("saison", id).get("/api/teams/" + team1).then().statusCode(200)
                .body("name", equalTo("Damen A"));
        given().cookie("stv_sitzung", alex).get("/api/teams/" + team1).then().statusCode(200)
                .body("name", equalTo("Damen 1"));
        given().cookie("stv_sitzung", alex).queryParam("saison", SAISON_2025).get("/api/teams/" + team1).then().statusCode(200)
                .body("name", equalTo("Team 1"));
    }

    @Test
    void ohneUebernahmeIstDieNeueSaisonLeer() {
        String id = given().cookie("stv_sitzung", alex).contentType(ContentType.JSON)
                .body("{\"name\":\"2027/28\",\"von\":\"2027-07-01\",\"bis\":\"2028-06-30\"}")
                .post("/api/saisons").then().statusCode(201).extract().path("id");
        given().cookie("stv_sitzung", alex).queryParam("saison", id).get("/api/teams").then().statusCode(200)
                .body("", hasSize(0));
    }

    @Test
    void sai03AuchEineAlteSaisonKannWiederAktuellWerden() {
        given().cookie("stv_sitzung", alex).post("/api/saisons/" + SAISON_2025 + "/aktivieren").then().statusCode(204);
        given().cookie("stv_sitzung", alex).get("/api/saisons").then().statusCode(200)
                .body("aktuell", contains(false, true))
                .body("archiv", contains(false, false));
    }

    @Test
    void konflikteUndFehler() {
        given().cookie("stv_sitzung", alex).contentType(ContentType.JSON)
                .body("{\"name\":\"2026/27\",\"von\":\"2027-07-01\",\"bis\":\"2028-06-30\"}")
                .post("/api/saisons").then().statusCode(409);
        given().cookie("stv_sitzung", alex).contentType(ContentType.JSON)
                .body("{\"name\":\"2027\",\"von\":\"2027-01-01\",\"bis\":\"2027-12-31\"}")
                .post("/api/saisons").then().statusCode(409);
        given().cookie("stv_sitzung", alex).contentType(ContentType.JSON)
                .body("{\"name\":\"2027/28\",\"von\":\"2028-07-01\",\"bis\":\"2027-06-30\"}")
                .post("/api/saisons").then().statusCode(400).body("felder[0].feld", equalTo("bis"));
        given().cookie("stv_sitzung", alex).contentType(ContentType.JSON)
                .body("{\"name\":\"2027/28\",\"von\":\"2027-07-01\",\"bis\":\"2028-06-30\",\"uebernehmenAus\":\"60000000-0000-4000-8000-000000000099\"}")
                .post("/api/saisons").then().statusCode(404);
        given().cookie("stv_sitzung", alex).post("/api/saisons/60000000-0000-4000-8000-000000000099/aktivieren").then().statusCode(404);
    }

    @Test
    void rr07NurAdminsVerwaltenSaisons() {
        String marc = anmelden("marc");
        given().cookie("stv_sitzung", marc).contentType(ContentType.JSON).body(NEUE)
                .post("/api/saisons").then().statusCode(403);
        given().cookie("stv_sitzung", marc).post("/api/saisons/" + SAISON_2025 + "/aktivieren").then().statusCode(403);
        given().get("/api/saisons").then().statusCode(401);
    }
}
