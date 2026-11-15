package ch.stvbero.platform.kern.team;

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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/** F-01: Teams und Teamzugehörigkeiten (PTF-03, PTF-04, PTF-07, PTF-12, SAI-02, RR-04, RR-07). */
@QuarkusTest
class TeamsTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String TEAM_3 = "10000000-0000-4000-8000-000000000003";
    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String TINA = "20000000-0000-4000-8000-000000000016";
    static final String VERA = "20000000-0000-4000-8000-000000000017";
    static final String SAISON_2025 = "60000000-0000-4000-8000-000000000001";

    String alex;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
        alex = anmelden("alex");
    }

    @Test
    void ptf12PetraSiehtBeideTeamsMitIhrerFunktion() {
        String petra = anmelden("petra");
        given().cookie("stv_sitzung", petra).get("/api/teams").then().statusCode(200)
                .body("name", contains("Team 1", "Team 2"))
                .body("meineFunktion", contains("COACH", "SPIELERIN"));
        given().cookie("stv_sitzung", petra).get("/api/ich").then().statusCode(200)
                .body("zugehoerigkeiten.team.name", contains("Team 1", "Team 2"))
                .body("zugehoerigkeiten.funktion", contains("COACH", "SPIELERIN"));
    }

    @Test
    void adminSiehtAlleTeamsDerSaison() {
        given().cookie("stv_sitzung", alex).get("/api/teams").then().statusCode(200)
                .body("name", contains("Team 1", "Team 2", "Team 3"))
                .body("[0].suTeamId", equalTo(900001))
                .body("[0].meineFunktion", nullValue());
        given().cookie("stv_sitzung", alex).queryParam("saison", SAISON_2025).get("/api/teams").then().statusCode(200)
                .body("name", contains("Team 1"));
    }

    @Test
    void ptf07CoachesUndSpielerinnenGetrennt() {
        String anna = anmelden("anna");
        given().cookie("stv_sitzung", anna).get("/api/teams/" + TEAM_1).then().statusCode(200)
                .body("name", equalTo("Team 1"))
                .body("meineFunktion", equalTo("SPIELERIN"))
                .body("coaches.vorname", contains("Petra"))
                .body("einteilung.gruppen", empty())
                .body("einteilung.ohneLinie", hasSize(7))
                .body("einteilung.ohneLinie.vorname", not(hasItem("Petra")))
                .body("einteilung.ohneLinie.nachname", contains("Baumann", "Brun", "Frei", "Graf", "Muster", "Roth", "Wyss"));
        given().cookie("stv_sitzung", anna).get("/api/teams/" + TEAM_2).then().statusCode(403);
    }

    @Test
    void ptf04GleichzeitigeAufnahmeErgibt409Statt500() {
        for (int runde = 0; runde < 5; runde++) {
            reset();
            List<Integer> status = Gleichzeitig.statuscodes(2, () -> given().cookie("stv_sitzung", alex)
                    .contentType(ContentType.JSON)
                    .body("{\"personId\":\"" + TINA + "\",\"funktion\":\"COACH\"}")
                    .post("/api/teams/" + TEAM_1 + "/mitglieder").statusCode());
            assertThat(status, contains(201, 409));
            given().cookie("stv_sitzung", alex).get("/api/teams/" + TEAM_1).then().statusCode(200)
                    .body("coaches.vorname.findAll { it == 'Tina' }", hasSize(1));
        }
    }

    @Test
    void ptf04ZweiteAufnahmeWirdAbgelehnt() {
        given().cookie("stv_sitzung", alex).contentType(ContentType.JSON)
                .body("{\"personId\":\"" + TINA + "\",\"funktion\":\"COACH\"}")
                .post("/api/teams/" + TEAM_1 + "/mitglieder").then().statusCode(201)
                .body("team.name", equalTo("Team 1"))
                .body("person.vorname", equalTo("Tina"))
                .body("funktion", equalTo("COACH"))
                .body("saisonId", equalTo("60000000-0000-4000-8000-000000000002"))
                .body("gruppeId", nullValue());

        given().cookie("stv_sitzung", alex).contentType(ContentType.JSON)
                .body("{\"personId\":\"" + TINA + "\",\"funktion\":\"SPIELERIN\"}")
                .post("/api/teams/" + TEAM_1 + "/mitglieder").then().statusCode(409)
                .body("regel", equalTo("PTF-04"))
                .body("detail", equalTo("Tina Bucher ist schon in diesem Team (Coach). Pro Team gibt es nur eine Funktion."));
    }

    @Test
    void ptf03PersonInMehrerenTeams() {
        given().cookie("stv_sitzung", alex).contentType(ContentType.JSON)
                .body("{\"personId\":\"" + ANNA + "\",\"funktion\":\"COACH\"}")
                .post("/api/teams/" + TEAM_3 + "/mitglieder").then().statusCode(201);
        given().cookie("stv_sitzung", anmelden("anna")).get("/api/teams").then().statusCode(200)
                .body("name", contains("Team 1", "Team 3"))
                .body("meineFunktion", contains("SPIELERIN", "COACH"));
    }

    @Test
    void funktionAendernUndEntfernen() {
        given().cookie("stv_sitzung", alex).contentType(ContentType.JSON).body("{\"funktion\":\"COACH\"}")
                .put("/api/teams/" + TEAM_1 + "/mitglieder/" + ANNA).then().statusCode(200)
                .body("funktion", equalTo("COACH"));
        given().cookie("stv_sitzung", alex).get("/api/teams/" + TEAM_1).then().statusCode(200)
                .body("coaches.vorname", contains("Petra", "Anna"))
                .body("einteilung.ohneLinie.vorname", not(hasItem("Anna")));

        given().cookie("stv_sitzung", alex).delete("/api/teams/" + TEAM_1 + "/mitglieder/" + ANNA).then().statusCode(204);
        given().cookie("stv_sitzung", alex).delete("/api/teams/" + TEAM_1 + "/mitglieder/" + ANNA).then().statusCode(404);
        given().cookie("stv_sitzung", anmelden("anna")).get("/api/teams").then().statusCode(200).body("", empty());
    }

    @Test
    void teamAnlegenUndUmbenennen() {
        String id = given().cookie("stv_sitzung", alex).contentType(ContentType.JSON).body("{\"name\":\"Team 4\"}")
                .post("/api/teams").then().statusCode(201)
                .body("name", equalTo("Team 4"))
                .extract().path("id");
        given().cookie("stv_sitzung", alex).contentType(ContentType.JSON).body("{\"name\":\"Damen 4\"}")
                .put("/api/teams/" + id).then().statusCode(200).body("name", equalTo("Damen 4"));
        given().cookie("stv_sitzung", alex).get("/api/teams").then().statusCode(200)
                .body("name", contains("Damen 4", "Team 1", "Team 2", "Team 3"));
        given().cookie("stv_sitzung", alex).contentType(ContentType.JSON).body("{\"name\":\"\"}")
                .post("/api/teams").then().statusCode(400).body("felder[0].feld", equalTo("name"));
    }

    @Test
    void sai02ArchivIstLesbarAberNichtAenderbar() {
        given().cookie("stv_sitzung", alex).queryParam("saison", SAISON_2025).get("/api/teams/" + TEAM_1).then().statusCode(200)
                .body("coaches", empty())
                .body("einteilung.ohneLinie.vorname", contains("Vera"));

        given().cookie("stv_sitzung", alex).queryParam("saison", SAISON_2025).contentType(ContentType.JSON)
                .body("{\"personId\":\"" + ANNA + "\",\"funktion\":\"SPIELERIN\"}")
                .post("/api/teams/" + TEAM_1 + "/mitglieder").then().statusCode(409)
                .body("regel", equalTo("SAI-02"));
        given().cookie("stv_sitzung", alex).queryParam("saison", SAISON_2025).contentType(ContentType.JSON)
                .body("{\"funktion\":\"COACH\"}")
                .put("/api/teams/" + TEAM_1 + "/mitglieder/" + VERA).then().statusCode(409);
        given().cookie("stv_sitzung", alex).queryParam("saison", SAISON_2025)
                .delete("/api/teams/" + TEAM_1 + "/mitglieder/" + VERA).then().statusCode(409);
        given().cookie("stv_sitzung", alex).queryParam("saison", SAISON_2025).contentType(ContentType.JSON)
                .body("{\"name\":\"Team X\"}").post("/api/teams").then().statusCode(409);
    }

    /** Nachbesserung #21: Umbenennen in der aktuellen Saison lässt das Archiv unverändert (SAI-02). */
    @Test
    void sai02UmbenennenVeraendertDasArchivNicht() {
        given().cookie("stv_sitzung", alex).contentType(ContentType.JSON).body("{\"name\":\"Damen 1\"}")
                .put("/api/teams/" + TEAM_1).then().statusCode(200).body("name", equalTo("Damen 1"));

        // Aktuelle Saison: neuer Name
        given().cookie("stv_sitzung", alex).get("/api/teams/" + TEAM_1).then().statusCode(200)
                .body("name", equalTo("Damen 1"));
        given().cookie("stv_sitzung", alex).get("/api/teams").then().statusCode(200)
                .body("name", contains("Damen 1", "Team 2", "Team 3"));
        // Archiv: weiterhin der damalige Name
        given().cookie("stv_sitzung", alex).queryParam("saison", SAISON_2025).get("/api/teams/" + TEAM_1).then().statusCode(200)
                .body("name", equalTo("Team 1"));
        given().cookie("stv_sitzung", alex).queryParam("saison", SAISON_2025).get("/api/teams").then().statusCode(200)
                .body("name", contains("Team 1"));
        // Personendetail: Zugehörigkeit im Archiv mit dem damaligen Namen (Vera war nur 2025/26 in Team 1)
        given().cookie("stv_sitzung", alex).get("/api/personen/" + VERA).then().statusCode(200)
                .body("zugehoerigkeiten.team.name", contains("Team 1"))
                .body("zugehoerigkeiten.saisonId", contains(SAISON_2025));
        // Mein Bereich zeigt den aktuellen Namen
        given().cookie("stv_sitzung", anmelden("petra")).get("/api/ich").then().statusCode(200)
                .body("zugehoerigkeiten.team.name", contains("Damen 1", "Team 2"));
    }

    @Test
    void sai02ArchivLaesstSichNichtUmbenennen() {
        given().cookie("stv_sitzung", alex).queryParam("saison", SAISON_2025).contentType(ContentType.JSON)
                .body("{\"name\":\"Team X\"}").put("/api/teams/" + TEAM_1).then().statusCode(409)
                .body("regel", equalTo("SAI-02"));
        given().cookie("stv_sitzung", alex).queryParam("saison", SAISON_2025).contentType(ContentType.JSON)
                .body("{\"name\":\"Team X\"}").put("/api/teams/" + TEAM_2).then().statusCode(404);
        given().cookie("stv_sitzung", alex).queryParam("saison", SAISON_2025).get("/api/teams/" + TEAM_1).then().statusCode(200)
                .body("name", equalTo("Team 1"));
    }

    @Test
    void teamDasEsInDerSaisonNichtGibt() {
        given().cookie("stv_sitzung", alex).queryParam("saison", SAISON_2025).get("/api/teams/" + TEAM_2).then().statusCode(404);
        given().cookie("stv_sitzung", alex).get("/api/teams/10000000-0000-4000-8000-000000000099").then().statusCode(404);
    }

    @Test
    void rr07NurAdminsVerwaltenTeams() {
        String petra = anmelden("petra");
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body("{\"name\":\"Team 9\"}")
                .post("/api/teams").then().statusCode(403);
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body("{\"name\":\"Team 9\"}")
                .put("/api/teams/" + TEAM_1).then().statusCode(403);
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body("{\"personId\":\"" + TINA + "\",\"funktion\":\"SPIELERIN\"}")
                .post("/api/teams/" + TEAM_1 + "/mitglieder").then().statusCode(403);
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body("{\"funktion\":\"COACH\"}")
                .put("/api/teams/" + TEAM_1 + "/mitglieder/" + ANNA).then().statusCode(403);
        given().cookie("stv_sitzung", petra).delete("/api/teams/" + TEAM_1 + "/mitglieder/" + ANNA).then().statusCode(403);
        given().cookie("stv_sitzung", petra).get("/api/teams/" + TEAM_3).then().statusCode(403);
    }
}
