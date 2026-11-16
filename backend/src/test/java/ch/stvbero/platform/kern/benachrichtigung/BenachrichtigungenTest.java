package ch.stvbero.platform.kern.benachrichtigung;

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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;

/**
 * F-06: Benachrichtigungen (BEN-01 bis BEN-05, BEN-07, BEN-11 bis BEN-13), Szenarien S5, S6, S9, S10 aus
 * docs/testdaten.md. Standard-«Jetzt»: Mo 16.11.2026 06:00.
 */
@QuarkusTest
class BenachrichtigungenTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String MARC = "20000000-0000-4000-8000-000000000003";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String LEA = "20000000-0000-4000-8000-000000000006";
    static final String MIA = "20000000-0000-4000-8000-000000000007";
    static final String SARA = "20000000-0000-4000-8000-000000000008";
    static final String NINA = "20000000-0000-4000-8000-000000000009";
    static final String JANA = "20000000-0000-4000-8000-000000000010";
    static final String KIM = "20000000-0000-4000-8000-000000000011";
    static final String EVA = "20000000-0000-4000-8000-000000000012";
    static final String LARA = "20000000-0000-4000-8000-000000000013";
    static final String ZOE = "20000000-0000-4000-8000-000000000014";
    static final String ELLA = "20000000-0000-4000-8000-000000000015";
    static final String T01 = "40000000-0000-4000-8000-000000000001";
    static final String T03 = "40000000-0000-4000-8000-000000000003";
    static final String T04 = "40000000-0000-4000-8000-000000000004";
    static final String T05 = "40000000-0000-4000-8000-000000000005";
    static final String ANNA_ABSAGE_T03 = "90000000-0000-4000-8000-000000000002";
    static final String LEA_ABSAGE_T03 = "90000000-0000-4000-8000-000000000003";

    /** Alle Mitglieder von Team 1 und Team 2 ausser Petra. */
    static final String[] TEAM_1_UND_2_OHNE_PETRA = {ANNA, LEA, MIA, SARA, NINA, JANA, KIM, MARC, EVA, LARA, ZOE, ELLA};

    @Inject
    TestKanal testKanal;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
        testKanal.erhalten().clear();
    }

    private static void uhr(String jetzt) {
        given().contentType(ContentType.JSON).body("{\"jetzt\":\"" + jetzt + "\"}").put("/api/test/uhr").then().statusCode(204);
    }

    private static ValidatableResponse eigene(String sitzung, String query) {
        return given().cookie("stv_sitzung", sitzung).get("/api/benachrichtigungen" + query).then();
    }

    private static ValidatableResponse gelesen(String sitzung, String id) {
        return given().cookie("stv_sitzung", sitzung).post("/api/benachrichtigungen/" + id + "/gelesen").then();
    }

    private static void antworten(String sitzung, String termin, String status) {
        given().cookie("stv_sitzung", sitzung).contentType(ContentType.JSON).body("{\"status\":\"" + status + "\"}")
                .put("/api/termine/" + termin + "/antworten/ich").then().statusCode(200);
    }

    /** Neu erzeugte Benachrichtigungen einer Art zu einem Termin (ohne Seed). */
    private static List<Map<String, Object>> neue(String art, String termin) {
        JsonPath json = given().get("/api/test/benachrichtigungen").then().statusCode(200).extract().jsonPath();
        return json.<Map<String, Object>>getList("findAll { it.art == '" + art + "' && it.terminId == '" + termin
                + "' && !it.id.startsWith('90000000') }");
    }

    @SuppressWarnings("unchecked")
    private static List<String> empfaenger(List<Map<String, Object>> liste) {
        return liste.stream().map(b -> (String) ((Map<String, Object>) b.get("empfaenger")).get("id")).toList();
    }

    private static List<String> texte(List<Map<String, Object>> liste) {
        return liste.stream().map(b -> (String) b.get("text")).toList();
    }

    // ------------------------------------------------------------------ Liste und gelesen (K1, K2)

    @Test
    void k1EigeneNeuesteZuerstMitUngelesenen() {
        String lea = anmelden("lea");
        JsonPath json = eigene(lea, "").statusCode(200).extract().jsonPath();
        assertThat(json.getList("art"), contains("AUFGEBOT_VEROEFFENTLICHT", "TERMIN_ABGESAGT"));
        assertThat(json.getList("terminId"), contains(T05, T03));
        assertThat(json.getList("empfaenger.id"), everyItem(equalTo(LEA)));
        assertThat(json.getList("gelesen"), contains(false, false));
        assertThat(json.getList("text"), contains("Das Aufgebot für Spieltag Sa 28.11. ist da: Du bist nicht aufgeboten.",
                "Training Di 24.11. ist abgesagt: Halle besetzt"));

        // Petra hat ihre gelesen; ungelesen=true lässt sie weg
        assertThat(eigene(anmelden("petra"), "").extract().jsonPath().getList("gelesen"), contains(true));
        assertThat(eigene(anmelden("petra"), "?ungelesen=true").extract().jsonPath().getList("id"), empty());
        // Alex ist in keinem Team
        assertThat(eigene(anmelden("alex"), "").extract().jsonPath().getList("id"), empty());
    }

    @Test
    void k2AlsGelesenMarkieren() {
        String anna = anmelden("anna");
        assertThat(eigene(anna, "?ungelesen=true").extract().jsonPath().getList("id"), contains(ANNA_ABSAGE_T03));
        gelesen(anna, ANNA_ABSAGE_T03).statusCode(204);
        assertThat(eigene(anna, "?ungelesen=true").extract().jsonPath().getList("id"), empty());
        assertThat(eigene(anna, "").extract().jsonPath().getList("gelesen"), contains(true));
        // Nochmals markieren ändert nichts
        gelesen(anna, ANNA_ABSAGE_T03).statusCode(204);
    }

    @Test
    void rr07FremdeBenachrichtigungNicht() {
        gelesen(anmelden("anna"), LEA_ABSAGE_T03).statusCode(403).body("regel", equalTo("RR-07"));
        // Leas Benachrichtigung bleibt ungelesen
        assertThat(eigene(anmelden("lea"), "?ungelesen=true").extract().jsonPath().getList("id"), hasSize(2));
        gelesen(anmelden("anna"), "90000000-0000-4000-8000-000000000099").statusCode(404);
        given().get("/api/benachrichtigungen").then().statusCode(401);
        given().post("/api/benachrichtigungen/" + ANNA_ABSAGE_T03 + "/gelesen").then().statusCode(401);
    }

    // ------------------------------------------------------------------ Auslöser

    @Test
    void s5KurzfristigeAbsageBenachrichtigtPetraNichtMarc() {
        uhr("2026-11-16T19:00:00+01:00");
        antworten(anmelden("anna"), T01, "NICHT_DABEI");
        List<Map<String, Object>> neu = neue("KURZFRISTIGE_ABSAGE", T01);
        assertThat(empfaenger(neu), contains(PETRA));
        assertThat(texte(neu), contains("Anna Muster hat sich kurzfristig für Training Di 17.11. abgemeldet."));

        // Weiterer Wechsel, der kurzfristig bleibt: keine zweite Benachrichtigung
        given().cookie("stv_sitzung", anmelden("anna")).contentType(ContentType.JSON)
                .body("{\"status\":\"VIELLEICHT\",\"kommentar\":\"Evtl. später\"}")
                .put("/api/termine/" + T01 + "/antworten/ich").then().statusCode(200).body("kurzfristig", equalTo(true));
        assertThat(neue("KURZFRISTIGE_ABSAGE", T01), hasSize(1));
    }

    @Test
    void ben02VorDerFristKeineBenachrichtigung() {
        antworten(anmelden("anna"), T01, "NICHT_DABEI");
        assertThat(neue("KURZFRISTIGE_ABSAGE", T01), empty());
    }

    @Test
    void s6ErinnerungGenauEinmalAnNinaUndElla() {
        uhr("2026-11-16T06:29:00+01:00");
        assertThat(neue("ERINNERUNG", T01), empty());

        uhr("2026-11-16T06:31:00+01:00");
        List<Map<String, Object>> neu = neue("ERINNERUNG", T01);
        assertThat(empfaenger(neu), containsInAnyOrder(NINA, ELLA));
        assertThat(texte(neu), everyItem(equalTo(
                "Erinnerung: Bitte gib bis Mo 16.11. 18:30 Bescheid, ob du bei Training Di 17.11. dabei bist.")));
        // Sonst niemand, auch nicht zu anderen Terminen
        JsonPath alle = given().get("/api/test/benachrichtigungen").then().extract().jsonPath();
        assertThat(alle.getList("findAll { it.art == 'ERINNERUNG' }"), hasSize(2));

        // BEN-13: erneute Prüfungen erinnern nicht nochmals
        uhr("2026-11-16T07:00:00+01:00");
        uhr("2026-11-16T18:00:00+01:00");
        assertThat(neue("ERINNERUNG", T01), hasSize(2));
        // Nina sieht sie in ihrer Liste
        assertThat(eigene(anmelden("nina"), "?ungelesen=true").extract().jsonPath().getList("art"),
                contains("ERINNERUNG", "TERMIN_ABGESAGT"));
    }

    @Test
    void ben01NachDerFristKeineErinnerungMehr() {
        uhr("2026-11-16T18:31:00+01:00");
        assertThat(neue("ERINNERUNG", T01), empty());
    }

    @Test
    void s9VeroeffentlichenBenachrichtigtAlleSpielerinnenDesTeams() {
        given().cookie("stv_sitzung", anmelden("petra")).post("/api/termine/" + T04 + "/aufgebot/veroeffentlichen")
                .then().statusCode(200);
        List<Map<String, Object>> neu = neue("AUFGEBOT_VEROEFFENTLICHT", T04);
        assertThat(empfaenger(neu), containsInAnyOrder(ANNA, LEA, MIA, SARA, NINA, JANA, KIM));
        assertThat(texte(neu.stream().filter(b -> empfaenger(List.of(b)).contains(ANNA)).toList()),
                contains("Das Aufgebot für Spieltag So 22.11. ist da: Du bist aufgeboten."));
        assertThat(texte(neu.stream().filter(b -> empfaenger(List.of(b)).contains(KIM)).toList()),
                contains("Das Aufgebot für Spieltag So 22.11. ist da: Du bist nicht aufgeboten."));
    }

    @Test
    void s10AbmeldungEinerAufgebotenenBenachrichtigtDieCoaches() {
        antworten(anmelden("eva"), T05, "NICHT_DABEI");
        List<Map<String, Object>> neu = neue("AUFGEBOTENE_ABGEMELDET", T05);
        assertThat(empfaenger(neu), contains(MARC));
        assertThat(texte(neu), contains("Eva Steiner ist für Spieltag Sa 28.11. aufgeboten und hat sich abgemeldet."));
        assertThat(neue("KURZFRISTIGE_ABSAGE", T05), empty());

        // Lea ist nicht aufgeboten: keine Benachrichtigung
        antworten(anmelden("lea"), T05, "NICHT_DABEI");
        assertThat(neue("AUFGEBOTENE_ABGEMELDET", T05), hasSize(1));
    }

    @Test
    void ben05AenderungEinesVeroeffentlichtenAufgebotsNurAnBetroffene() {
        String petra = anmelden("petra");
        // Im Entwurf: keine Benachrichtigung
        aufgebotSetzen(petra, T04, ANNA, LEA, MIA, SARA, JANA, NINA);
        assertThat(neue("AUFGEBOT_GEAENDERT", T04), empty());

        given().cookie("stv_sitzung", petra).post("/api/termine/" + T04 + "/aufgebot/veroeffentlichen").then().statusCode(200);
        // Mia fällt heraus, Kim kommt dazu
        aufgebotSetzen(petra, T04, ANNA, LEA, SARA, JANA, NINA, KIM);
        List<Map<String, Object>> neu = neue("AUFGEBOT_GEAENDERT", T04);
        assertThat(empfaenger(neu), containsInAnyOrder(MIA, KIM));
        assertThat(texte(neu), containsInAnyOrder(
                "Das Aufgebot für Spieltag So 22.11. wurde geändert: Du bist nicht mehr aufgeboten.",
                "Das Aufgebot für Spieltag So 22.11. wurde geändert: Du bist jetzt aufgeboten."));
    }

    @Test
    void ben07AbsageBenachrichtigtAlleMitgliederDerBeteiligtenTeams() {
        given().cookie("stv_sitzung", anmelden("petra")).contentType(ContentType.JSON).body("{\"grund\":\"Halle gesperrt\"}")
                .post("/api/termine/" + T01 + "/absagen").then().statusCode(200);
        List<Map<String, Object>> neu = neue("TERMIN_ABGESAGT", T01);
        assertThat(empfaenger(neu), containsInAnyOrder(TEAM_1_UND_2_OHNE_PETRA));
        assertThat(texte(neu), everyItem(equalTo("Training Di 17.11. ist abgesagt: Halle gesperrt")));
    }

    @Test
    void ben07VerschiebungBenachrichtigtAlleMitgliederDerBeteiligtenTeams() {
        String petra = anmelden("petra");
        // Nur das Ende ändert: keine Verschiebung
        aendern(petra, "Training", "2026-11-17T18:30:00+01:00", "2026-11-17T20:15:00+01:00");
        assertThat(neue("TERMIN_VERSCHOBEN", T01), empty());

        aendern(petra, "Training", "2026-11-18T19:00:00+01:00", "2026-11-18T20:30:00+01:00");
        List<Map<String, Object>> neu = neue("TERMIN_VERSCHOBEN", T01);
        assertThat(empfaenger(neu), containsInAnyOrder(TEAM_1_UND_2_OHNE_PETRA));
        assertThat(texte(neu), everyItem(equalTo("Training Di 17.11. ist verschoben auf Mi 18.11. 19:00.")));
    }

    @Test
    void ben11JederKanalErhaeltDieNachricht() {
        antworten(anmelden("eva"), T05, "NICHT_DABEI");
        assertThat(testKanal.erhalten(), hasSize(1));
        Nachricht n = testKanal.erhalten().get(0);
        assertThat(n.art(), equalTo(BenachrichtigungArt.AUFGEBOTENE_ABGEMELDET));
        assertThat(n.empfaengerId().toString(), equalTo(MARC));
        assertThat(n.terminId().toString(), equalTo(T05));
    }

    private static void aufgebotSetzen(String sitzung, String termin, String... personen) {
        String ids = String.join(",", List.of(personen).stream().map(p -> "\"" + p + "\"").toList());
        given().cookie("stv_sitzung", sitzung).contentType(ContentType.JSON).body("{\"personIds\":[" + ids + "]}")
                .put("/api/termine/" + termin + "/aufgebot").then().statusCode(200);
    }

    private static void aendern(String sitzung, String titel, String beginn, String ende) {
        given().cookie("stv_sitzung", sitzung).contentType(ContentType.JSON).body("""
                        {"typ":"TRAINING","titel":"%s","beginn":"%s","ende":"%s","teamIds":["%s","%s"],
                         "ort":{"name":"Sporthalle Nord"}}""".formatted(titel, beginn, ende, TEAM_1, TEAM_2))
                .put("/api/termine/" + T01).then().statusCode(200);
    }
}
