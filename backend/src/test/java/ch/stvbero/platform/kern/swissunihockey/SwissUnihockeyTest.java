package ch.stvbero.platform.kern.swissunihockey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static ch.stvbero.platform.kern.anmeldung.AnmeldungTest.anmelden;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * F-07: Anbindung swiss unihockey (SUH-02 bis SUH-10, BEN-06, S12) gegen den Simulator. IDs aus docs/testdaten.md.
 */
@QuarkusTest
class SwissUnihockeyTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String T04 = "40000000-0000-4000-8000-000000000004";
    static final String T05 = "40000000-0000-4000-8000-000000000005";
    static final String SPIEL_02 = "50000000-0000-4000-8000-000000000002";

    static final String SPIEL_01_SU = """
            {"suSpielId":5000001,"anspielzeit":"2026-11-22T09:30:00+01:00","heimteam":"Team 1",
             "gastteam":"UHC Musterdorf","halle":"Sporthalle Seefeld","ort":"Musterdorf",
             "mapsUrl":"https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf"}""";

    @Inject
    SwissUnihockeyQuelle quelle;

    @Inject
    AbgleichJob job;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static void simulator(String body) {
        given().contentType(ContentType.JSON).body(body).put("/api/test/swiss-unihockey/spiele").then().statusCode(204);
    }

    private static String spiel02(String zeit) {
        return """
                {"suSpielId":5000002,"anspielzeit":"2026-11-22T%s:00+01:00","heimteam":"Unihockey Beispielstadt",
                 "gastteam":"Team 1","halle":"Sporthalle Seefeld","ort":"Musterdorf"}""".formatted(zeit);
    }

    private static ValidatableResponse abgleich(String sitzung, String team) {
        return given().cookie("stv_sitzung", sitzung).post("/api/teams/" + team + "/swiss-unihockey/abgleich").then();
    }

    private static ValidatableResponse verknuepfen(String sitzung, String team, String suTeamId) {
        return given().cookie("stv_sitzung", sitzung).contentType(ContentType.JSON)
                .body("{\"suTeamId\":" + suTeamId + "}").put("/api/teams/" + team + "/swiss-unihockey").then();
    }

    private static JsonPath termin(String id) {
        return given().cookie("stv_sitzung", anmelden("alex")).get("/api/termine/" + id).then().statusCode(200)
                .extract().jsonPath();
    }

    private static List<Map<String, Object>> neueBenachrichtigungen() {
        List<Map<String, Object>> alle = given().get("/api/test/benachrichtigungen").then().statusCode(200)
                .extract().jsonPath().getList("$");
        return alle.stream().filter(b -> !((String) b.get("id")).startsWith("90000000")).toList();
    }

    // ------------------------------------------------------------------ Kriterium 1: Verknüpfen (SUH-02)

    @Test
    void suh02AdminVerknuepftTeamUndLoestAbgleichAus() {
        String alex = anmelden("alex");
        verknuepfen(alex, TEAM_2, "900002").statusCode(200)
                .body("id", equalTo(TEAM_2)).body("name", equalTo("Team 2")).body("suTeamId", equalTo(900002));
        given().cookie("stv_sitzung", alex).get("/api/teams/" + TEAM_2).then().body("suTeamId", equalTo(900002));

        simulator("{\"suTeamId\":900002,\"spiele\":[{\"suSpielId\":6000001,"
                + "\"anspielzeit\":\"2026-12-05T13:00:00+01:00\",\"heimteam\":\"UHC Fünf\",\"gastteam\":\"Team 2\","
                + "\"halle\":\"Turnhalle Weid\",\"ort\":\"Beispielstadt\"}]}");
        abgleich(alex, TEAM_2).statusCode(200).body("neu", equalTo(1)).body("betroffeneTermine", hasSize(1));

        verknuepfen(alex, TEAM_2, "null").statusCode(200).body("suTeamId", nullValue());
        abgleich(alex, TEAM_2).statusCode(409).body("regel", equalTo("SUH-02"));
    }

    @Test
    void suh02NurAdmins() {
        String petra = anmelden("petra");
        verknuepfen(petra, TEAM_1, "900009").statusCode(403);
        abgleich(petra, TEAM_1).statusCode(403);
        given().post("/api/teams/" + TEAM_1 + "/swiss-unihockey/abgleich").then().statusCode(401);
    }

    @Test
    void suh02TeamIdNurEinmalUndPositiv() {
        String alex = anmelden("alex");
        verknuepfen(alex, TEAM_2, "900001").statusCode(409).body("regel", equalTo("SUH-02"));
        verknuepfen(alex, TEAM_2, "0").statusCode(400).body("felder[0].feld", equalTo("suTeamId"));
        verknuepfen(alex, TEAM_1, "900001").statusCode(200);
        verknuepfen(alex, "10000000-0000-4000-8000-000000000999", "900005").statusCode(404);
        abgleich(alex, "10000000-0000-4000-8000-000000000999").statusCode(404);
    }

    // ------------------------------------------------------------------ Kriterium 2: Simulator in dev/test

    @Test
    void imProfilTestSprichtDieAppMitDemSimulator() {
        assertInstanceOf(SuSimulator.class, io.quarkus.arc.ClientProxy.unwrap(quelle));
    }

    @Test
    void nachResetIstAllesAbgeglichen() {
        abgleich(anmelden("alex"), TEAM_1).statusCode(200)
                .body("neu", equalTo(0)).body("geaendert", equalTo(0)).body("nichtMehrVorhanden", equalTo(0))
                .body("unveraendert", equalTo(2)).body("betroffeneTermine", hasSize(0));
        assertThat(neueBenachrichtigungen(), hasSize(0));
    }

    // ------------------------------------------------------------------ Kriterium 3: Spieltage bilden (SUH-04)

    @Test
    void suh04SpieleAmSelbenTagInDerselbenHalleSindEinSpieltag() {
        simulator("""
                {"suTeamId":900001,"spiele":[%s,%s,
                 {"suSpielId":5000020,"anspielzeit":"2026-12-06T10:00:00+01:00","heimteam":"Team 1",
                  "gastteam":"UHC Drei","halle":"Sporthalle Nord","ort":"Beromünster"},
                 {"suSpielId":5000021,"anspielzeit":"2026-12-06T12:30:00+01:00","heimteam":"UHC Vier",
                  "gastteam":"Team 1","halle":"Sporthalle Nord","ort":"Beromünster","resultat":null},
                 {"suSpielId":5000022,"anspielzeit":"2026-12-06T15:00:00+01:00","heimteam":"Team 1",
                  "gastteam":"UHC Fünf","halle":"Turnhalle Weid","ort":"Beispielstadt"},
                 {"suSpielId":5000023,"anspielzeit":"2026-12-13T10:00:00+01:00","heimteam":"UHC Sechs",
                  "gastteam":"Team 1","halle":"Sporthalle Nord","ort":"Beromünster","breite":47.2,"laenge":8.2}]}"""
                .formatted(SPIEL_01_SU, spiel02("11:45")));

        List<String> termine = abgleich(anmelden("alex"), TEAM_1).statusCode(200)
                .body("neu", equalTo(4)).body("unveraendert", equalTo(2))
                .extract().jsonPath().getList("betroffeneTermine");
        assertThat(termine, hasSize(3));

        JsonPath turnier = termin(termine.get(0));
        assertEquals("SPIELTAG", turnier.getString("typ"));
        assertEquals("Spieltag", turnier.getString("titel"));
        assertThat(turnier.getList("teams.id"), contains(TEAM_1));
        assertThat(turnier.getList("spiele.gegner"), contains("UHC Drei", "UHC Vier"));
        assertThat(turnier.getList("spiele.heim"), contains(true, false));
        assertThat(turnier.getList("spiele.quelle"), everyItem(equalTo("SWISS_UNIHOCKEY")));
        assertEquals("Sporthalle Nord", turnier.getString("ort.name"));
        assertEquals("Beromünster", turnier.getString("ort.adresse"));
        // SUH-07: Google-Maps-Link automatisch
        assertEquals("https://www.google.com/maps/search/?api=1&query=Sporthalle+Nord+Beromünster"
                .replace("ü", "%C3%BC"), turnier.getString("ort.mapsUrl"));
        assertEquals("2026-12-06T10:00:00+01:00", turnier.getString("beginn"));
        assertEquals("2026-12-06T13:45:00+01:00", turnier.getString("ende"));

        JsonPath andereHalle = termin(termine.get(1));
        assertThat(andereHalle.getList("spiele.gegner"), contains("UHC Fünf"));
        JsonPath andererTag = termin(termine.get(2));
        assertThat(andererTag.getList("spiele.gegner"), contains("UHC Sechs"));
        assertEquals("https://www.google.com/maps/search/?api=1&query=47.2%2C8.2", andererTag.getString("ort.mapsUrl"));

        // Erneuter Abgleich ändert nichts
        abgleich(anmelden("alex"), TEAM_1).statusCode(200).body("neu", equalTo(0)).body("unveraendert", equalTo(6));
    }

    @Test
    void suh04NeuesSpielKommtZumBestehendenSpieltag() {
        simulator("""
                {"suTeamId":900001,"spiele":[%s,%s,
                 {"suSpielId":5000030,"anspielzeit":"2026-11-22T14:00:00+01:00","heimteam":"Team 1",
                  "gastteam":"UHC Drei","halle":"Sporthalle Seefeld","ort":"Musterdorf"}]}"""
                .formatted(SPIEL_01_SU, spiel02("11:45")));
        abgleich(anmelden("alex"), TEAM_1).statusCode(200).body("neu", equalTo(1))
                .body("betroffeneTermine", contains(T04));
        JsonPath t = termin(T04);
        assertThat(t.getList("spiele.gegner"), contains("UHC Musterdorf", "Unihockey Beispielstadt", "UHC Drei"));
        assertEquals("2026-11-22T15:15:00+01:00", t.getString("ende"));
    }

    // ------------------------------------------------------------------ Kriterium 4: S12 (SUH-05, SUH-06, BEN-06)

    @Test
    void s12VerschiebungWirdUebernommenEigeneDatenBleiben() {
        String alex = anmelden("alex");
        JsonPath vorher = termin(T04);
        List<Object> aufgebotVorher = given().cookie("stv_sitzung", alex).get("/api/termine/" + T04 + "/aufgebot")
                .then().statusCode(200).extract().jsonPath().getList("gruppen");

        simulator("{\"suTeamId\":900001,\"spiele\":[" + SPIEL_01_SU + "," + spiel02("12:15") + "]}");
        abgleich(alex, TEAM_1).statusCode(200)
                .body("geaendert", equalTo(1)).body("unveraendert", equalTo(1)).body("neu", equalTo(0))
                .body("betroffeneTermine", contains(T04));

        JsonPath nachher = termin(T04);
        assertThat(nachher.getList("spiele.anspielzeit"),
                contains("2026-11-22T09:30:00+01:00", "2026-11-22T12:15:00+01:00"));
        assertThat(nachher.getList("spiele.id"), hasItem(SPIEL_02));
        // SUH-06: eigene Daten unverändert
        assertEquals(vorher.getString("treffpunkt.zeit"), nachher.getString("treffpunkt.zeit"));
        assertEquals(vorher.getString("treffpunkt.ort"), nachher.getString("treffpunkt.ort"));
        assertEquals("Weisse Trikots mitnehmen", nachher.getString("hinweise"));
        assertEquals(vorher.getString("beginn"), nachher.getString("beginn"));
        assertEquals("2026-11-22T13:30:00+01:00", nachher.getString("ende"));
        assertEquals(vorher.getMap("zaehler"), nachher.getMap("zaehler"), "Antworten bleiben");
        assertEquals(aufgebotVorher, given().cookie("stv_sitzung", alex).get("/api/termine/" + T04 + "/aufgebot")
                .then().extract().jsonPath().getList("gruppen"), "Aufgebot bleibt");

        // BEN-06: alle Mitglieder von Team 1 (Petra, Anna, Lea, Mia, Sara, Nina, Jana, Kim), nicht Team 2
        List<Map<String, Object>> neu = neueBenachrichtigungen();
        assertThat(neu.stream().map(b -> ((Map<?, ?>) b.get("empfaenger")).get("vorname")).toList(),
                containsInAnyOrder("Petra", "Anna", "Lea", "Mia", "Sara", "Nina", "Jana", "Kim"));
        assertThat(neu.stream().map(b -> b.get("art")).toList(), everyItem(equalTo("SPIEL_GEAENDERT")));
        assertThat(neu.stream().map(b -> b.get("terminId")).toList(), everyItem(equalTo(T04)));
        assertThat(neu.stream().map(b -> b.get("text")).toList(), everyItem(equalTo(
                "Spieltag So 22.11. wurde geändert: Spiel gegen Unihockey Beispielstadt neu um 12:15.")));

        // Zweiter Abgleich: nichts mehr zu tun, keine weitere Benachrichtigung
        abgleich(alex, TEAM_1).statusCode(200).body("geaendert", equalTo(0)).body("unveraendert", equalTo(2));
        assertThat(neueBenachrichtigungen(), hasSize(8));
    }

    @Test
    void suh05ErstesSpielVerschobenVerschiebtDenSpieltag() {
        simulator("""
                {"suTeamId":900001,"spiele":[
                 {"suSpielId":5000001,"anspielzeit":"2026-11-22T10:00:00+01:00","heimteam":"Team 1",
                  "gastteam":"UHC Musterdorf","halle":"Sporthalle Seefeld","ort":"Musterdorf"},%s]}"""
                .formatted(spiel02("11:45")));
        abgleich(anmelden("alex"), TEAM_1).statusCode(200).body("geaendert", equalTo(1));
        JsonPath t = termin(T04);
        assertEquals("2026-11-22T10:00:00+01:00", t.getString("beginn"));
        assertEquals(true, t.getBoolean("verschoben"));
        assertEquals("2026-11-22T08:00:00+01:00", t.getString("treffpunkt.zeit"), "Treffpunkt bleibt (SUH-06)");
    }

    @Test
    void suh05SpielAnAnderemTagWirdEigenerSpieltag() {
        simulator("""
                {"suTeamId":900001,"spiele":[%s,
                 {"suSpielId":5000002,"anspielzeit":"2026-11-29T11:45:00+01:00","heimteam":"Unihockey Beispielstadt",
                  "gastteam":"Team 1","halle":"Turnhalle Weid","ort":"Beispielstadt"}]}""".formatted(SPIEL_01_SU));
        List<String> betroffen = abgleich(anmelden("alex"), TEAM_1).statusCode(200).body("geaendert", equalTo(1))
                .extract().jsonPath().getList("betroffeneTermine");
        assertThat(betroffen, hasSize(2));
        assertThat(termin(T04).getList("spiele.gegner"), contains("UHC Musterdorf"));
        String neuerTermin = betroffen.stream().filter(id -> !id.equals(T04)).findFirst().orElseThrow();
        JsonPath neu = termin(neuerTermin);
        assertThat(neu.getList("spiele.id"), contains(SPIEL_02));
        assertEquals("Turnhalle Weid", neu.getString("ort.name"));
        assertThat(neueBenachrichtigungen().stream().map(b -> b.get("text")).distinct().toList(), contains(
                "Spieltag So 22.11. wurde geändert: Spiel gegen Unihockey Beispielstadt neu am So 29.11. 11:45, "
                        + "neu in Turnhalle Weid."));
    }

    @Test
    void suh08ResultatWirdUebernommenOhneBenachrichtigung() {
        simulator("{\"suTeamId\":900001,\"spiele\":[" + SPIEL_01_SU.replace("\"halle\"", "\"resultat\":\"5:3\",\"halle\"")
                + "," + spiel02("11:45") + "]}");
        abgleich(anmelden("alex"), TEAM_1).statusCode(200).body("geaendert", equalTo(1));
        assertThat(termin(T04).getList("spiele.resultat"), contains("5:3", null));
        assertThat(neueBenachrichtigungen(), hasSize(0));
    }

    // ------------------------------------------------------------------ Kriterium 5: Spiel fehlt

    @Test
    void fehlendesSpielWirdMarkiertNichtGeloescht() {
        String alex = anmelden("alex");
        simulator("{\"suTeamId\":900001,\"spiele\":[" + SPIEL_01_SU + "]}");
        abgleich(alex, TEAM_1).statusCode(200).body("nichtMehrVorhanden", equalTo(1)).body("unveraendert", equalTo(1))
                .body("betroffeneTermine", contains(T04));
        JsonPath t = termin(T04);
        assertThat(t.getList("spiele.beiSuNichtMehrVorhanden"), contains(false, true));
        assertThat(t.getList("spiele.id"), hasItem(SPIEL_02));

        // Jetzt darf die Coach es löschen (SUH-06)
        given().cookie("stv_sitzung", anmelden("petra")).delete("/api/termine/" + T04 + "/spiele/" + SPIEL_02)
                .then().statusCode(204);
    }

    @Test
    void wiederGeliefertesSpielIstNichtMehrMarkiert() {
        String alex = anmelden("alex");
        simulator("{\"suTeamId\":900001,\"spiele\":[" + SPIEL_01_SU + "]}");
        abgleich(alex, TEAM_1).statusCode(200);
        given().post("/api/test/reset").then().statusCode(204);
        simulator("{\"suTeamId\":900001,\"spiele\":[" + SPIEL_01_SU + "]}");
        abgleich(alex = anmelden("alex"), TEAM_1).statusCode(200);
        simulator("{\"suTeamId\":900001,\"spiele\":[" + SPIEL_01_SU + "," + spiel02("11:45") + "]}");
        abgleich(alex, TEAM_1).statusCode(200).body("geaendert", equalTo(1)).body("nichtMehrVorhanden", equalTo(0));
        assertThat(termin(T04).getList("spiele.beiSuNichtMehrVorhanden"), everyItem(equalTo(false)));
    }

    // ------------------------------------------------------------------ Kriterium 6: nicht erreichbar (SUH-09)

    @Test
    void suh09NichtErreichbarAendertNichts() {
        String alex = anmelden("alex");
        JsonPath vorher = termin(T04);
        simulator("{\"suTeamId\":900001,\"nichtErreichbar\":true,\"spiele\":[" + SPIEL_01_SU + "]}");

        abgleich(alex, TEAM_1).statusCode(502).contentType("application/problem+json")
                .body("regel", equalTo("SUH-09"))
                .body("detail", equalTo(Abgleich.NICHT_ERREICHBAR));

        JsonPath nachher = termin(T04);
        assertEquals(vorher.getList("spiele"), nachher.getList("spiele"));
        assertThat(neueBenachrichtigungen(), hasSize(0));
        // Die App funktioniert normal weiter
        given().cookie("stv_sitzung", anmelden("anna")).get("/api/termine").then().statusCode(200);
        given().cookie("stv_sitzung", alex).get("/api/teams").then().statusCode(200);
    }

    // ------------------------------------------------------------------ Kriterium 7: täglicher Job (SUH-03)

    @Test
    void suh03TaeglicherJobGleichtAlleVerknuepftenTeamsAb() {
        String alex = anmelden("alex");
        verknuepfen(alex, TEAM_2, "900002").statusCode(200);
        simulator("{\"suTeamId\":900002,\"nichtErreichbar\":true,\"spiele\":[]}");
        simulator("{\"suTeamId\":900001,\"spiele\":[" + SPIEL_01_SU + "," + spiel02("12:15") + "]}");

        // Team 2 gestört, Team 1 läuft trotzdem
        assertEquals(1, job.alleAbgleichen());
        assertThat(termin(T04).getList("spiele.anspielzeit"), hasItem("2026-11-22T12:15:00+01:00"));
    }

    // ------------------------------------------------------------------ SUH-10, Abgrenzung

    @Test
    void suh10VonHandErfassteSpieltageBleibenUnberuehrt() {
        String alex = anmelden("alex");
        verknuepfen(alex, TEAM_2, "900002").statusCode(200);
        JsonPath vorher = termin(T05);
        // swiss unihockey liefert für Team 2 ein Spiel am selben Tag in derselben Halle wie der manuelle Termin 05
        simulator("{\"suTeamId\":900002,\"spiele\":[{\"suSpielId\":6000005,"
                + "\"anspielzeit\":\"2026-11-28T15:00:00+01:00\",\"heimteam\":\"Team 2\",\"gastteam\":\"UHC Fünf\","
                + "\"halle\":\"Turnhalle Weid\",\"ort\":\"Beispielstadt\"}]}");
        List<String> betroffen = abgleich(alex, TEAM_2).statusCode(200).body("neu", equalTo(1))
                .extract().jsonPath().getList("betroffeneTermine");
        assertThat(betroffen, not(hasItem(T05)));
        JsonPath nachher = termin(T05);
        assertEquals(vorher.getList("spiele"), nachher.getList("spiele"));
        assertEquals(vorher.getString("ende"), nachher.getString("ende"));
    }

    @Test
    void abgleichOhneAktuelleSaison() {
        given().delete("/api/test/aktuelle-saison").then().statusCode(204);
        abgleich(anmelden("alex"), TEAM_1).statusCode(409).body("regel", equalTo("SAI-03"));
    }

    @Test
    void simulatorVorgabeWirdGeprueft() {
        given().contentType(ContentType.JSON).body("{\"suTeamId\":900001,\"spiele\":[{\"suSpielId\":1}]}")
                .put("/api/test/swiss-unihockey/spiele").then().statusCode(400);
    }
}
