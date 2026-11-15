package ch.stvbero.platform.kern.termin;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static ch.stvbero.platform.kern.anmeldung.AnmeldungTest.anmelden;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/** F-03: Termine und Serien (TER-01 bis TER-11, RR-06, RR-07). IDs aus docs/testdaten.md. */
@QuarkusTest
class TermineTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String TEAM_3 = "10000000-0000-4000-8000-000000000003";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String T01 = "40000000-0000-4000-8000-000000000001";
    static final String T02 = "40000000-0000-4000-8000-000000000002";
    static final String T03 = "40000000-0000-4000-8000-000000000003";
    static final String T04 = "40000000-0000-4000-8000-000000000004";
    static final String T05 = "40000000-0000-4000-8000-000000000005";
    static final String T06 = "40000000-0000-4000-8000-000000000006";
    static final String T07 = "40000000-0000-4000-8000-000000000007";
    static final String T08 = "40000000-0000-4000-8000-000000000008";
    static final String SERIE_1 = "70000000-0000-4000-8000-000000000001";
    static final String SERIE_2 = "70000000-0000-4000-8000-000000000002";

    @Inject
    EntityManager em;

    String petra;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
        petra = anmelden("petra");
    }

    private static ValidatableResponse liste(String sitzung, String query) {
        return given().cookie("stv_sitzung", sitzung).get("/api/termine" + query).then().statusCode(200);
    }

    private static List<String> ids(String sitzung, String query) {
        return liste(sitzung, query).extract().jsonPath().getList("id");
    }

    private static String termin(String beginn, String ende, String... teams) {
        return """
                {"typ":"TRAINING","titel":"Zusatztraining","beginn":"%s","ende":"%s","teamIds":[%s],
                 "ort":{"name":"Turnhalle Dorf"},"hinweise":"Hallenschuhe"}"""
                .formatted(beginn, ende, String.join(",", List.of(teams).stream().map(t -> "\"" + t + "\"").toList()));
    }

    private static String serie(String wochentag, String beginnZeit, String von, String bis, String... teams) {
        return """
                {"typ":"TRAINING","titel":"Konditionstraining","teamIds":[%s],"wochentag":"%s","beginnZeit":"%s",
                 "endeZeit":"21:00","gueltigVon":"%s","gueltigBis":"%s","ort":{"name":"Sporthalle Nord"}}"""
                .formatted(String.join(",", List.of(teams).stream().map(t -> "\"" + t + "\"").toList()),
                        wochentag, beginnZeit, von, bis);
    }

    // ------------------------------------------------------------------ Kriterium 1 und 2

    @Test
    void k1SeedSerienUndTermine() {
        String anna = anmelden("anna");
        given().cookie("stv_sitzung", anna).get("/api/termine/" + T01).then().statusCode(200)
                .body("typ", equalTo("TRAINING"))
                .body("beginn", equalTo("2026-11-17T18:30:00+01:00"))
                .body("ende", equalTo("2026-11-17T20:00:00+01:00"))
                .body("teams.name", contains("Team 1", "Team 2"))
                .body("teams[0].meineFunktion", equalTo("SPIELERIN"))
                .body("teams[1].meineFunktion", nullValue())
                .body("ort.name", equalTo("Sporthalle Nord"))
                .body("serieId", equalTo(SERIE_1))
                .body("antwortfristStunden", equalTo(24))
                .body("antwortfrist", equalTo("2026-11-16T18:30:00+01:00"))
                .body("meineAntwort.status", equalTo("OFFEN"))
                // 12 verschiedene Spielerinnen, Lea zählt einmal (ANM-32); Antworten folgen mit F-04
                .body("zaehler.offen", equalTo(12))
                .body("darfBearbeiten", equalTo(false));
        given().cookie("stv_sitzung", anna).get("/api/termine/" + T04).then().statusCode(200)
                .body("typ", equalTo("SPIELTAG"))
                .body("treffpunkt.ort", equalTo("Parkplatz Sporthalle Nord"))
                .body("treffpunkt.zeit", equalTo("2026-11-22T08:00:00+01:00"))
                .body("hinweise", equalTo("Weisse Trikots mitnehmen"))
                .body("ort.mapsUrl", equalTo("https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf"))
                .body("spiele", empty())
                .body("meinAufgebot", equalTo("NOCH_KEIN_AUFGEBOT"));
        given().cookie("stv_sitzung", petra).get("/api/serien/" + SERIE_1).then().statusCode(200)
                .body("wochentag", equalTo("DIENSTAG"))
                .body("beginnZeit", equalTo("18:30"))
                .body("endeZeit", equalTo("20:00"))
                .body("gueltigVon", equalTo("2026-08-18"))
                .body("gueltigBis", equalTo("2027-04-27"))
                .body("teamIds", contains(TEAM_1, TEAM_2))
                .body("ausnahmen[0].grund", equalTo("Weihnachtsferien"))
                .body("ausnahmen[0].von", equalTo("2026-12-21"))
                .body("ausnahmen[0].bis", equalTo("2027-01-03"));
        // Weihnachtsferien: keine Trainings am 22.12. und 29.12. (TER-07)
        List<String> beginne = liste(petra, "?von=2026-12-20&bis=2027-01-06&team=" + TEAM_2)
                .extract().jsonPath().getList("beginn");
        assertThat(beginne, contains("2027-01-05T18:30:00+01:00"));
    }

    @Test
    void k2KommendeTermineDerEigenenTeamsChronologisch() {
        String anna = anmelden("anna");
        List<String> annas = ids(anna, "");
        assertThat(annas.subList(0, 4), contains(T01, T02, T04, T03));
        assertThat(annas, hasItems(T01, T02, T03, T04, T06));
        assertThat(annas, not(hasItem(T05)));
        assertThat(annas, not(hasItem(T07)));
        List<String> beginne = liste(anna, "").extract().jsonPath().getList("beginn");
        assertThat(beginne.stream().map(b -> Instant.from(java.time.OffsetDateTime.parse(b))).toList(),
                equalTo(beginne.stream().map(b -> Instant.from(java.time.OffsetDateTime.parse(b))).sorted().toList()));

        String tina = anmelden("tina");
        List<String> tinas = ids(tina, "");
        assertThat(tinas, not(hasItem(T01)));
        assertThat(tinas, hasItem(T06));
        liste(tina, "").body("[0].beginn", equalTo("2026-11-18T18:00:00+01:00"))
                .body("teams.id", everyItem(hasItem(TEAM_3)));
        // Alex ist in keinem Team: keine Termine
        liste(anmelden("alex"), "").body("$", empty());
    }

    @Test
    void k2NurMitgliederSehenEinenTermin() {
        given().cookie("stv_sitzung", anmelden("tina")).get("/api/termine/" + T01).then().statusCode(403)
                .contentType("application/problem+json");
        given().cookie("stv_sitzung", anmelden("alex")).get("/api/termine/" + T01).then().statusCode(200);
        given().cookie("stv_sitzung", petra).get("/api/termine/" + UUID.randomUUID()).then().statusCode(404);
        given().get("/api/termine").then().statusCode(401);
        given().cookie("stv_sitzung", anmelden("tina")).get("/api/serien/" + SERIE_1).then().statusCode(403);
    }

    // ------------------------------------------------------------------ Kriterium 3

    @Test
    void k3TerminFuerZweiTeamsErscheintBeiMitgliedernBeider() {
        String id = given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body(termin("2026-11-20T18:00:00+01:00", "2026-11-20T19:30:00+01:00", TEAM_1, TEAM_2))
                .post("/api/termine").then().statusCode(201)
                .body("titel", equalTo("Zusatztraining"))
                .body("teams.id", contains(TEAM_1, TEAM_2))
                .body("hinweise", equalTo("Hallenschuhe"))
                .body("status", equalTo("GEPLANT"))
                .body("serieId", nullValue())
                .body("antwortfristStunden", equalTo(24))
                .body("darfBearbeiten", equalTo(true))
                .extract().path("id");
        assertThat(ids(anmelden("anna"), ""), hasItem(id));   // Team 1
        assertThat(ids(anmelden("eva"), ""), hasItem(id));    // Team 2
        assertThat(ids(anmelden("marc"), ""), hasItem(id));   // Coach Team 2
        assertThat(ids(anmelden("tina"), ""), not(hasItem(id)));
        // Marc (Coach von Team 2) darf den gemeinsamen Termin bearbeiten (RR-06)
        given().cookie("stv_sitzung", anmelden("marc")).get("/api/termine/" + id).then().body("darfBearbeiten", equalTo(true));
    }

    @Test
    void k3NurCoachesEinesBeteiligtenTeamsErfassen() {
        String body = termin("2026-11-20T18:00:00+01:00", "2026-11-20T19:30:00+01:00", TEAM_1);
        given().cookie("stv_sitzung", anmelden("marc")).contentType(ContentType.JSON).body(body)
                .post("/api/termine").then().statusCode(403).body("regel", equalTo("RR-07"));
        given().cookie("stv_sitzung", anmelden("anna")).contentType(ContentType.JSON).body(body)
                .post("/api/termine").then().statusCode(403);
        // Petra ist in Team 2 nur Spielerin
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body(termin("2026-11-20T18:00:00+01:00", "2026-11-20T19:30:00+01:00", TEAM_2))
                .post("/api/termine").then().statusCode(403);
        given().cookie("stv_sitzung", anmelden("alex")).contentType(ContentType.JSON).body(body)
                .post("/api/termine").then().statusCode(201);
        given().cookie("stv_sitzung", anmelden("anna")).contentType(ContentType.JSON).body(body)
                .put("/api/termine/" + T01).then().statusCode(403);
        given().cookie("stv_sitzung", anmelden("anna")).contentType(ContentType.JSON).body("{\"grund\":\"x\"}")
                .post("/api/termine/" + T01 + "/absagen").then().statusCode(403);
        given().cookie("stv_sitzung", anmelden("ruth")).contentType(ContentType.JSON)
                .body(serie("MONTAG", "18:00", "2026-11-23", "2026-12-21", TEAM_1))
                .post("/api/serien").then().statusCode(403);
        given().cookie("stv_sitzung", anmelden("ruth")).contentType(ContentType.JSON)
                .body(serie("DIENSTAG", "18:30", "2026-08-18", "2027-04-27", TEAM_3))
                .put("/api/serien/" + SERIE_1).then().statusCode(403);
        given().cookie("stv_sitzung", anmelden("ruth")).contentType(ContentType.JSON)
                .body("{\"von\":\"2026-12-01\",\"bis\":\"2026-12-02\",\"grund\":\"x\"}")
                .post("/api/serien/" + SERIE_1 + "/ausnahmen").then().statusCode(403);
    }

    @Test
    void k3EingabenWerdenGeprueft() {
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body(termin("2026-11-20T19:30:00+01:00", "2026-11-20T18:00:00+01:00", TEAM_1))
                .post("/api/termine").then().statusCode(400).body("felder[0].feld", equalTo("ende"));
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body(termin("2026-11-10T18:00:00+01:00", "2026-11-10T19:30:00+01:00", TEAM_1))
                .post("/api/termine").then().statusCode(400);
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body(termin("2026-11-20T18:00:00+01:00", "2026-11-20T19:30:00+01:00", UUID.randomUUID().toString()))
                .post("/api/termine").then().statusCode(400).body("felder[0].feld", equalTo("teamIds"));
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body(termin("2026-11-20T18:00:00+01:00", "2026-11-20T19:30:00+01:00"))
                .post("/api/termine").then().statusCode(400);
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body(termin("2026-11-20T18:00:00+01:00", "2026-11-20T19:30:00+01:00", TEAM_1, TEAM_2)
                        .replace("TRAINING", "SPIELTAG"))
                .post("/api/termine").then().statusCode(400).body("felder[0].feld", equalTo("teamIds"));
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body(termin("2026-11-20T18:00:00+01:00", "2026-11-20T19:30:00+01:00", TEAM_1)
                        .replace("\"name\":\"Turnhalle Dorf\"", "\"name\":\"Halle\",\"mapsUrl\":\"http://x.ch\""))
                .post("/api/termine").then().statusCode(400);
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body(serie("MONTAG", "18:00", "2026-11-23", "2027-12-21", TEAM_1))
                .post("/api/serien").then().statusCode(400).body("felder[0].feld", equalTo("gueltigBis"));
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body(serie("MONTAG", "21:30", "2026-11-23", "2026-12-21", TEAM_1))
                .post("/api/serien").then().statusCode(400).body("felder[0].feld", equalTo("endeZeit"));
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body(serie("MONTAG", "18 Uhr", "2026-11-23", "2026-12-21", TEAM_1))
                .post("/api/serien").then().statusCode(400);
    }

    // ------------------------------------------------------------------ Kriterium 4

    @Test
    void k4SerieErzeugtTerminFuerJedeWoche() {
        String id = given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body(serie("MONTAG", "19:30", "2026-11-16", "2026-12-21", TEAM_1))
                .post("/api/serien").then().statusCode(201)
                .body("wochentag", equalTo("MONTAG"))
                .body("ausnahmen", empty())
                .body("antwortfristStunden", equalTo(24))
                .extract().path("id");
        List<String> beginne = liste(anmelden("anna"), "?bis=2027-01-31").extract().jsonPath()
                .getList("findAll { it.serieId == '" + id + "' }.beginn");
        assertThat(beginne, contains("2026-11-16T19:30:00+01:00", "2026-11-23T19:30:00+01:00",
                "2026-11-30T19:30:00+01:00", "2026-12-07T19:30:00+01:00", "2026-12-14T19:30:00+01:00",
                "2026-12-21T19:30:00+01:00"));
    }

    @Test
    void k4SerieErzeugtKeineVergangenenTermine() {
        String id = given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body(serie("MONTAG", "05:00", "2026-11-02", "2026-11-30", TEAM_1).replace("21:00", "06:00"))
                .post("/api/serien").then().statusCode(201).extract().path("id");
        List<String> beginne = liste(petra, "").extract().jsonPath().getList("findAll { it.serieId == '" + id + "' }.beginn");
        assertThat(beginne, contains("2026-11-23T05:00:00+01:00", "2026-11-30T05:00:00+01:00"));
    }

    // ------------------------------------------------------------------ Kriterium 5

    @Test
    void k5AusnahmeEntferntTermineOhneAntworten() {
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body("{\"von\":\"2026-11-30\",\"bis\":\"2026-12-06\",\"grund\":\"Hallenreinigung\"}")
                .post("/api/serien/" + SERIE_1 + "/ausnahmen").then().statusCode(201)
                .body("ausnahmen.grund", contains("Hallenreinigung", "Weihnachtsferien"));
        List<String> beginne = liste(petra, "?von=2026-11-28&bis=2026-12-09&typ=TRAINING").extract().jsonPath()
                .getList("findAll { it.serieId == '" + SERIE_1 + "' }.beginn");
        assertThat(beginne, contains("2026-12-08T18:30:00+01:00"));
    }

    @Test
    void k5AusnahmeSagtTermineMitAntwortenAb() {
        // Antworten gibt es ab F-04; hier direkt in der Datenbank
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                        insert into antwort (termin_id, person_id, status, geaendert_am) values (?1, ?2, 'DABEI', now())""")
                .setParameter(1, UUID.fromString(T01)).setParameter(2, UUID.fromString(ANNA)).executeUpdate());
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body("{\"von\":\"2026-11-17\",\"bis\":\"2026-11-19\",\"grund\":\"Hallensperre\"}")
                .post("/api/serien/" + SERIE_1 + "/ausnahmen").then().statusCode(201);
        given().cookie("stv_sitzung", petra).get("/api/termine/" + T01).then().statusCode(200)
                .body("status", equalTo("ABGESAGT"))
                .body("absagegrund", equalTo("Hallensperre"));
        given().cookie("stv_sitzung", anmelden("anna")).get("/api/termine/" + T01).then()
                .body("meineAntwort.status", equalTo("DABEI"))
                .body("zaehler.dabei", equalTo(1));
        // Andere Serien sind nicht betroffen
        given().cookie("stv_sitzung", petra).get("/api/termine/" + T02).then().body("status", equalTo("GEPLANT"));
    }

    @Test
    void k5AusnahmeBisVorVonIstUngueltig() {
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body("{\"von\":\"2026-12-06\",\"bis\":\"2026-11-30\",\"grund\":\"x\"}")
                .post("/api/serien/" + SERIE_1 + "/ausnahmen").then().statusCode(400);
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body("{\"von\":\"2026-11-30\",\"bis\":\"2026-12-06\",\"grund\":\"x\"}")
                .post("/api/serien/" + UUID.randomUUID() + "/ausnahmen").then().statusCode(404);
    }

    // ------------------------------------------------------------------ Kriterium 6

    @Test
    void k6EinzelnenSerienterminVerschieben() {
        String body = """
                {"typ":"TRAINING","titel":"Training","beginn":"2026-11-20T19:00:00+01:00","ende":"2026-11-20T20:30:00+01:00",
                 "teamIds":["%s"],"ort":{"name":"Sporthalle Nord"}}""".formatted(TEAM_1);
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(body)
                .put("/api/termine/" + T02).then().statusCode(200)
                .body("beginn", equalTo("2026-11-20T19:00:00+01:00"))
                .body("verschoben", equalTo(true))
                .body("serieId", equalTo(SERIE_2));
        // Die Serie bleibt unverändert, die anderen Termine auch
        given().cookie("stv_sitzung", petra).get("/api/serien/" + SERIE_2).then()
                .body("wochentag", equalTo("DONNERSTAG")).body("beginnZeit", equalTo("19:00"));
        List<String> beginne = liste(petra, "?von=2026-11-19&bis=2026-11-27&team=" + TEAM_1 + "&typ=TRAINING")
                .extract().jsonPath().getList("findAll { it.serieId == '" + SERIE_2 + "' }.beginn");
        assertThat(beginne, contains("2026-11-20T19:00:00+01:00", "2026-11-26T19:00:00+01:00"));

        // Spätere Änderung der Serie übernimmt der verschobene Termin nicht (TER-08)
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body(serie("DONNERSTAG", "19:30", "2026-08-20", "2027-04-29", TEAM_1).replace("21:00", "21:00"))
                .put("/api/serien/" + SERIE_2).then().statusCode(200).body("beginnZeit", equalTo("19:30"));
        given().cookie("stv_sitzung", petra).get("/api/termine/" + T02).then()
                .body("beginn", equalTo("2026-11-20T19:00:00+01:00"));
        beginne = liste(petra, "?von=2026-11-19&bis=2026-12-04&team=" + TEAM_1 + "&typ=TRAINING")
                .extract().jsonPath().getList("findAll { it.serieId == '" + SERIE_2 + "' }.beginn");
        assertThat(beginne, contains("2026-11-20T19:00:00+01:00", "2026-11-26T19:30:00+01:00", "2026-12-03T19:30:00+01:00"));
        // Vergangene Termine bleiben unverändert
        given().cookie("stv_sitzung", petra).get("/api/termine?ansicht=vergangen&team=" + TEAM_1 + "&typ=TRAINING")
                .then().body("findAll { it.serieId == '" + SERIE_2 + "' }[0].beginn", equalTo("2026-11-12T19:00:00+01:00"));
    }

    @Test
    void ter08SerieAufAnderenWochentag() {
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body(serie("FREITAG", "19:00", "2026-08-20", "2026-12-31", TEAM_1))
                .put("/api/serien/" + SERIE_2).then().statusCode(200);
        List<String> beginne = liste(petra, "?bis=2026-12-31&team=" + TEAM_1 + "&typ=TRAINING")
                .extract().jsonPath().getList("findAll { it.serieId == '" + SERIE_2 + "' }.beginn");
        assertThat(beginne, contains("2026-11-20T19:00:00+01:00", "2026-11-27T19:00:00+01:00",
                "2026-12-04T19:00:00+01:00", "2026-12-11T19:00:00+01:00", "2026-12-18T19:00:00+01:00",
                "2026-12-25T19:00:00+01:00"));
        given().cookie("stv_sitzung", petra).get("/api/termine/" + T02).then().statusCode(404);
    }

    // ------------------------------------------------------------------ Kriterium 7

    @Test
    void k7AbgesagterTerminMitGrund() {
        given().cookie("stv_sitzung", anmelden("anna")).get("/api/termine/" + T03).then().statusCode(200)
                .body("status", equalTo("ABGESAGT"))
                .body("absagegrund", equalTo("Halle besetzt"))
                .body("darfBearbeiten", equalTo(false));
        given().cookie("stv_sitzung", petra).get("/api/termine/" + T03).then().body("darfBearbeiten", equalTo(false));
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body("{\"grund\":\"Nochmals\"}")
                .post("/api/termine/" + T03 + "/absagen").then().statusCode(409).body("regel", equalTo("TER-06"));
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body(termin("2026-11-24T18:30:00+01:00", "2026-11-24T20:00:00+01:00", TEAM_1))
                .put("/api/termine/" + T03).then().statusCode(409);
    }

    @Test
    void ter06TerminAbsagen() {
        // Marc ist Coach von Team 2 und darf den gemeinsamen Termin absagen (RR-06)
        given().cookie("stv_sitzung", anmelden("marc")).contentType(ContentType.JSON).body("{\"grund\":\"  Halle gesperrt \"}")
                .post("/api/termine/" + T01 + "/absagen").then().statusCode(200)
                .body("status", equalTo("ABGESAGT")).body("absagegrund", equalTo("Halle gesperrt"));
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body("{\"grund\":\"\"}")
                .post("/api/termine/" + T02 + "/absagen").then().statusCode(400);
        // Vergangene Termine lassen sich nicht absagen
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body("{\"grund\":\"x\"}")
                .post("/api/termine/" + T07 + "/absagen").then().statusCode(409);
    }

    // ------------------------------------------------------------------ Kriterium 8

    @Test
    void k8AntwortfristProTerminUndSerie() {
        String body = termin("2026-11-20T18:00:00+01:00", "2026-11-20T19:30:00+01:00", TEAM_1)
                .replace("\"hinweise\"", "\"antwortfristStunden\":48,\"hinweise\"");
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(body)
                .post("/api/termine").then().statusCode(201)
                .body("antwortfristStunden", equalTo(48))
                .body("antwortfrist", equalTo("2026-11-18T18:00:00+01:00"));
        String serie = given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body(serie("MONTAG", "19:30", "2026-11-16", "2026-11-30", TEAM_1)
                        .replace("\"ort\"", "\"antwortfristStunden\":2,\"ort\""))
                .post("/api/serien").then().statusCode(201).body("antwortfristStunden", equalTo(2))
                .extract().path("id");
        liste(petra, "?bis=2026-11-16").body("find { it.serieId == '" + serie + "' }.antwortfrist",
                equalTo("2026-11-16T17:30:00+01:00"));
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body(body.replace("48", "-1")).post("/api/termine").then().statusCode(400);
    }

    // ------------------------------------------------------------------ Kriterium 9

    @Test
    void k9FilterTeamTypUndVergangen() {
        // Termin 06 gehört auch zu Team 3; Petra ist dort nicht Mitglied, sieht ihn aber über Team 1 und 2
        assertThat(ids(petra, "?team=" + TEAM_3), contains(T06));
        assertThat(ids(petra, "?typ=SPIELTAG"), contains(T04, T05));
        assertThat(ids(petra, "?typ=ANLASS&team=" + TEAM_1), contains(T06));
        List<String> vergangen = ids(anmelden("anna"), "?ansicht=vergangen");
        assertThat(vergangen.get(1), equalTo(T07));
        assertThat(vergangen, not(hasItem(T08)));
        assertThat(ids(anmelden("vera"), "?ansicht=vergangen"), containsInAnyOrder(T08));
        // Vergangene absteigend: Do 12.11. (Serie 02), Di 10.11. (Termin 07), Do 05.11.
        liste(anmelden("anna"), "?ansicht=vergangen").body("[0].beginn", equalTo("2026-11-12T19:00:00+01:00"))
                .body("[1].beginn", equalTo("2026-11-10T18:30:00+01:00"))
                .body("[2].beginn", equalTo("2026-11-05T19:00:00+01:00"));
        given().cookie("stv_sitzung", petra).get("/api/termine?ansicht=morgen").then().statusCode(400);
    }
}
