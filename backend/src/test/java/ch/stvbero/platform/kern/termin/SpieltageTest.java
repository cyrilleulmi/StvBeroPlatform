package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.fehler.Gleichzeitig;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ch.stvbero.platform.kern.anmeldung.AnmeldungTest.anmelden;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * F-05: Spieltage und Aufgebot (AUF-10 bis AUF-28, RR-03, S9, S11). IDs aus docs/testdaten.md.
 */
@QuarkusTest
class SpieltageTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String NINA = "20000000-0000-4000-8000-000000000009";
    static final String KIM = "20000000-0000-4000-8000-000000000011";
    static final String TINA = "20000000-0000-4000-8000-000000000016";
    static final String T01 = "40000000-0000-4000-8000-000000000001";
    static final String T04 = "40000000-0000-4000-8000-000000000004";
    static final String T05 = "40000000-0000-4000-8000-000000000005";
    static final String SPIEL_01 = "50000000-0000-4000-8000-000000000001";
    static final String SPIEL_02 = "50000000-0000-4000-8000-000000000002";
    static final String SPIEL_03 = "50000000-0000-4000-8000-000000000003";

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static ValidatableResponse termin(String sitzung, String id) {
        return given().cookie("stv_sitzung", sitzung).get("/api/termine/" + id).then();
    }

    private static ValidatableResponse aufgebot(String sitzung, String id) {
        return given().cookie("stv_sitzung", sitzung).get("/api/termine/" + id + "/aufgebot").then();
    }

    private static ValidatableResponse aufgebotSetzen(String sitzung, String id, String body) {
        return given().cookie("stv_sitzung", sitzung).contentType(ContentType.JSON).body(body)
                .put("/api/termine/" + id + "/aufgebot").then();
    }

    private static ValidatableResponse veroeffentlichen(String sitzung, String id) {
        return given().cookie("stv_sitzung", sitzung).post("/api/termine/" + id + "/aufgebot/veroeffentlichen").then();
    }

    private static ValidatableResponse spielHinzufuegen(String sitzung, String termin, String body) {
        return given().cookie("stv_sitzung", sitzung).contentType(ContentType.JSON).body(body)
                .post("/api/termine/" + termin + "/spiele").then();
    }

    private static ValidatableResponse spielAendern(String sitzung, String termin, String spiel, String body) {
        return given().cookie("stv_sitzung", sitzung).contentType(ContentType.JSON).body(body)
                .put("/api/termine/" + termin + "/spiele/" + spiel).then();
    }

    private static ValidatableResponse spielLoeschen(String sitzung, String termin, String spiel) {
        return given().cookie("stv_sitzung", sitzung).delete("/api/termine/" + termin + "/spiele/" + spiel).then();
    }

    private static void uhr(String jetzt) {
        given().contentType(ContentType.JSON).body("{\"jetzt\":\"" + jetzt + "\"}").put("/api/test/uhr").then().statusCode(204);
    }

    private static String ids(String... ids) {
        return "{\"personIds\":[" + String.join(",", List.of(ids).stream().map(i -> "\"" + i + "\"").toList()) + "]}";
    }

    // ------------------------------------------------------------------ Seed (Kriterium 1)

    @Test
    void seedTermin04MitSpielenTreffpunktUndEntwurf() {
        JsonPath json = termin(anmelden("petra"), T04).statusCode(200).extract().jsonPath();
        // AUF-14: nach Anspielzeit
        assertThat(json.getList("spiele.id"), contains(SPIEL_01, SPIEL_02));
        assertThat(json.getList("spiele.gegner"), contains("UHC Musterdorf", "Unihockey Beispielstadt"));
        assertThat(json.getList("spiele.heim"), contains(true, false));
        assertThat(json.getList("spiele.anspielzeit"), contains("2026-11-22T09:30:00+01:00", "2026-11-22T11:45:00+01:00"));
        assertThat(json.getList("spiele.quelle"), everyItem(equalTo("SWISS_UNIHOCKEY")));
        assertThat(json.getList("spiele.suSpielId"), contains(5000001, 5000002));
        assertThat(json.getString("treffpunkt.ort"), equalTo("Parkplatz Sporthalle Nord"));
        assertThat(json.getString("treffpunkt.zeit"), equalTo("2026-11-22T08:00:00+01:00"));
        assertThat(json.getString("ort.mapsUrl"), equalTo("https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf"));
        assertThat(json.getString("hinweise"), equalTo("Weisse Trikots mitnehmen"));
        assertThat(json.getString("aufgebotStatus"), equalTo("ENTWURF"));
        // Petra ist in Team 1 Coach: kein eigener Aufgebotsstatus
        assertThat(json.get("meinAufgebot"), nullValue());
    }

    @Test
    void seedTermin05VeroeffentlichtMitEinemSpiel() {
        JsonPath json = termin(anmelden("marc"), T05).statusCode(200).extract().jsonPath();
        assertThat(json.getList("spiele.id"), contains(SPIEL_03));
        assertThat(json.getString("spiele[0].quelle"), equalTo("MANUELL"));
        assertThat(json.getString("aufgebotStatus"), equalTo("VEROEFFENTLICHT"));
        // AUF-24: jede Spielerin sieht ihren Status; Petra ist in Team 2 Spielerin
        assertThat(termin(anmelden("eva"), T05).extract().path("meinAufgebot"), equalTo("AUFGEBOTEN"));
        assertThat(termin(anmelden("petra"), T05).extract().path("meinAufgebot"), equalTo("AUFGEBOTEN"));
        assertThat(termin(anmelden("lea"), T05).extract().path("meinAufgebot"), equalTo("NICHT_AUFGEBOTEN"));
    }

    @Test
    void auf23EntwurfBleibtFuerSpielerinnenVerborgen() {
        JsonPath json = termin(anmelden("anna"), T04).statusCode(200).extract().jsonPath();
        assertThat(json.getString("aufgebotStatus"), equalTo("KEINES"));
        assertThat(json.getString("meinAufgebot"), equalTo("NOCH_KEIN_AUFGEBOT"));
    }

    @Test
    void trainingHatKeineSpieleUndKeinAufgebot() {
        JsonPath json = termin(anmelden("anna"), T01).statusCode(200).extract().jsonPath();
        assertThat(json.get("spiele"), nullValue());
        assertThat(json.get("aufgebotStatus"), nullValue());
        assertThat(json.get("meinAufgebot"), nullValue());
    }

    // ------------------------------------------------------------------ Aufgebot lesen (Kriterium 4, 7)

    @Test
    void auf21Auf22AuswahlNachLinienMitAntwortstatus() {
        JsonPath json = aufgebot(anmelden("petra"), T04).statusCode(200).extract().jsonPath();
        assertThat(json.getString("status"), equalTo("ENTWURF"));
        assertThat(json.getInt("anzahlAufgeboten"), equalTo(5));
        assertThat(json.get("veroeffentlichtAm"), nullValue());
        assertThat(json.getList("gruppen.name"), contains("Linie 1", "Linie 2", "Goalies", "Ohne Linie"));
        assertThat(json.getList("gruppen.art"), contains("LINIE", "LINIE", "GOALIES", "OHNE_LINIE"));
        assertThat(json.getList("gruppen[0].eintraege.person.vorname"), contains("Mia", "Lea", "Anna"));
        assertThat(json.getList("gruppen[0].eintraege.antwort.status"), contains("VIELLEICHT", "DABEI", "DABEI"));
        assertThat(json.getString("gruppen[0].eintraege[0].antwort.kommentar"), equalTo("Muss evtl. arbeiten"));
        assertThat(json.getList("gruppen[0].eintraege.aufgeboten"), contains(true, true, true));
        assertThat(json.getList("gruppen[1].eintraege.person.vorname"), contains("Sara", "Nina"));
        assertThat(json.getList("gruppen[1].eintraege.antwort.status"), contains("DABEI", "OFFEN"));
        assertThat(json.getList("gruppen[1].eintraege.aufgeboten"), contains(true, false));
        assertThat(json.getList("gruppen[2].eintraege.person.vorname"), contains("Jana"));
        assertThat(json.getList("gruppen[3].eintraege.person.vorname"), contains("Kim"));
        assertThat(json.getList("gruppen[3].eintraege.antwort.status"), contains("NICHT_DABEI"));
    }

    @Test
    void auf28CoachesStehenNichtInDerAuswahl() {
        JsonPath json = aufgebot(anmelden("petra"), T04).statusCode(200).extract().jsonPath();
        assertThat(json.getList("gruppen.eintraege.person.id.flatten()"), not(hasItem(PETRA)));
        assertThat(json.getList("gruppen.eintraege.person.id.flatten()").size(), equalTo(7));
    }

    @Test
    void s11AufgebotNurFuerCoachesDesTeamsUndAdmins() {
        aufgebot(anmelden("anna"), T04).statusCode(403).body("regel", equalTo("RR-07"));
        aufgebot(anmelden("marc"), T04).statusCode(403);
        aufgebotSetzen(anmelden("anna"), T04, ids(ANNA)).statusCode(403);
        veroeffentlichen(anmelden("anna"), T04).statusCode(403);
        aufgebot(anmelden("alex"), T04).statusCode(200);
        aufgebot(anmelden("marc"), T05).statusCode(200);
    }

    @Test
    void auf02KeinAufgebotFuerTrainings() {
        aufgebot(anmelden("petra"), T01).statusCode(409).body("regel", equalTo("AUF-02"));
        aufgebotSetzen(anmelden("petra"), T01, ids(ANNA)).statusCode(409);
    }

    @Test
    void aufgebotUnbekannterTermin404() {
        aufgebot(anmelden("petra"), "40000000-0000-4000-8000-000000000099").statusCode(404);
    }

    // ------------------------------------------------------------------ Aufgebot setzen und veröffentlichen

    @Test
    void auf22AuchOffeneUndAbgemeldeteAufbietbar() {
        String petra = anmelden("petra");
        JsonPath json = aufgebotSetzen(petra, T04, ids(NINA, KIM)).statusCode(200).extract().jsonPath();
        assertThat(json.getInt("anzahlAufgeboten"), equalTo(2));
        assertThat(json.getString("status"), equalTo("ENTWURF"));
        assertThat(json.getList("gruppen[1].eintraege.aufgeboten"), contains(false, true));
        assertThat(json.getList("gruppen[3].eintraege.aufgeboten"), contains(true));
        // bleibt gespeichert
        assertThat(aufgebot(petra, T04).extract().path("anzahlAufgeboten"), equalTo(2));
    }

    @Test
    void auf28CoachUndFremdeSindNichtAufbietbar() {
        String petra = anmelden("petra");
        aufgebotSetzen(petra, T04, ids(ANNA, PETRA)).statusCode(422).body("regel", equalTo("AUF-28"));
        aufgebotSetzen(petra, T04, ids(TINA)).statusCode(422);
        aufgebotSetzen(petra, T04, "{}").statusCode(400);
        assertThat(aufgebot(petra, T04).extract().path("anzahlAufgeboten"), equalTo(5));
    }

    @Test
    void s9VeroeffentlichenZeigtSpielerinnenIhrenStatus() {
        String petra = anmelden("petra");
        assertThat(termin(anmelden("anna"), T04).extract().path("meinAufgebot"), equalTo("NOCH_KEIN_AUFGEBOT"));

        JsonPath json = veroeffentlichen(petra, T04).statusCode(200).extract().jsonPath();
        assertThat(json.getString("status"), equalTo("VEROEFFENTLICHT"));
        assertThat(json.getString("veroeffentlichtAm"), equalTo("2026-11-16T06:00:00+01:00"));

        assertThat(termin(anmelden("anna"), T04).extract().path("meinAufgebot"), equalTo("AUFGEBOTEN"));
        assertThat(termin(anmelden("kim"), T04).extract().path("meinAufgebot"), equalTo("NICHT_AUFGEBOTEN"));
        assertThat(termin(anmelden("nina"), T04).extract().path("aufgebotStatus"), equalTo("VEROEFFENTLICHT"));

        // nur einmal
        veroeffentlichen(petra, T04).statusCode(409).body("regel", equalTo("AUF-23"));
        // Änderung danach: bleibt veröffentlicht, Kim jetzt aufgeboten (AUF-27)
        aufgebotSetzen(petra, T04, ids(ANNA, KIM)).statusCode(200).body("status", equalTo("VEROEFFENTLICHT"));
        assertThat(termin(anmelden("kim"), T04).extract().path("meinAufgebot"), equalTo("AUFGEBOTEN"));
        assertThat(termin(anmelden("lea"), T04).extract().path("meinAufgebot"), equalTo("NICHT_AUFGEBOTEN"));
    }

    @Test
    void auf23LeeresAufgebotLaesstSichNichtVeroeffentlichen() {
        String petra = anmelden("petra");
        aufgebotSetzen(petra, T04, ids()).statusCode(200).body("status", equalTo("KEINES"));
        veroeffentlichen(petra, T04).statusCode(409).body("regel", equalTo("AUF-23"));
    }

    @Test
    void ter06VorbeiOderAbgesagtLaesstSichNichtMehrAendern() {
        String petra = anmelden("petra");
        uhr("2026-11-22T14:00:00+01:00");
        aufgebotSetzen(petra, T04, ids(ANNA)).statusCode(409).body("regel", equalTo("TER-06"));
        veroeffentlichen(petra, T04).statusCode(409);
        aufgebot(petra, T04).statusCode(200);
    }

    @Test
    void gleichzeitigesSpeichernErgibtNie500() {
        String petra = anmelden("petra");
        List<Integer> codes = Gleichzeitig.statuscodes(4,
                () -> aufgebotSetzen(petra, T04, ids(ANNA)).extract().statusCode());
        assertThat(codes, everyItem(equalTo(200)));
    }

    // ------------------------------------------------------------------ Spiele (Kriterium 2, 8)

    @Test
    void auf16SpieltagVonHandMitSpielenErfassen() {
        String petra = anmelden("petra");
        String terminId = given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body("""
                        {"typ":"SPIELTAG","titel":"Spieltag","beginn":"2026-12-06T10:00:00+01:00",
                         "ende":"2026-12-06T14:00:00+01:00","teamIds":["%s"],
                         "ort":{"name":"Halle Ost","mapsUrl":"https://maps.google.com/?q=Halle+Ost"},
                         "hinweise":"Rote Trikots","treffpunkt":{"zeit":"2026-12-06T09:00:00+01:00","ort":"Bahnhof"}}
                        """.formatted(TEAM_1))
                .post("/api/termine").then().statusCode(201).extract().path("id");
        spielHinzufuegen(petra, terminId, """
                {"gegner":"UHC Zwei","anspielzeit":"2026-12-06T12:00:00+01:00","heim":false}""").statusCode(201)
                .body("quelle", equalTo("MANUELL"));
        spielHinzufuegen(petra, terminId, """
                {"gegner":"UHC Eins","anspielzeit":"2026-12-06T10:00:00+01:00","heim":true}""").statusCode(201);

        JsonPath json = termin(petra, terminId).statusCode(200).extract().jsonPath();
        assertThat(json.getList("spiele.gegner"), contains("UHC Eins", "UHC Zwei"));
        assertThat(json.getList("spiele.heim"), contains(true, false));
        assertThat(json.getString("treffpunkt.ort"), equalTo("Bahnhof"));
        assertThat(json.getString("ort.mapsUrl"), equalTo("https://maps.google.com/?q=Halle+Ost"));
        assertThat(json.getString("hinweise"), equalTo("Rote Trikots"));
        assertThat(json.getString("aufgebotStatus"), equalTo("KEINES"));
        assertThat(termin(anmelden("anna"), terminId).extract().path("meinAufgebot"), equalTo("NOCH_KEIN_AUFGEBOT"));
    }

    @Test
    void spielHinzufuegenRegeln() {
        String petra = anmelden("petra");
        String spiel = """
                {"gegner":"UHC Drei","anspielzeit":"2026-11-22T12:30:00+01:00","heim":true}""";
        spielHinzufuegen(anmelden("anna"), T04, spiel).statusCode(403);
        spielHinzufuegen(anmelden("marc"), T04, spiel).statusCode(403);
        spielHinzufuegen(petra, T01, spiel).statusCode(409).body("regel", equalTo("AUF-01"))
                .body("detail", equalTo("Nur ein Spieltag hat Spiele."));
        spielHinzufuegen(petra, T04, """
                {"gegner":"UHC Drei","anspielzeit":"2026-11-23T12:30:00+01:00","heim":true}""")
                .statusCode(400).body("felder[0].feld", equalTo("anspielzeit"));
        spielHinzufuegen(petra, T04, """
                {"gegner":"","anspielzeit":"2026-11-22T12:30:00+01:00","heim":true}""").statusCode(400);
        spielHinzufuegen(petra, T04, spiel).statusCode(201);
        assertThat(termin(petra, T04).extract().jsonPath().getList("spiele.gegner"),
                contains("UHC Musterdorf", "Unihockey Beispielstadt", "UHC Drei"));
    }

    @Test
    void auf15ResultatErfassenAuchNachDemSpieltag() {
        String petra = anmelden("petra");
        uhr("2026-11-23T08:00:00+01:00");
        spielAendern(petra, T04, SPIEL_01, """
                {"gegner":"UHC Musterdorf","anspielzeit":"2026-11-22T09:30:00+01:00","heim":true,"resultat":"5:3"}""")
                .statusCode(200).body("resultat", equalTo("5:3"));
        assertThat(termin(anmelden("anna"), T04).extract().path("spiele[0].resultat"), equalTo("5:3"));
        spielAendern(petra, T04, SPIEL_01, """
                {"gegner":"UHC Musterdorf","anspielzeit":"2026-11-22T09:30:00+01:00","heim":true,"resultat":"5-3"}""")
                .statusCode(400);
        spielAendern(anmelden("anna"), T04, SPIEL_01, """
                {"gegner":"UHC Musterdorf","anspielzeit":"2026-11-22T09:30:00+01:00","heim":true,"resultat":"1:0"}""")
                .statusCode(403);
    }

    @Test
    void suh06ImportierteSpieleSindGeschuetzt() {
        String petra = anmelden("petra");
        spielAendern(petra, T04, SPIEL_02, """
                {"gegner":"Andere","anspielzeit":"2026-11-22T11:45:00+01:00","heim":false}""")
                .statusCode(409).body("regel", equalTo("SUH-06"));
        spielLoeschen(petra, T04, SPIEL_02).statusCode(409);
        // Spiel eines anderen Termins
        spielAendern(petra, T04, SPIEL_03, """
                {"gegner":"UH Testingen","anspielzeit":"2026-11-22T11:45:00+01:00","heim":true}""").statusCode(404);
    }

    @Test
    void vonHandErfasstesSpielAendernUndLoeschen() {
        String marc = anmelden("marc");
        spielAendern(marc, T05, SPIEL_03, """
                {"gegner":"UH Testingen II","anspielzeit":"2026-11-28T14:15:00+01:00","heim":false}""")
                .statusCode(200).body("gegner", equalTo("UH Testingen II")).body("heim", equalTo(false));
        spielLoeschen(anmelden("petra"), T05, SPIEL_03).statusCode(403);
        spielLoeschen(marc, T05, SPIEL_03).statusCode(204);
        assertThat(termin(marc, T05).extract().jsonPath().getList("spiele"), equalTo(List.of()));
        spielLoeschen(marc, T05, SPIEL_03).statusCode(404);
    }

    @Test
    void spieltagVerschiebenNimmtVonHandErfassteSpieleMit() {
        String marc = anmelden("marc");
        given().cookie("stv_sitzung", marc).contentType(ContentType.JSON).body("""
                        {"typ":"SPIELTAG","titel":"Spieltag","beginn":"2026-11-29T14:00:00+01:00",
                         "ende":"2026-11-29T16:00:00+01:00","teamIds":["10000000-0000-4000-8000-000000000002"]}""")
                .put("/api/termine/" + T05).then().statusCode(200)
                .body("spiele[0].anspielzeit", equalTo("2026-11-29T14:00:00+01:00"));
    }

    @Test
    void o48ImportierterSpieltagNurAmSelbenTagVerschiebbarResultatBleibtErfassbar() {
        String petra = anmelden("petra");
        String verschoben = """
                {"typ":"SPIELTAG","titel":"Spieltag","beginn":"2026-11-29T09:30:00+01:00",
                 "ende":"2026-11-29T13:00:00+01:00","teamIds":["%s"]}""".formatted(TEAM_1);
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body(verschoben)
                .put("/api/termine/" + T04).then().statusCode(422)
                .body("regel", equalTo("SUH-05")).body("felder[0].feld", equalTo("beginn"));
        // Spieltag und Spiele bleiben am 22.11.
        termin(petra, T04).body("beginn", equalTo("2026-11-22T09:30:00+01:00"))
                .body("spiele[0].anspielzeit", equalTo("2026-11-22T09:30:00+01:00"));
        // Am selben Tag verschieben geht
        given().cookie("stv_sitzung", petra).contentType(ContentType.JSON).body("""
                        {"typ":"SPIELTAG","titel":"Spieltag","beginn":"2026-11-22T09:00:00+01:00",
                         "ende":"2026-11-22T13:00:00+01:00","teamIds":["%s"]}""".formatted(TEAM_1))
                .put("/api/termine/" + T04).then().statusCode(200);
        spielAendern(petra, T04, SPIEL_01, """
                {"gegner":"UHC Musterdorf","anspielzeit":"2026-11-22T09:30:00+01:00","heim":true,"resultat":"2:1"}""")
                .statusCode(200).body("resultat", equalTo("2:1"));
    }

    @Test
    void auf01SpieltagMitSpielenBleibtSpieltag() {
        given().cookie("stv_sitzung", anmelden("petra")).contentType(ContentType.JSON).body("""
                        {"typ":"TRAINING","titel":"Training","beginn":"2026-11-22T09:30:00+01:00",
                         "ende":"2026-11-22T13:00:00+01:00","teamIds":["%s"]}""".formatted(TEAM_1))
                .put("/api/termine/" + T04).then().statusCode(409).body("regel", equalTo("AUF-01"));
    }

    @Test
    void auf03SpieltagGehoertZuGenauEinemTeam() {
        // bestehend aus F-03, hier nur als Kontrolle, dass Spieltage weiterhin ein Team haben
        JsonPath json = termin(anmelden("alex"), T04).statusCode(200).extract().jsonPath();
        assertThat(json.getList("teams.id"), containsInAnyOrder(TEAM_1));
    }
}
