package ch.stvbero.platform.kern.person;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static ch.stvbero.platform.kern.anmeldung.AnmeldungTest.anmelden;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

/** F-01: Personenverwaltung (PTF-01, PTF-11, RR-04, RR-07). */
@QuarkusTest
class PersonenTest {

    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String ALEX = "20000000-0000-4000-8000-000000000001";

    String alex;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
        alex = anmelden("alex");
    }

    @Test
    void listeNachNachnameMitSuche() {
        given().cookie("stv_sitzung", alex).get("/api/personen").then().statusCode(200)
                .body("", hasSize(17))
                .body("[0].nachname", equalTo("Admin"))
                .body("[1].nachname", equalTo("Baumann"));
        given().cookie("stv_sitzung", alex).queryParam("suche", "kel").get("/api/personen").then().statusCode(200)
                .body("vorname", contains("Petra"));
        given().cookie("stv_sitzung", alex).queryParam("suche", "petra keller").get("/api/personen").then().statusCode(200)
                .body("id", contains(PETRA));
    }

    @Test
    void ptf11PersonAnlegenUndAendern() {
        String id = given().cookie("stv_sitzung", alex).contentType(ContentType.JSON)
                .body("{\"vorname\":\" Nora \",\"nachname\":\"Lang\",\"email\":\"\"}")
                .post("/api/personen").then().statusCode(201)
                .body("vorname", equalTo("Nora"))
                .body("email", nullValue())
                .body("admin", equalTo(false))
                .extract().path("id");

        given().cookie("stv_sitzung", alex).contentType(ContentType.JSON)
                .body("{\"vorname\":\"Nora\",\"nachname\":\"Lang-Meier\",\"email\":\"nora@example.ch\",\"admin\":false}")
                .put("/api/personen/" + id).then().statusCode(200)
                .body("nachname", equalTo("Lang-Meier"))
                .body("email", equalTo("nora@example.ch"));

        given().cookie("stv_sitzung", alex).get("/api/personen/" + id).then().statusCode(200)
                .body("nachname", equalTo("Lang-Meier"))
                .body("zugehoerigkeiten", hasSize(0));
    }

    @Test
    void personDetailZeigtZugehoerigkeitenAllerSaisons() {
        given().cookie("stv_sitzung", alex).get("/api/personen/" + PETRA).then().statusCode(200)
                .body("zugehoerigkeiten.team.name", contains("Team 1", "Team 2"))
                .body("zugehoerigkeiten.funktion", contains("COACH", "SPIELERIN"));
        given().cookie("stv_sitzung", alex).get("/api/personen/20000000-0000-4000-8000-000000000017").then().statusCode(200)
                .body("zugehoerigkeiten.saisonId", contains("60000000-0000-4000-8000-000000000001"));
    }

    @Test
    void ungueltigeEingabeGibt400MitFeldern() {
        given().cookie("stv_sitzung", alex).contentType(ContentType.JSON)
                .body("{\"vorname\":\"\",\"nachname\":\"Lang\",\"email\":\"kein-mail\"}")
                .post("/api/personen").then().statusCode(400)
                .body("felder.feld", contains("email", "vorname"));
    }

    @Test
    void rr04LetzterAdminBleibtAdmin() {
        given().cookie("stv_sitzung", alex).contentType(ContentType.JSON)
                .body("{\"vorname\":\"Alex\",\"nachname\":\"Admin\",\"admin\":false}")
                .put("/api/personen/" + ALEX).then().statusCode(409)
                .body("regel", equalTo("RR-04"));
    }

    @Test
    void unbekanntePersonGibt404() {
        given().cookie("stv_sitzung", alex).get("/api/personen/20000000-0000-4000-8000-000000000099").then().statusCode(404);
    }

    @Test
    void rr07NichtAdminsBekommen403() {
        String petra = anmelden("petra");
        given().cookie("stv_sitzung", petra).get("/api/personen").then().statusCode(403)
                .contentType("application/problem+json")
                .body("title", equalTo("Keine Berechtigung"));
        given().cookie("stv_sitzung", petra).get("/api/personen/" + PETRA).then().statusCode(403);
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body("{\"vorname\":\"X\",\"nachname\":\"Y\"}")
                .post("/api/personen").then().statusCode(403);
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body("{\"vorname\":\"Petra\",\"nachname\":\"Keller\",\"admin\":true}")
                .put("/api/personen/" + PETRA).then().statusCode(403);
        given().cookie("stv_sitzung", alex).get("/api/personen").then().statusCode(200)
                .body("findAll { it.admin }.vorname", hasItem("Alex"))
                .body("findAll { it.admin }", hasSize(1));
    }
}
