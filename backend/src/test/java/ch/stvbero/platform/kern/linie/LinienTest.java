package ch.stvbero.platform.kern.linie;

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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/** F-02: Linien und Goalies (LIN-01 bis LIN-11, RR-03, RR-07). IDs aus docs/testdaten.md. */
@QuarkusTest
class LinienTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String TEAM_3 = "10000000-0000-4000-8000-000000000003";
    static final String T1_LINIE_1 = "30000000-0000-4000-8000-000000000001";
    static final String T1_LINIE_2 = "30000000-0000-4000-8000-000000000002";
    static final String T1_GOALIES = "30000000-0000-4000-8000-000000000003";
    static final String T2_LINIE_1 = "30000000-0000-4000-8000-000000000004";
    static final String T2_LINIE_2 = "30000000-0000-4000-8000-000000000005";
    static final String T3_GOALIES = "30000000-0000-4000-8000-000000000008";
    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String MARC = "20000000-0000-4000-8000-000000000003";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String LEA = "20000000-0000-4000-8000-000000000006";
    static final String KIM = "20000000-0000-4000-8000-000000000011";
    static final String SAISON_2025 = "60000000-0000-4000-8000-000000000001";

    String petra;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
        petra = anmelden("petra");
    }

    private static String linie(String name) {
        return "{\"name\":\"" + name + "\"}";
    }

    private static String linie(String name, int reihenfolge) {
        return "{\"name\":\"" + name + "\",\"reihenfolge\":" + reihenfolge + "}";
    }

    private static String zuweisung(String gruppeId) {
        return gruppeId == null ? "{\"gruppeId\":null}" : "{\"gruppeId\":\"" + gruppeId + "\"}";
    }

    @Test
    void k1SeedGruppenUndZuweisungen() {
        given().cookie("stv_sitzung", petra).get("/api/teams/" + TEAM_1 + "/gruppen").then().statusCode(200)
                .body("gruppen.id", contains(T1_LINIE_1, T1_LINIE_2, T1_GOALIES))
                .body("gruppen.art", contains("LINIE", "LINIE", "GOALIES"))
                .body("gruppen.reihenfolge", contains(1, 2, null))
                .body("gruppen[0].spielerinnen.vorname", contains("Mia", "Lea", "Anna"))
                .body("gruppen[1].spielerinnen.vorname", contains("Sara", "Nina"))
                .body("gruppen[2].spielerinnen.vorname", contains("Jana"))
                .body("ohneLinie.vorname", contains("Kim"));
        given().cookie("stv_sitzung", anmelden("ruth")).get("/api/teams/" + TEAM_3 + "/gruppen").then().statusCode(200)
                .body("gruppen.name", contains("Linie 1", "Goalies"))
                .body("gruppen[1].id", equalTo(T3_GOALIES))
                .body("gruppen[1].spielerinnen", empty())
                .body("ohneLinie", empty());
    }

    @Test
    void k2LinieAnlegenUmbenennenVerschiebenLoeschen() {
        String l3 = given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(linie("Linie 3"))
                .post("/api/teams/" + TEAM_1 + "/gruppen").then().statusCode(201)
                .body("art", equalTo("LINIE"))
                .body("reihenfolge", equalTo(3))
                .extract().path("id");

        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(linie("Powerplay", 1))
                .put("/api/teams/" + TEAM_1 + "/gruppen/" + l3).then().statusCode(200)
                .body("name", equalTo("Powerplay"))
                .body("reihenfolge", equalTo(1));
        given().cookie("stv_sitzung", petra).get("/api/teams/" + TEAM_1 + "/gruppen").then().statusCode(200)
                .body("gruppen.name", contains("Powerplay", "Linie 1", "Linie 2", "Goalies"))
                .body("gruppen.reihenfolge", contains(1, 2, 3, null));

        // Linie 2 löschen: Sara und Nina sind danach ohne Linie (LIN-06), Reihenfolge ohne Lücke
        given().cookie("stv_sitzung", petra).delete("/api/teams/" + TEAM_1 + "/gruppen/" + T1_LINIE_2).then().statusCode(204);
        given().cookie("stv_sitzung", petra).get("/api/teams/" + TEAM_1 + "/gruppen").then().statusCode(200)
                .body("gruppen.name", contains("Powerplay", "Linie 1", "Goalies"))
                .body("gruppen.reihenfolge", contains(1, 2, null))
                .body("ohneLinie.vorname", contains("Kim", "Sara", "Nina"));
        given().cookie("stv_sitzung", petra).delete("/api/teams/" + TEAM_1 + "/gruppen/" + T1_LINIE_2).then().statusCode(404);
    }

    @Test
    void k2NeueLinieAnBestimmterPosition() {
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(linie("Linie 0", 1))
                .post("/api/teams/" + TEAM_1 + "/gruppen").then().statusCode(201).body("reihenfolge", equalTo(1));
        given().cookie("stv_sitzung", petra).get("/api/teams/" + TEAM_1 + "/gruppen").then()
                .body("gruppen.name", contains("Linie 0", "Linie 1", "Linie 2", "Goalies"));
    }

    @Test
    void lin09NameProTeamEindeutig() {
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(linie("linie 2"))
                .post("/api/teams/" + TEAM_1 + "/gruppen").then().statusCode(409).body("regel", equalTo("LIN-09"));
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(linie("Linie 1"))
                .put("/api/teams/" + TEAM_1 + "/gruppen/" + T1_LINIE_2).then().statusCode(409).body("regel", equalTo("LIN-09"));
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(linie(""))
                .post("/api/teams/" + TEAM_1 + "/gruppen").then().statusCode(400).body("felder[0].feld", equalTo("name"));
        // Gleicher Name in einem anderen Team ist erlaubt
        given().cookie("stv_sitzung", anmelden("marc")).contentType(ContentType.JSON).body(linie("Linie 3"))
                .post("/api/teams/" + TEAM_2 + "/gruppen").then().statusCode(201);
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(linie("Linie 3"))
                .post("/api/teams/" + TEAM_1 + "/gruppen").then().statusCode(201);
    }

    @Test
    void doppelklickAufLinieAnlegenErgibt409Statt500() {
        for (int runde = 0; runde < 5; runde++) {
            reset();
            List<Integer> status = Gleichzeitig.statuscodes(2, () -> given().cookie("stv_sitzung", petra)
                    .contentType(ContentType.JSON).body(linie("Linie 3"))
                    .post("/api/teams/" + TEAM_1 + "/gruppen").statusCode());
            assertThat(status, contains(201, 409));
        }
    }

    @Test
    void k3ZuweisenUndOhneLinie() {
        // S14: Kim der Linie 2 zuweisen
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(zuweisung(T1_LINIE_2))
                .put("/api/teams/" + TEAM_1 + "/zuweisungen/" + KIM).then().statusCode(204);
        given().cookie("stv_sitzung", petra).get("/api/teams/" + TEAM_1 + "/gruppen").then()
                .body("gruppen[1].spielerinnen.vorname", contains("Kim", "Sara", "Nina"))
                .body("ohneLinie", empty());
        // zurück auf «ohne Linie»
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(zuweisung(null))
                .put("/api/teams/" + TEAM_1 + "/zuweisungen/" + KIM).then().statusCode(204);
        given().cookie("stv_sitzung", petra).get("/api/teams/" + TEAM_1 + "/gruppen").then()
                .body("ohneLinie.vorname", contains("Kim"));
        // Zur Goalie machen
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(zuweisung(T1_GOALIES))
                .put("/api/teams/" + TEAM_1 + "/zuweisungen/" + KIM).then().statusCode(204);
        given().cookie("stv_sitzung", petra).get("/api/teams/" + TEAM_1 + "/gruppen").then()
                .body("gruppen[2].spielerinnen.vorname", contains("Kim", "Jana"));
    }

    @Test
    void s15HoechstensEineGruppeProSpielerinUndTeam() {
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(zuweisung(T1_LINIE_2))
                .put("/api/teams/" + TEAM_1 + "/zuweisungen/" + ANNA).then().statusCode(204);
        given().cookie("stv_sitzung", petra).get("/api/teams/" + TEAM_1 + "/gruppen").then()
                .body("gruppen[0].spielerinnen.vorname", not(hasItem("Anna")))
                .body("gruppen[1].spielerinnen.vorname", contains("Sara", "Anna", "Nina"))
                .body("gruppen.spielerinnen.flatten().findAll { it.vorname == 'Anna' }", hasSize(1));
        given().cookie("stv_sitzung", anmelden("anna")).get("/api/ich").then().statusCode(200)
                .body("zugehoerigkeiten[0].gruppeId", equalTo(T1_LINIE_2));
    }

    @Test
    void lin03LeaHatInJedemTeamEineEigeneLinie() {
        given().cookie("stv_sitzung", anmelden("lea")).get("/api/ich").then().statusCode(200)
                .body("zugehoerigkeiten.team.name", contains("Team 1", "Team 2"))
                .body("zugehoerigkeiten.gruppeId", contains(T1_LINIE_1, T2_LINIE_2));
        // Änderung in Team 1 lässt Team 2 unverändert
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(zuweisung(T1_LINIE_2))
                .put("/api/teams/" + TEAM_1 + "/zuweisungen/" + LEA).then().statusCode(204);
        given().cookie("stv_sitzung", anmelden("lea")).get("/api/ich").then().statusCode(200)
                .body("zugehoerigkeiten.gruppeId", contains(T1_LINIE_2, T2_LINIE_2));
        // Eine Gruppe eines anderen Teams lässt sich nicht zuweisen
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(zuweisung(T2_LINIE_1))
                .put("/api/teams/" + TEAM_1 + "/zuweisungen/" + LEA).then().statusCode(404);
    }

    @Test
    void lin10NurSpielerinnenDesTeams() {
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(zuweisung(T1_LINIE_1))
                .put("/api/teams/" + TEAM_1 + "/zuweisungen/" + PETRA).then().statusCode(409)
                .body("regel", equalTo("LIN-10"));
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(zuweisung(T1_LINIE_1))
                .put("/api/teams/" + TEAM_1 + "/zuweisungen/" + MARC).then().statusCode(404);
    }

    @Test
    void lin10WechselZurCoachEntferntDieZuweisung() {
        String alex = anmelden("alex");
        given().cookie("stv_sitzung", alex).contentType(ContentType.JSON).body("{\"funktion\":\"COACH\"}")
                .put("/api/teams/" + TEAM_1 + "/mitglieder/" + ANNA).then().statusCode(200)
                .body("gruppeId", nullValue());
        given().cookie("stv_sitzung", alex).contentType(ContentType.JSON).body("{\"funktion\":\"SPIELERIN\"}")
                .put("/api/teams/" + TEAM_1 + "/mitglieder/" + ANNA).then().statusCode(200);
        given().cookie("stv_sitzung", petra).get("/api/teams/" + TEAM_1 + "/gruppen").then()
                .body("gruppen[0].spielerinnen.vorname", contains("Mia", "Lea"))
                .body("ohneLinie.vorname", containsInAnyOrder("Kim", "Anna"));
    }

    @Test
    void k6GoaliesGenauEinmalUndNichtLoeschbar() {
        given().cookie("stv_sitzung", petra).delete("/api/teams/" + TEAM_1 + "/gruppen/" + T1_GOALIES).then().statusCode(409)
                .body("regel", equalTo("LIN-05"));
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(linie("Torhüterinnen"))
                .put("/api/teams/" + TEAM_1 + "/gruppen/" + T1_GOALIES).then().statusCode(409)
                .body("regel", equalTo("LIN-05"));
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(linie("Goalies"))
                .post("/api/teams/" + TEAM_1 + "/gruppen").then().statusCode(409);
        given().cookie("stv_sitzung", petra).get("/api/teams/" + TEAM_1 + "/gruppen").then()
                .body("gruppen.findAll { it.art == 'GOALIES' }", hasSize(1));
    }

    @Test
    void k6NeuesTeamHatSofortGoalies() {
        String alex = anmelden("alex");
        String id = given().cookie("stv_sitzung", alex).contentType(ContentType.JSON).body("{\"name\":\"Team 4\"}")
                .post("/api/teams").then().statusCode(201).extract().path("id");
        given().cookie("stv_sitzung", alex).get("/api/teams/" + id + "/gruppen").then().statusCode(200)
                .body("gruppen.art", contains("GOALIES"))
                .body("gruppen.name", contains("Goalies"));
    }

    @Test
    void k7NurCoachesDesTeamsUndAdminsAendern() {
        String marc = anmelden("marc");
        String anna = anmelden("anna");
        // marc ist Coach von Team 2, nicht von Team 1: 403 (RR-07)
        given().cookie("stv_sitzung", marc).get("/api/teams/" + TEAM_1 + "/gruppen").then().statusCode(403);
        given().cookie("stv_sitzung", marc).contentType(ContentType.JSON).body(linie("Linie 3"))
                .post("/api/teams/" + TEAM_1 + "/gruppen").then().statusCode(403);
        given().cookie("stv_sitzung", marc).contentType(ContentType.JSON).body(linie("X"))
                .put("/api/teams/" + TEAM_1 + "/gruppen/" + T1_LINIE_1).then().statusCode(403);
        given().cookie("stv_sitzung", marc).delete("/api/teams/" + TEAM_1 + "/gruppen/" + T1_LINIE_1).then().statusCode(403);
        given().cookie("stv_sitzung", marc).contentType(ContentType.JSON).body(zuweisung(T1_LINIE_2))
                .put("/api/teams/" + TEAM_1 + "/zuweisungen/" + ANNA).then().statusCode(403);

        // anna ist Spielerin: darf lesen, nicht ändern
        given().cookie("stv_sitzung", anna).get("/api/teams/" + TEAM_1 + "/gruppen").then().statusCode(200)
                .body("gruppen", hasSize(3));
        given().cookie("stv_sitzung", anna).contentType(ContentType.JSON).body(zuweisung(T1_LINIE_2))
                .put("/api/teams/" + TEAM_1 + "/zuweisungen/" + ANNA).then().statusCode(403);
        given().cookie("stv_sitzung", anna).contentType(ContentType.JSON).body(linie("Linie 3"))
                .post("/api/teams/" + TEAM_1 + "/gruppen").then().statusCode(403);

        // petra ist in Team 2 nur Spielerin
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(zuweisung(T2_LINIE_1))
                .put("/api/teams/" + TEAM_2 + "/zuweisungen/" + LEA).then().statusCode(403);

        // Admin darf
        given().cookie("stv_sitzung", anmelden("alex")).contentType(ContentType.JSON).body(zuweisung(T1_LINIE_2))
                .put("/api/teams/" + TEAM_1 + "/zuweisungen/" + ANNA).then().statusCode(204);
        // Ohne Anmeldung: 401
        given().get("/api/teams/" + TEAM_1 + "/gruppen").then().statusCode(401);
    }

    @Test
    void unbekanntesTeamUndUnbekannteLinie() {
        String alex = anmelden("alex");
        given().cookie("stv_sitzung", alex).get("/api/teams/10000000-0000-4000-8000-000000000099/gruppen").then().statusCode(404);
        given().cookie("stv_sitzung", alex).delete("/api/teams/" + TEAM_1 + "/gruppen/" + T2_LINIE_1).then().statusCode(404);
    }

    @Test
    void sai02ArchivBehaeltSeineEinteilung() {
        given().cookie("stv_sitzung", anmelden("alex")).queryParam("saison", SAISON_2025)
                .get("/api/teams/" + TEAM_1).then().statusCode(200)
                .body("einteilung.gruppen.name", contains("Goalies"))
                .body("einteilung.ohneLinie.vorname", contains("Vera"));
    }

    @Test
    void lin02GleichzeitigeZuweisungErgibtKeinen500() {
        for (int runde = 0; runde < 5; runde++) {
            reset();
            List<Integer> status = Gleichzeitig.statuscodes(2, () -> given().cookie("stv_sitzung", petra)
                    .contentType(ContentType.JSON).body(zuweisung(T1_LINIE_2))
                    .put("/api/teams/" + TEAM_1 + "/zuweisungen/" + KIM).statusCode());
            assertThat(status, not(hasItem(500)));
            given().cookie("stv_sitzung", petra).get("/api/teams/" + TEAM_1 + "/gruppen").then()
                    .body("gruppen[1].spielerinnen.vorname", contains("Kim", "Sara", "Nina"));
        }
    }
}
