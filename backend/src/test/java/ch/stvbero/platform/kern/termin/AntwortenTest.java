package ch.stvbero.platform.kern.termin;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static ch.stvbero.platform.kern.anmeldung.AnmeldungTest.anmelden;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/** F-04: An- und Abmeldung mit Übersicht (ANM-01 bis ANM-32, RR-02, RR-03, RR-06). IDs aus docs/testdaten.md. */
@QuarkusTest
class AntwortenTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String MARC = "20000000-0000-4000-8000-000000000003";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String LEA = "20000000-0000-4000-8000-000000000006";
    static final String MIA = "20000000-0000-4000-8000-000000000007";
    static final String NINA = "20000000-0000-4000-8000-000000000009";
    static final String EVA = "20000000-0000-4000-8000-000000000012";
    static final String TINA = "20000000-0000-4000-8000-000000000016";
    static final String T01 = "40000000-0000-4000-8000-000000000001";
    static final String T02 = "40000000-0000-4000-8000-000000000002";
    static final String T03 = "40000000-0000-4000-8000-000000000003";
    static final String T07 = "40000000-0000-4000-8000-000000000007";
    static final String GRUPPE_LINIE_1_TEAM_1 = "30000000-0000-4000-8000-000000000001";

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static ValidatableResponse uebersicht(String sitzung, String termin) {
        return given().cookie("stv_sitzung", sitzung).get("/api/termine/" + termin + "/uebersicht").then();
    }

    private static ValidatableResponse setzen(String sitzung, String termin, String person, String body) {
        return given().cookie("stv_sitzung", sitzung).contentType(ContentType.JSON).body(body)
                .put("/api/termine/" + termin + "/antworten/" + person).then();
    }

    private static void uhr(String jetzt) {
        given().contentType(ContentType.JSON).body("{\"jetzt\":\"" + jetzt + "\"}").put("/api/test/uhr").then().statusCode(204);
    }

    private static List<String> namen(JsonPath json, String pfad) {
        return json.getList(pfad + ".person.vorname");
    }

    // ------------------------------------------------------------------ Übersicht (S1)

    @Test
    void s1UebersichtVonTermin01() {
        JsonPath json = uebersicht(anmelden("petra"), T01).statusCode(200).extract().jsonPath();

        // ANM-23, ANM-32: Gesamttotal mit jeder Spielerin einmal (Lea zählt einmal)
        assertThat(json.getMap("total"), equalTo(Map.of("dabei", 7, "nichtDabei", 2, "vielleicht", 1, "offen", 2)));

        // ANM-20: nach Team, Teams nach Name
        assertThat(json.getList("teams.team.name"), contains("Team 1", "Team 2"));
        assertThat(json.getMap("teams[0].zaehler"), equalTo(Map.of("dabei", 5, "nichtDabei", 1, "vielleicht", 0, "offen", 1)));
        assertThat(json.getMap("teams[1].zaehler"), equalTo(Map.of("dabei", 3, "nichtDabei", 1, "vielleicht", 1, "offen", 1)));

        // ANM-21: Linien in ihrer Reihenfolge, dann Goalies, dann «Ohne Linie»; Personen nach Nachname
        assertThat(json.getList("teams[0].gruppen.name"), contains("Linie 1", "Linie 2", "Goalies", "Ohne Linie"));
        assertThat(json.getList("teams[0].gruppen.art"), contains("LINIE", "LINIE", "GOALIES", "OHNE_LINIE"));
        assertThat(json.getString("teams[0].gruppen[0].gruppeId"), equalTo(GRUPPE_LINIE_1_TEAM_1));
        assertThat(json.getString("teams[0].gruppen[3].gruppeId"), nullValue());
        assertThat(namen(json, "teams[0].gruppen[0].eintraege"), contains("Mia", "Lea", "Anna"));
        assertThat(json.getList("teams[0].gruppen[0].eintraege.antwort.status"), contains("NICHT_DABEI", "DABEI", "DABEI"));
        assertThat(namen(json, "teams[0].gruppen[1].eintraege"), contains("Sara", "Nina"));
        assertThat(json.getList("teams[0].gruppen[1].eintraege.antwort.status"), contains("DABEI", "OFFEN"));
        assertThat(namen(json, "teams[0].gruppen[2].eintraege"), contains("Jana"));
        assertThat(namen(json, "teams[0].gruppen[3].eintraege"), contains("Kim"));

        // ANM-31: Petra als Spielerin in Team 2, Lea in beiden Teams; leere Gruppen fehlen nicht hier, Team 2 hat keine «Ohne Linie»
        assertThat(json.getList("teams[1].gruppen.name"), contains("Linie 1", "Linie 2", "Goalies"));
        assertThat(namen(json, "teams[1].gruppen[0].eintraege"), contains("Petra", "Eva", "Lara"));
        assertThat(json.getList("teams[1].gruppen[0].eintraege.antwort.status"), contains("DABEI", "VIELLEICHT", "DABEI"));
        assertThat(namen(json, "teams[1].gruppen[1].eintraege"), contains("Lea", "Zoe"));
        assertThat(namen(json, "teams[1].gruppen[2].eintraege"), contains("Ella"));

        // ANM-22: Coach-Block mit Team, nicht unter den Spielerinnen von Team 1
        assertThat(namen(json, "coaches"), contains("Petra", "Marc"));
        assertThat(json.getList("coaches.team.name"), contains("Team 1", "Team 2"));
        assertThat(json.getList("coaches.antwort.status"), contains("DABEI", "NICHT_DABEI"));
    }

    @Test
    void anm21LeereGruppenFehlen() {
        // Termin 02 nur Team 1; nach S14 ist «Ohne Linie» leer
        String petra = anmelden("petra");
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON)
                .body("{\"gruppeId\":\"30000000-0000-4000-8000-000000000002\"}")
                .put("/api/teams/" + TEAM_1 + "/zuweisungen/20000000-0000-4000-8000-000000000011")
                .then().statusCode(204);
        JsonPath json = uebersicht(petra, T01).statusCode(200).extract().jsonPath();
        assertThat(json.getList("teams[0].gruppen.name"), contains("Linie 1", "Linie 2", "Goalies"));
        assertThat(namen(json, "teams[0].gruppen[1].eintraege"), contains("Kim", "Sara", "Nina"));
    }

    // ------------------------------------------------------------------ Kommentare (S4)

    @Test
    void s4KommentareNichtFuerSpielerinnen() {
        JsonPath json = uebersicht(anmelden("anna"), T01).statusCode(200).extract().jsonPath();
        List<Map<String, Object>> alle = json.getList("teams.gruppen.flatten().eintraege.flatten().antwort");
        assertThat(alle, everyItem(not(hasKey("kommentar"))));
        assertThat(json.getList("coaches.antwort"), everyItem(not(hasKey("kommentar"))));
    }

    @Test
    void s4KommentareFuerCoachNurImEigenenTeam() {
        JsonPath json = uebersicht(anmelden("petra"), T01).statusCode(200).extract().jsonPath();
        assertThat(json.getString("teams[0].gruppen[0].eintraege[0].antwort.kommentar"), equalTo("Krank"));
        // RR-06: Petra ist in Team 2 Spielerin, sieht Evas Kommentar dort nicht, auch nicht Marcs
        assertThat(json.getMap("teams[1].gruppen[0].eintraege[1].antwort"), not(hasKey("kommentar")));
        assertThat(json.getMap("coaches[1].antwort"), not(hasKey("kommentar")));

        JsonPath marc = uebersicht(anmelden("marc"), T01).statusCode(200).extract().jsonPath();
        assertThat(marc.getString("teams[1].gruppen[0].eintraege[1].antwort.kommentar"), equalTo("Komme evtl. später"));
        assertThat(marc.getString("coaches[1].antwort.kommentar"), equalTo("Ferien"));
        assertThat(marc.getMap("teams[0].gruppen[0].eintraege[0].antwort"), not(hasKey("kommentar")));
    }

    @Test
    void anm07EigenerKommentarUndAdminSiehtAlle() {
        JsonPath mia = uebersicht(anmelden("mia"), T01).statusCode(200).extract().jsonPath();
        assertThat(mia.getString("teams[0].gruppen[0].eintraege[0].antwort.kommentar"), equalTo("Krank"));
        assertThat(mia.getMap("teams[1].gruppen[0].eintraege[1].antwort"), not(hasKey("kommentar")));
        given().cookie("stv_sitzung", anmelden("mia")).get("/api/termine/" + T01).then().statusCode(200)
                .body("meineAntwort.kommentar", equalTo("Krank"));

        JsonPath alex = uebersicht(anmelden("alex"), T01).statusCode(200).extract().jsonPath();
        assertThat(alex.getString("teams[1].gruppen[0].eintraege[1].antwort.kommentar"), equalTo("Komme evtl. später"));
    }

    @Test
    void rr02UebersichtNurFuerMitgliederDerTeams() {
        uebersicht(anmelden("ruth"), T01).statusCode(403);
        uebersicht(anmelden("anna"), "40000000-0000-4000-8000-000000000099").statusCode(404);
    }

    // ------------------------------------------------------------------ Eigene Antwort (S2, S3, S5)

    @Test
    void s2AntwortMitEinemTap() {
        String nina = anmelden("nina");
        setzen(nina, T01, "ich", "{\"status\":\"DABEI\"}").statusCode(200)
                .body("status", equalTo("DABEI")).body("kurzfristig", equalTo(false))
                .body("geaendertAm", equalTo("2026-11-16T06:00:00+01:00"));
        JsonPath json = uebersicht(nina, T01).statusCode(200).extract().jsonPath();
        assertThat(json.getMap("teams[0].zaehler"), equalTo(Map.of("dabei", 6, "nichtDabei", 1, "vielleicht", 0, "offen", 0)));
        assertThat(json.getMap("total"), equalTo(Map.of("dabei", 8, "nichtDabei", 2, "vielleicht", 1, "offen", 1)));
        // ANM-09: bleibt nach Neuladen, auch in der Terminliste
        given().cookie("stv_sitzung", nina).get("/api/termine/" + T01).then().statusCode(200)
                .body("meineAntwort.status", equalTo("DABEI")).body("zaehler.dabei", equalTo(8));
    }

    @Test
    void anm09AntwortJederzeitAenderbar() {
        String anna = anmelden("anna");
        setzen(anna, T01, "ich", "{\"status\":\"NICHT_DABEI\",\"kommentar\":\"  Arzttermin \"}").statusCode(200)
                .body("status", equalTo("NICHT_DABEI")).body("kommentar", equalTo("Arzttermin"));
        setzen(anna, T01, "ich", "{\"status\":\"DABEI\"}").statusCode(200)
                .body("status", equalTo("DABEI")).body("$", not(hasKey("kommentar")));
    }

    @Test
    void s3VielleichtOhneKommentar() {
        String nina = anmelden("nina");
        setzen(nina, T01, "ich", "{\"status\":\"VIELLEICHT\"}").statusCode(422)
                .contentType("application/problem+json")
                .body("regel", equalTo("ANM-04")).body("detail", equalTo(AntwortRegeln.KOMMENTAR_PFLICHT))
                .body("felder[0].feld", equalTo("kommentar"));
        setzen(nina, T01, "ich", "{\"status\":\"VIELLEICHT\",\"kommentar\":\" \"}").statusCode(422);
        // Nichts gespeichert
        given().cookie("stv_sitzung", nina).get("/api/termine/" + T01).then().body("meineAntwort.status", equalTo("OFFEN"));
        setzen(nina, T01, "ich", "{\"status\":\"VIELLEICHT\",\"kommentar\":\"Knie\"}").statusCode(200)
                .body("kommentar", equalTo("Knie"));
    }

    @Test
    void anm03NurDreiAntworten() {
        String nina = anmelden("nina");
        setzen(nina, T01, "ich", "{\"status\":\"OFFEN\"}").statusCode(400);
        setzen(nina, T01, "ich", "{}").statusCode(400);
        setzen(nina, T01, "ich", "{\"status\":\"DABEI\",\"kommentar\":\"" + "x".repeat(201) + "\"}").statusCode(400);
    }

    @Test
    void s5KurzfristigeAbsage() {
        uhr("2026-11-16T19:00:00+01:00");
        String anna = anmelden("anna");
        setzen(anna, T01, "ich", "{\"status\":\"NICHT_DABEI\"}").statusCode(200).body("kurzfristig", equalTo(true));
        given().cookie("stv_sitzung", anmelden("petra")).get("/api/termine/" + T01 + "/uebersicht").then()
                .body("teams[0].gruppen[0].eintraege[2].person.vorname", equalTo("Anna"))
                .body("teams[0].gruppen[0].eintraege[2].antwort.kurzfristig", equalTo(true));
        // Zurück auf «dabei»: keine kurzfristige Absage mehr
        setzen(anna, T01, "ich", "{\"status\":\"DABEI\"}").statusCode(200).body("kurzfristig", equalTo(false));
    }

    @Test
    void anm10VorDerFristNichtKurzfristig() {
        setzen(anmelden("anna"), T01, "ich", "{\"status\":\"NICHT_DABEI\"}").statusCode(200)
                .body("kurzfristig", equalTo(false));
    }

    @Test
    void anm02EineAntwortProPersonUndTermin() {
        // Lea in Team 1 und Team 2: eine Antwort, in beiden Teams sichtbar (ANM-30)
        String lea = anmelden("lea");
        setzen(lea, T01, "ich", "{\"status\":\"NICHT_DABEI\"}").statusCode(200);
        JsonPath json = uebersicht(lea, T01).extract().jsonPath();
        assertThat(json.getString("teams[0].gruppen[0].eintraege[1].antwort.status"), equalTo("NICHT_DABEI"));
        assertThat(json.getString("teams[1].gruppen[1].eintraege[0].antwort.status"), equalTo("NICHT_DABEI"));
        assertThat(json.getInt("total.nichtDabei"), equalTo(3));
        assertThat(json.getInt("total.dabei"), equalTo(6));
    }

    @Test
    void anm01CoachAntwortetWieSpielerin() {
        String marc = anmelden("marc");
        setzen(marc, T01, "ich", "{\"status\":\"DABEI\"}").statusCode(200);
        JsonPath json = uebersicht(marc, T01).extract().jsonPath();
        assertThat(json.getString("coaches[1].antwort.status"), equalTo("DABEI"));
        // Coaches zählen nicht (ANM-23)
        assertThat(json.getInt("total.dabei"), equalTo(7));
    }

    @Test
    void rr02KeineAntwortAufFremdeTermine() {
        setzen(anmelden("ruth"), T01, "ich", "{\"status\":\"DABEI\"}").statusCode(403);
        // Admin ohne Team antwortet nicht für sich
        setzen(anmelden("alex"), T01, "ich", "{\"status\":\"DABEI\"}").statusCode(403);
        setzen(anmelden("anna"), "40000000-0000-4000-8000-000000000099", "ich", "{\"status\":\"DABEI\"}").statusCode(404);
    }

    @Test
    void ter06AbgesagterTerminNimmtKeineAntwort() {
        setzen(anmelden("anna"), T03, "ich", "{\"status\":\"DABEI\"}").statusCode(409)
                .body("regel", equalTo("TER-06")).body("detail", equalTo(AntwortRegeln.ABGESAGT));
        setzen(anmelden("petra"), T03, ANNA, "{\"status\":\"DABEI\"}").statusCode(409);
    }

    @Test
    void ter06VergangenerTerminNimmtKeineAntwort() {
        setzen(anmelden("anna"), T07, "ich", "{\"status\":\"DABEI\"}").statusCode(409).body("regel", equalTo("TER-06"));
    }

    // ------------------------------------------------------------------ Stellvertretend (S7, S8)

    @Test
    void s7StellvertretendAntworten() {
        String petra = anmelden("petra");
        setzen(petra, T01, NINA, "{\"status\":\"NICHT_DABEI\",\"kommentar\":\"Hat angerufen\"}").statusCode(200)
                .body("status", equalTo("NICHT_DABEI"))
                .body("eingetragenVon.vorname", equalTo("Petra")).body("eingetragenVon.nachname", equalTo("Keller"));
        // ANM-12: für alle sichtbar, wer eingetragen hat; Nina sieht es bei ihrer Antwort
        given().cookie("stv_sitzung", anmelden("nina")).get("/api/termine/" + T01).then().statusCode(200)
                .body("meineAntwort.status", equalTo("NICHT_DABEI"))
                .body("meineAntwort.eingetragenVon.vorname", equalTo("Petra"))
                .body("meineAntwort.kommentar", equalTo("Hat angerufen"));
        JsonPath anna = uebersicht(anmelden("anna"), T01).extract().jsonPath();
        assertThat(anna.getString("teams[0].gruppen[1].eintraege[1].antwort.eingetragenVon.nachname"), equalTo("Keller"));
        // Antwortet Nina danach selbst, fällt «eingetragen von» weg
        setzen(anmelden("nina"), T01, "ich", "{\"status\":\"DABEI\"}").statusCode(200).body("$", not(hasKey("eingetragenVon")));
    }

    @Test
    void s7StellvertretendFolgtDenselbenRegeln() {
        String petra = anmelden("petra");
        setzen(petra, T01, NINA, "{\"status\":\"VIELLEICHT\"}").statusCode(422).body("regel", equalTo("ANM-04"));
        uhr("2026-11-16T19:00:00+01:00");
        setzen(petra, T01, ANNA, "{\"status\":\"NICHT_DABEI\"}").statusCode(200).body("kurzfristig", equalTo(true));
    }

    @Test
    void s8KeinStellvertretenFuerFremdeTeams() {
        String marc = anmelden("marc");
        setzen(marc, T01, ANNA, "{\"status\":\"NICHT_DABEI\"}").statusCode(403)
                .contentType("application/problem+json").body("regel", equalTo("RR-07"));
        // Lea ist auch in Team 2 Spielerin: Marc darf
        setzen(marc, T01, LEA, "{\"status\":\"NICHT_DABEI\"}").statusCode(200);
        // Nicht für Coaches (Petra ist in Team 2 Spielerin: das geht)
        setzen(marc, T01, PETRA, "{\"status\":\"NICHT_DABEI\"}").statusCode(200);
        setzen(anmelden("petra"), T01, MARC, "{\"status\":\"DABEI\"}").statusCode(403);
        // RR-06: Petra ist in Team 2 nur Spielerin
        setzen(anmelden("petra"), T01, EVA, "{\"status\":\"DABEI\"}").statusCode(403);
        // Spielerinnen vertreten niemanden
        setzen(anmelden("anna"), T01, MIA, "{\"status\":\"DABEI\"}").statusCode(403);
        // Nicht Mitglied eines beteiligten Teams
        setzen(anmelden("ruth"), T01, ANNA, "{\"status\":\"DABEI\"}").statusCode(403);
        // Unveränderte Antwort von Anna
        given().cookie("stv_sitzung", anmelden("anna")).get("/api/termine/" + T01).then()
                .body("meineAntwort.status", equalTo("DABEI"));
    }

    @Test
    void stellvertretendFuerPersonOhneTeamImTermin() {
        setzen(anmelden("petra"), T01, TINA, "{\"status\":\"DABEI\"}").statusCode(404);
        setzen(anmelden("petra"), T01, "20000000-0000-4000-8000-000000000099", "{\"status\":\"DABEI\"}").statusCode(404);
        setzen(anmelden("alex"), T01, TINA, "{\"status\":\"DABEI\"}").statusCode(404);
    }

    @Test
    void rr04AdminVertrittJedes() {
        String alex = anmelden("alex");
        setzen(alex, T01, MARC, "{\"status\":\"DABEI\"}").statusCode(200).body("eingetragenVon.vorname", equalTo("Alex"));
        setzen(alex, T02, ANNA, "{\"status\":\"NICHT_DABEI\"}").statusCode(200);
    }

    // ------------------------------------------------------------------ Seed

    @Test
    void seedAntwortenNachTestdaten() {
        String anna = anmelden("anna");
        List<Map<String, Object>> liste = given().cookie("stv_sitzung", anna).get("/api/termine").then().statusCode(200)
                .extract().jsonPath().getList("findAll { it.id in ['" + T01 + "','" + T02 + "'] }.meineAntwort");
        assertThat(liste.stream().map(m -> m.get("status")).toList(), contains("DABEI", "DABEI"));
        // Termin 02: nur Anna hat geantwortet
        given().cookie("stv_sitzung", anna).get("/api/termine/" + T02).then()
                .body("zaehler.dabei", equalTo(1)).body("zaehler.offen", equalTo(6));
        // Termin 06: niemand
        given().cookie("stv_sitzung", anna).get("/api/termine/40000000-0000-4000-8000-000000000006/uebersicht").then()
                .body("total.offen", equalTo(13)).body("total.dabei", equalTo(0));
        // Termin 04 und 05 (Spieltage)
        given().cookie("stv_sitzung", anmelden("petra")).get("/api/termine/40000000-0000-4000-8000-000000000004/uebersicht").then()
                .body("total.dabei", equalTo(4)).body("total.vielleicht", equalTo(1)).body("total.nichtDabei", equalTo(1))
                .body("total.offen", equalTo(1)).body("coaches[0].antwort.status", equalTo("DABEI"));
        given().cookie("stv_sitzung", anmelden("marc")).get("/api/termine/40000000-0000-4000-8000-000000000005/uebersicht").then()
                .body("total.dabei", equalTo(5)).body("total.nichtDabei", equalTo(1)).body("total.offen", equalTo(0))
                .body("coaches[0].antwort.status", equalTo("DABEI"));
        assertThat(liste, not(empty()));
    }
}
