package ch.stvbero.platform.core.event;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static ch.stvbero.platform.core.auth.AuthTest.login;
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
class ResponsesTest {

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
    static final String GROUP_LINE_1_TEAM_1 = "30000000-0000-4000-8000-000000000001";

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static ValidatableResponse overview(String session, String event) {
        return given().cookie("stv_session", session).get("/api/events/" + event + "/overview").then();
    }

    private static ValidatableResponse set(String session, String event, String person, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .put("/api/events/" + event + "/responses/" + person).then();
    }

    private static void clock(String now) {
        given().contentType(ContentType.JSON).body("{\"now\":\"" + now + "\"}").put("/api/test/clock").then().statusCode(204);
    }

    private static List<String> names(JsonPath json, String path) {
        return json.getList(path + ".person.firstName");
    }

    // ------------------------------------------------------------------ Übersicht (S1)

    @Test
    void s1OverviewFromEvent01() {
        JsonPath json = overview(login("petra"), T01).statusCode(200).extract().jsonPath();

        // ANM-23, ANM-32: Gesamttotal mit jeder Spielerin einmal (Lea zählt einmal)
        assertThat(json.getMap("total"), equalTo(Map.of("attending", 7, "declined", 2, "maybe", 1, "pending", 2)));

        // ANM-20: nach Team, Teams nach Name
        assertThat(json.getList("teams.team.name"), contains("Team 1", "Team 2"));
        assertThat(json.getMap("teams[0].counts"), equalTo(Map.of("attending", 5, "declined", 1, "maybe", 0, "pending", 1)));
        assertThat(json.getMap("teams[1].counts"), equalTo(Map.of("attending", 3, "declined", 1, "maybe", 1, "pending", 1)));

        // ANM-21: Linien in ihrer Reihenfolge, dann Goalies, dann «Ohne Linie»; Personen nach Nachname
        assertThat(json.getList("teams[0].groups.name"), contains("Linie 1", "Linie 2", "Goalies", "Ohne Linie"));
        assertThat(json.getList("teams[0].groups.type"), contains("LINE", "LINE", "GOALIES", "NO_LINE"));
        assertThat(json.getString("teams[0].groups[0].groupId"), equalTo(GROUP_LINE_1_TEAM_1));
        assertThat(json.getString("teams[0].groups[3].groupId"), nullValue());
        assertThat(names(json, "teams[0].groups[0].entries"), contains("Mia", "Lea", "Anna"));
        assertThat(json.getList("teams[0].groups[0].entries.response.status"), contains("DECLINED", "ATTENDING", "ATTENDING"));
        assertThat(names(json, "teams[0].groups[1].entries"), contains("Sara", "Nina"));
        assertThat(json.getList("teams[0].groups[1].entries.response.status"), contains("ATTENDING", "PENDING"));
        assertThat(names(json, "teams[0].groups[2].entries"), contains("Jana"));
        assertThat(names(json, "teams[0].groups[3].entries"), contains("Kim"));

        // ANM-31: Petra als Spielerin in Team 2, Lea in beiden Teams; leere Gruppen fehlen nicht hier, Team 2 hat keine «Ohne Linie»
        assertThat(json.getList("teams[1].groups.name"), contains("Linie 1", "Linie 2", "Goalies"));
        assertThat(names(json, "teams[1].groups[0].entries"), contains("Petra", "Eva", "Lara"));
        assertThat(json.getList("teams[1].groups[0].entries.response.status"), contains("ATTENDING", "MAYBE", "ATTENDING"));
        assertThat(names(json, "teams[1].groups[1].entries"), contains("Lea", "Zoe"));
        assertThat(names(json, "teams[1].groups[2].entries"), contains("Ella"));

        // ANM-22: Coach-Block mit Team, nicht unter den Spielerinnen von Team 1
        assertThat(names(json, "coaches"), contains("Petra", "Marc"));
        assertThat(json.getList("coaches.team.name"), contains("Team 1", "Team 2"));
        assertThat(json.getList("coaches.response.status"), contains("ATTENDING", "DECLINED"));
    }

    @Test
    void anm21EmptyGroupsMissing() {
        // Termin 02 nur Team 1; nach S14 ist «Ohne Linie» leer
        String petra = login("petra");
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"groupId\":\"30000000-0000-4000-8000-000000000002\"}")
                .put("/api/teams/" + TEAM_1 + "/assignments/20000000-0000-4000-8000-000000000011")
                .then().statusCode(204);
        JsonPath json = overview(petra, T01).statusCode(200).extract().jsonPath();
        assertThat(json.getList("teams[0].groups.name"), contains("Linie 1", "Linie 2", "Goalies"));
        assertThat(names(json, "teams[0].groups[1].entries"), contains("Kim", "Sara", "Nina"));
    }

    // ------------------------------------------------------------------ Kommentare (S4)

    @Test
    void s4CommentsNotForPlayers() {
        JsonPath json = overview(login("anna"), T01).statusCode(200).extract().jsonPath();
        List<Map<String, Object>> all = json.getList("teams.groups.flatten().entries.flatten().response");
        assertThat(all, everyItem(not(hasKey("comment"))));
        assertThat(json.getList("coaches.response"), everyItem(not(hasKey("comment"))));
    }

    @Test
    void s4CommentsForCoachOnlyInOwnTeam() {
        JsonPath json = overview(login("petra"), T01).statusCode(200).extract().jsonPath();
        assertThat(json.getString("teams[0].groups[0].entries[0].response.comment"), equalTo("Krank"));
        // RR-06: Petra ist in Team 2 Spielerin, sieht Evas Kommentar dort nicht, auch nicht Marcs
        assertThat(json.getMap("teams[1].groups[0].entries[1].response"), not(hasKey("comment")));
        assertThat(json.getMap("coaches[1].response"), not(hasKey("comment")));

        JsonPath marc = overview(login("marc"), T01).statusCode(200).extract().jsonPath();
        assertThat(marc.getString("teams[1].groups[0].entries[1].response.comment"), equalTo("Komme evtl. später"));
        assertThat(marc.getString("coaches[1].response.comment"), equalTo("Ferien"));
        assertThat(marc.getMap("teams[0].groups[0].entries[0].response"), not(hasKey("comment")));
    }

    @Test
    void anm07OwnCommentAndAdminSeesAll() {
        JsonPath mia = overview(login("mia"), T01).statusCode(200).extract().jsonPath();
        assertThat(mia.getString("teams[0].groups[0].entries[0].response.comment"), equalTo("Krank"));
        assertThat(mia.getMap("teams[1].groups[0].entries[1].response"), not(hasKey("comment")));
        given().cookie("stv_session", login("mia")).get("/api/events/" + T01).then().statusCode(200)
                .body("myResponse.comment", equalTo("Krank"));

        JsonPath alex = overview(login("alex"), T01).statusCode(200).extract().jsonPath();
        assertThat(alex.getString("teams[1].groups[0].entries[1].response.comment"), equalTo("Komme evtl. später"));
    }

    @Test
    void rr02OverviewOnlyForMembersTheTeams() {
        overview(login("ruth"), T01).statusCode(403);
        overview(login("anna"), "40000000-0000-4000-8000-000000000099").statusCode(404);
    }

    // ------------------------------------------------------------------ Eigene Antwort (S2, S3, S5)

    @Test
    void s2ResponseWithATap() {
        String nina = login("nina");
        set(nina, T01, "me", "{\"status\":\"ATTENDING\"}").statusCode(200)
                .body("status", equalTo("ATTENDING")).body("lateCancellation", equalTo(false))
                .body("changedAt", equalTo("2026-11-16T06:00:00+01:00"));
        JsonPath json = overview(nina, T01).statusCode(200).extract().jsonPath();
        assertThat(json.getMap("teams[0].counts"), equalTo(Map.of("attending", 6, "declined", 1, "maybe", 0, "pending", 0)));
        assertThat(json.getMap("total"), equalTo(Map.of("attending", 8, "declined", 2, "maybe", 1, "pending", 1)));
        // ANM-09: bleibt nach Neuladen, auch in der Terminliste
        given().cookie("stv_session", nina).get("/api/events/" + T01).then().statusCode(200)
                .body("myResponse.status", equalTo("ATTENDING")).body("counts.attending", equalTo(8));
    }

    @Test
    void anm09ResponseAnyTimeChangeable() {
        String anna = login("anna");
        set(anna, T01, "me", "{\"status\":\"DECLINED\",\"comment\":\"  Arzttermin \"}").statusCode(200)
                .body("status", equalTo("DECLINED")).body("comment", equalTo("Arzttermin"));
        set(anna, T01, "me", "{\"status\":\"ATTENDING\"}").statusCode(200)
                .body("status", equalTo("ATTENDING")).body("$", not(hasKey("comment")));
    }

    @Test
    void s3MaybeWithoutComment() {
        String nina = login("nina");
        set(nina, T01, "me", "{\"status\":\"MAYBE\"}").statusCode(422)
                .contentType("application/problem+json")
                .body("rule", equalTo("ANM-04")).body("detail", equalTo(ResponseRules.COMMENT_REQUIRED))
                .body("fields[0].field", equalTo("comment"));
        set(nina, T01, "me", "{\"status\":\"MAYBE\",\"comment\":\" \"}").statusCode(422);
        // Nichts gespeichert
        given().cookie("stv_session", nina).get("/api/events/" + T01).then().body("myResponse.status", equalTo("PENDING"));
        set(nina, T01, "me", "{\"status\":\"MAYBE\",\"comment\":\"Knie\"}").statusCode(200)
                .body("comment", equalTo("Knie"));
    }

    @Test
    void anm03OnlyThreeResponses() {
        String nina = login("nina");
        set(nina, T01, "me", "{\"status\":\"PENDING\"}").statusCode(400);
        set(nina, T01, "me", "{}").statusCode(400);
        set(nina, T01, "me", "{\"status\":\"ATTENDING\",\"comment\":\"" + "x".repeat(201) + "\"}").statusCode(400);
    }

    @Test
    void s5LateCancellation() {
        clock("2026-11-16T19:00:00+01:00");
        String anna = login("anna");
        set(anna, T01, "me", "{\"status\":\"DECLINED\"}").statusCode(200).body("lateCancellation", equalTo(true));
        given().cookie("stv_session", login("petra")).get("/api/events/" + T01 + "/overview").then()
                .body("teams[0].groups[0].entries[2].person.firstName", equalTo("Anna"))
                .body("teams[0].groups[0].entries[2].response.lateCancellation", equalTo(true));
        // Zurück auf «dabei»: keine kurzfristige Absage mehr
        set(anna, T01, "me", "{\"status\":\"ATTENDING\"}").statusCode(200).body("lateCancellation", equalTo(false));
    }

    @Test
    void anm10BeforeTheDeadlineNotLate() {
        set(login("anna"), T01, "me", "{\"status\":\"DECLINED\"}").statusCode(200)
                .body("lateCancellation", equalTo(false));
    }

    @Test
    void anm02OneResponsePerPersonAndEvent() {
        // Lea in Team 1 und Team 2: eine Antwort, in beiden Teams sichtbar (ANM-30)
        String lea = login("lea");
        set(lea, T01, "me", "{\"status\":\"DECLINED\"}").statusCode(200);
        JsonPath json = overview(lea, T01).extract().jsonPath();
        assertThat(json.getString("teams[0].groups[0].entries[1].response.status"), equalTo("DECLINED"));
        assertThat(json.getString("teams[1].groups[1].entries[0].response.status"), equalTo("DECLINED"));
        assertThat(json.getInt("total.declined"), equalTo(3));
        assertThat(json.getInt("total.attending"), equalTo(6));
    }

    @Test
    void anm01CoachRespondsLikePlayer() {
        String marc = login("marc");
        set(marc, T01, "me", "{\"status\":\"ATTENDING\"}").statusCode(200);
        JsonPath json = overview(marc, T01).extract().jsonPath();
        assertThat(json.getString("coaches[1].response.status"), equalTo("ATTENDING"));
        // Coaches zählen nicht (ANM-23)
        assertThat(json.getInt("total.attending"), equalTo(7));
    }

    @Test
    void rr02NoResponseOnOtherEvents() {
        set(login("ruth"), T01, "me", "{\"status\":\"ATTENDING\"}").statusCode(403);
        // Admin ohne Team antwortet nicht für sich
        set(login("alex"), T01, "me", "{\"status\":\"ATTENDING\"}").statusCode(403);
        set(login("anna"), "40000000-0000-4000-8000-000000000099", "me", "{\"status\":\"ATTENDING\"}").statusCode(404);
    }

    @Test
    void ter06CancelledEventTakesNoResponse() {
        set(login("anna"), T03, "me", "{\"status\":\"ATTENDING\"}").statusCode(409)
                .body("rule", equalTo("TER-06")).body("detail", equalTo(ResponseRules.CANCELLED));
        set(login("petra"), T03, ANNA, "{\"status\":\"ATTENDING\"}").statusCode(409);
    }

    @Test
    void ter06PastEventTakesNoResponse() {
        set(login("anna"), T07, "me", "{\"status\":\"ATTENDING\"}").statusCode(409).body("rule", equalTo("TER-06"));
    }

    // ------------------------------------------------------------------ Stellvertretend (S7, S8)

    @Test
    void s7OnBehalfResponses() {
        String petra = login("petra");
        set(petra, T01, NINA, "{\"status\":\"DECLINED\",\"comment\":\"Hat angerufen\"}").statusCode(200)
                .body("status", equalTo("DECLINED"))
                .body("enteredBy.firstName", equalTo("Petra")).body("enteredBy.lastName", equalTo("Keller"));
        // ANM-12: für alle sichtbar, wer eingetragen hat; Nina sieht es bei ihrer Antwort
        given().cookie("stv_session", login("nina")).get("/api/events/" + T01).then().statusCode(200)
                .body("myResponse.status", equalTo("DECLINED"))
                .body("myResponse.enteredBy.firstName", equalTo("Petra"))
                .body("myResponse.comment", equalTo("Hat angerufen"));
        JsonPath anna = overview(login("anna"), T01).extract().jsonPath();
        assertThat(anna.getString("teams[0].groups[1].entries[1].response.enteredBy.lastName"), equalTo("Keller"));
        // Antwortet Nina danach selbst, fällt «eingetragen von» weg
        set(login("nina"), T01, "me", "{\"status\":\"ATTENDING\"}").statusCode(200).body("$", not(hasKey("enteredBy")));
    }

    @Test
    void s7OnBehalfFollowsSameRules() {
        String petra = login("petra");
        set(petra, T01, NINA, "{\"status\":\"MAYBE\"}").statusCode(422).body("rule", equalTo("ANM-04"));
        clock("2026-11-16T19:00:00+01:00");
        set(petra, T01, ANNA, "{\"status\":\"DECLINED\"}").statusCode(200).body("lateCancellation", equalTo(true));
    }

    @Test
    void s8NoOnBehalfForOtherTeams() {
        String marc = login("marc");
        set(marc, T01, ANNA, "{\"status\":\"DECLINED\"}").statusCode(403)
                .contentType("application/problem+json").body("rule", equalTo("RR-07"));
        // Lea ist auch in Team 2 Spielerin: Marc darf
        set(marc, T01, LEA, "{\"status\":\"DECLINED\"}").statusCode(200);
        // Nicht für Coaches (Petra ist in Team 2 Spielerin: das geht)
        set(marc, T01, PETRA, "{\"status\":\"DECLINED\"}").statusCode(200);
        set(login("petra"), T01, MARC, "{\"status\":\"ATTENDING\"}").statusCode(403);
        // RR-06: Petra ist in Team 2 nur Spielerin
        set(login("petra"), T01, EVA, "{\"status\":\"ATTENDING\"}").statusCode(403);
        // Spielerinnen vertreten niemanden
        set(login("anna"), T01, MIA, "{\"status\":\"ATTENDING\"}").statusCode(403);
        // Nicht Mitglied eines beteiligten Teams
        set(login("ruth"), T01, ANNA, "{\"status\":\"ATTENDING\"}").statusCode(403);
        // Unveränderte Antwort von Anna
        given().cookie("stv_session", login("anna")).get("/api/events/" + T01).then()
                .body("myResponse.status", equalTo("ATTENDING"));
    }

    @Test
    void onBehalfForPersonWithoutTeamInEvent() {
        set(login("petra"), T01, TINA, "{\"status\":\"ATTENDING\"}").statusCode(404);
        set(login("petra"), T01, "20000000-0000-4000-8000-000000000099", "{\"status\":\"ATTENDING\"}").statusCode(404);
        set(login("alex"), T01, TINA, "{\"status\":\"ATTENDING\"}").statusCode(404);
    }

    @Test
    void rr04AdminActsForEvery() {
        String alex = login("alex");
        set(alex, T01, MARC, "{\"status\":\"ATTENDING\"}").statusCode(200).body("enteredBy.firstName", equalTo("Alex"));
        set(alex, T02, ANNA, "{\"status\":\"DECLINED\"}").statusCode(200);
    }

    // ------------------------------------------------------------------ Seed

    @Test
    void seedResponsesByTestData() {
        String anna = login("anna");
        List<Map<String, Object>> list = given().cookie("stv_session", anna).get("/api/events").then().statusCode(200)
                .extract().jsonPath().getList("findAll { it.id in ['" + T01 + "','" + T02 + "'] }.myResponse");
        assertThat(list.stream().map(m -> m.get("status")).toList(), contains("ATTENDING", "ATTENDING"));
        // Termin 02: nur Anna hat geantwortet
        given().cookie("stv_session", anna).get("/api/events/" + T02).then()
                .body("counts.attending", equalTo(1)).body("counts.pending", equalTo(6));
        // Termin 06: niemand
        given().cookie("stv_session", anna).get("/api/events/40000000-0000-4000-8000-000000000006/overview").then()
                .body("total.pending", equalTo(13)).body("total.attending", equalTo(0));
        // Termin 04 und 05 (Spieltage)
        given().cookie("stv_session", login("petra")).get("/api/events/40000000-0000-4000-8000-000000000004/overview").then()
                .body("total.attending", equalTo(4)).body("total.maybe", equalTo(1)).body("total.declined", equalTo(1))
                .body("total.pending", equalTo(1)).body("coaches[0].response.status", equalTo("ATTENDING"));
        given().cookie("stv_session", login("marc")).get("/api/events/40000000-0000-4000-8000-000000000005/overview").then()
                .body("total.attending", equalTo(5)).body("total.declined", equalTo(1)).body("total.pending", equalTo(0))
                .body("coaches[0].response.status", equalTo("ATTENDING"));
        assertThat(list, not(empty()));
    }
}
