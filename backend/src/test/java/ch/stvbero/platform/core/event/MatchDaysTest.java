package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.error.Concurrently;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ch.stvbero.platform.core.auth.AuthTest.login;
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
class MatchDaysTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String NINA = "20000000-0000-4000-8000-000000000009";
    static final String KIM = "20000000-0000-4000-8000-000000000011";
    static final String TINA = "20000000-0000-4000-8000-000000000016";
    static final String T01 = "40000000-0000-4000-8000-000000000001";
    static final String T04 = "40000000-0000-4000-8000-000000000004";
    static final String T05 = "40000000-0000-4000-8000-000000000005";
    static final String MATCH_01 = "50000000-0000-4000-8000-000000000001";
    static final String MATCH_02 = "50000000-0000-4000-8000-000000000002";
    static final String MATCH_03 = "50000000-0000-4000-8000-000000000003";

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static ValidatableResponse event(String session, String id) {
        return given().cookie("stv_session", session).get("/api/events/" + id).then();
    }

    private static ValidatableResponse callUp(String session, String id) {
        return given().cookie("stv_session", session).get("/api/events/" + id + "/call-up").then();
    }

    private static ValidatableResponse setCallUp(String session, String id, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .put("/api/events/" + id + "/call-up").then();
    }

    private static ValidatableResponse publish(String session, String id) {
        return given().cookie("stv_session", session).post("/api/events/" + id + "/call-up/publish").then();
    }

    private static ValidatableResponse matchAdd(String session, String event, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .post("/api/events/" + event + "/matches").then();
    }

    private static ValidatableResponse matchUpdate(String session, String event, String match, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .put("/api/events/" + event + "/matches/" + match).then();
    }

    private static ValidatableResponse matchDelete(String session, String event, String match) {
        return given().cookie("stv_session", session).delete("/api/events/" + event + "/matches/" + match).then();
    }

    private static void clock(String now) {
        given().contentType(ContentType.JSON).body("{\"now\":\"" + now + "\"}").put("/api/test/clock").then().statusCode(204);
    }

    private static String ids(String... ids) {
        return "{\"personIds\":[" + String.join(",", List.of(ids).stream().map(i -> "\"" + i + "\"").toList()) + "]}";
    }

    // ------------------------------------------------------------------ Seed (Kriterium 1)

    @Test
    void seedEvent04WithMatchesMeetingPointAndDraft() {
        JsonPath json = event(login("petra"), T04).statusCode(200).extract().jsonPath();
        // AUF-14: nach Anspielzeit
        assertThat(json.getList("matches.id"), contains(MATCH_01, MATCH_02));
        assertThat(json.getList("matches.opponent"), contains("UHC Musterdorf", "Unihockey Beispielstadt"));
        assertThat(json.getList("matches.home"), contains(true, false));
        assertThat(json.getList("matches.startTime"), contains("2026-11-22T09:30:00+01:00", "2026-11-22T11:45:00+01:00"));
        assertThat(json.getList("matches.source"), everyItem(equalTo("SWISS_UNIHOCKEY")));
        assertThat(json.getList("matches.suMatchId"), contains(5000001, 5000002));
        assertThat(json.getString("meetingPoint.location"), equalTo("Parkplatz Sporthalle Nord"));
        assertThat(json.getString("meetingPoint.time"), equalTo("2026-11-22T08:00:00+01:00"));
        assertThat(json.getString("location.mapsUrl"), equalTo("https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf"));
        assertThat(json.getString("notes"), equalTo("Weisse Trikots mitnehmen"));
        assertThat(json.getString("callUpStatus"), equalTo("DRAFT"));
        // Petra ist in Team 1 Coach: kein eigener Aufgebotsstatus
        assertThat(json.get("myCallUp"), nullValue());
    }

    @Test
    void seedEvent05PublishedWithAMatch() {
        JsonPath json = event(login("marc"), T05).statusCode(200).extract().jsonPath();
        assertThat(json.getList("matches.id"), contains(MATCH_03));
        assertThat(json.getString("matches[0].source"), equalTo("MANUAL"));
        assertThat(json.getString("callUpStatus"), equalTo("PUBLISHED"));
        // AUF-24: jede Spielerin sieht ihren Status; Petra ist in Team 2 Spielerin
        assertThat(event(login("eva"), T05).extract().path("myCallUp"), equalTo("CALLED_UP"));
        assertThat(event(login("petra"), T05).extract().path("myCallUp"), equalTo("CALLED_UP"));
        assertThat(event(login("lea"), T05).extract().path("myCallUp"), equalTo("NOT_CALLED_UP"));
    }

    @Test
    void auf23DraftStaysForPlayersHidden() {
        JsonPath json = event(login("anna"), T04).statusCode(200).extract().jsonPath();
        assertThat(json.getString("callUpStatus"), equalTo("NONE"));
        assertThat(json.getString("myCallUp"), equalTo("NO_CALL_UP_YET"));
    }

    @Test
    void practiceHasNoMatchesAndNoCallUp() {
        JsonPath json = event(login("anna"), T01).statusCode(200).extract().jsonPath();
        assertThat(json.get("matches"), nullValue());
        assertThat(json.get("callUpStatus"), nullValue());
        assertThat(json.get("myCallUp"), nullValue());
    }

    // ------------------------------------------------------------------ Aufgebot lesen (Kriterium 4, 7)

    @Test
    void auf21Auf22SelectionByLinesWithResponseStatus() {
        JsonPath json = callUp(login("petra"), T04).statusCode(200).extract().jsonPath();
        assertThat(json.getString("status"), equalTo("DRAFT"));
        assertThat(json.getInt("calledUpCount"), equalTo(5));
        assertThat(json.get("publishedAt"), nullValue());
        assertThat(json.getList("groups.name"), contains("Linie 1", "Linie 2", "Goalies", "Ohne Linie"));
        assertThat(json.getList("groups.type"), contains("LINE", "LINE", "GOALIES", "NO_LINE"));
        assertThat(json.getList("groups[0].entries.person.firstName"), contains("Mia", "Lea", "Anna"));
        assertThat(json.getList("groups[0].entries.response.status"), contains("MAYBE", "ATTENDING", "ATTENDING"));
        assertThat(json.getString("groups[0].entries[0].response.comment"), equalTo("Muss evtl. arbeiten"));
        assertThat(json.getList("groups[0].entries.calledUp"), contains(true, true, true));
        assertThat(json.getList("groups[1].entries.person.firstName"), contains("Sara", "Nina"));
        assertThat(json.getList("groups[1].entries.response.status"), contains("ATTENDING", "PENDING"));
        assertThat(json.getList("groups[1].entries.calledUp"), contains(true, false));
        assertThat(json.getList("groups[2].entries.person.firstName"), contains("Jana"));
        assertThat(json.getList("groups[3].entries.person.firstName"), contains("Kim"));
        assertThat(json.getList("groups[3].entries.response.status"), contains("DECLINED"));
    }

    @Test
    void auf28CoachesStandNotInTheSelection() {
        JsonPath json = callUp(login("petra"), T04).statusCode(200).extract().jsonPath();
        assertThat(json.getList("groups.entries.person.id.flatten()"), not(hasItem(PETRA)));
        assertThat(json.getList("groups.entries.person.id.flatten()").size(), equalTo(7));
    }

    @Test
    void s11CallUpOnlyForCoachesTheTeamsAndAdmins() {
        callUp(login("anna"), T04).statusCode(403).body("rule", equalTo("RR-07"));
        callUp(login("marc"), T04).statusCode(403);
        setCallUp(login("anna"), T04, ids(ANNA)).statusCode(403);
        publish(login("anna"), T04).statusCode(403);
        callUp(login("alex"), T04).statusCode(200);
        callUp(login("marc"), T05).statusCode(200);
    }

    @Test
    void auf02NoCallUpForPractices() {
        callUp(login("petra"), T01).statusCode(409).body("rule", equalTo("AUF-02"));
        setCallUp(login("petra"), T01, ids(ANNA)).statusCode(409);
    }

    @Test
    void callUpUnknownEvent404() {
        callUp(login("petra"), "40000000-0000-4000-8000-000000000099").statusCode(404);
    }

    // ------------------------------------------------------------------ Aufgebot setzen und veröffentlichen

    @Test
    void auf22AlsoPendingAndDeclinedCallable() {
        String petra = login("petra");
        JsonPath json = setCallUp(petra, T04, ids(NINA, KIM)).statusCode(200).extract().jsonPath();
        assertThat(json.getInt("calledUpCount"), equalTo(2));
        assertThat(json.getString("status"), equalTo("DRAFT"));
        assertThat(json.getList("groups[1].entries.calledUp"), contains(false, true));
        assertThat(json.getList("groups[3].entries.calledUp"), contains(true));
        // bleibt gespeichert
        assertThat(callUp(petra, T04).extract().path("calledUpCount"), equalTo(2));
    }

    @Test
    void auf28CoachAndOtherAreNotCallable() {
        String petra = login("petra");
        setCallUp(petra, T04, ids(ANNA, PETRA)).statusCode(422).body("rule", equalTo("AUF-28"));
        setCallUp(petra, T04, ids(TINA)).statusCode(422);
        setCallUp(petra, T04, "{}").statusCode(400);
        assertThat(callUp(petra, T04).extract().path("calledUpCount"), equalTo(5));
    }

    @Test
    void s9PublishShowsPlayersHerStatus() {
        String petra = login("petra");
        assertThat(event(login("anna"), T04).extract().path("myCallUp"), equalTo("NO_CALL_UP_YET"));

        JsonPath json = publish(petra, T04).statusCode(200).extract().jsonPath();
        assertThat(json.getString("status"), equalTo("PUBLISHED"));
        assertThat(json.getString("publishedAt"), equalTo("2026-11-16T06:00:00+01:00"));

        assertThat(event(login("anna"), T04).extract().path("myCallUp"), equalTo("CALLED_UP"));
        assertThat(event(login("kim"), T04).extract().path("myCallUp"), equalTo("NOT_CALLED_UP"));
        assertThat(event(login("nina"), T04).extract().path("callUpStatus"), equalTo("PUBLISHED"));

        // nur einmal
        publish(petra, T04).statusCode(409).body("rule", equalTo("AUF-23"));
        // Änderung danach: bleibt veröffentlicht, Kim jetzt aufgeboten (AUF-27)
        setCallUp(petra, T04, ids(ANNA, KIM)).statusCode(200).body("status", equalTo("PUBLISHED"));
        assertThat(event(login("kim"), T04).extract().path("myCallUp"), equalTo("CALLED_UP"));
        assertThat(event(login("lea"), T04).extract().path("myCallUp"), equalTo("NOT_CALLED_UP"));
    }

    @Test
    void auf23EmptyCallUpLetsItselfNotPublish() {
        String petra = login("petra");
        setCallUp(petra, T04, ids()).statusCode(200).body("status", equalTo("NONE"));
        publish(petra, T04).statusCode(409).body("rule", equalTo("AUF-23"));
    }

    @Test
    void ter06OverOrCancelledLetsItselfNotMoreUpdate() {
        String petra = login("petra");
        clock("2026-11-22T14:00:00+01:00");
        setCallUp(petra, T04, ids(ANNA)).statusCode(409).body("rule", equalTo("TER-06"));
        publish(petra, T04).statusCode(409);
        callUp(petra, T04).statusCode(200);
    }

    @Test
    void concurrentSaveGivesNever500() {
        String petra = login("petra");
        List<Integer> codes = Concurrently.statuscodes(4,
                () -> setCallUp(petra, T04, ids(ANNA)).extract().statusCode());
        assertThat(codes, everyItem(equalTo(200)));
    }

    // ------------------------------------------------------------------ Spiele (Kriterium 2, 8)

    @Test
    void auf16MatchDayFromHandWithMatchesCreate() {
        String petra = login("petra");
        String eventId = given().cookie("stv_session", petra).contentType(ContentType.JSON).body("""
                        {"type":"MATCH_DAY","title":"Spieltag","start":"2026-12-06T10:00:00+01:00",
                         "end":"2026-12-06T14:00:00+01:00","teamIds":["%s"],
                         "location":{"name":"Halle Ost","mapsUrl":"https://maps.google.com/?q=Halle+Ost"},
                         "notes":"Rote Trikots","meetingPoint":{"time":"2026-12-06T09:00:00+01:00","location":"Bahnhof"}}
                        """.formatted(TEAM_1))
                .post("/api/events").then().statusCode(201).extract().path("id");
        matchAdd(petra, eventId, """
                {"opponent":"UHC Zwei","startTime":"2026-12-06T12:00:00+01:00","home":false}""").statusCode(201)
                .body("source", equalTo("MANUAL"));
        matchAdd(petra, eventId, """
                {"opponent":"UHC Eins","startTime":"2026-12-06T10:00:00+01:00","home":true}""").statusCode(201);

        JsonPath json = event(petra, eventId).statusCode(200).extract().jsonPath();
        assertThat(json.getList("matches.opponent"), contains("UHC Eins", "UHC Zwei"));
        assertThat(json.getList("matches.home"), contains(true, false));
        assertThat(json.getString("meetingPoint.location"), equalTo("Bahnhof"));
        assertThat(json.getString("location.mapsUrl"), equalTo("https://maps.google.com/?q=Halle+Ost"));
        assertThat(json.getString("notes"), equalTo("Rote Trikots"));
        assertThat(json.getString("callUpStatus"), equalTo("NONE"));
        assertThat(event(login("anna"), eventId).extract().path("myCallUp"), equalTo("NO_CALL_UP_YET"));
    }

    @Test
    void matchAddRules() {
        String petra = login("petra");
        String match = """
                {"opponent":"UHC Drei","startTime":"2026-11-22T12:30:00+01:00","home":true}""";
        matchAdd(login("anna"), T04, match).statusCode(403);
        matchAdd(login("marc"), T04, match).statusCode(403);
        matchAdd(petra, T01, match).statusCode(409).body("rule", equalTo("AUF-01"))
                .body("detail", equalTo("Nur ein Spieltag hat Spiele."));
        matchAdd(petra, T04, """
                {"opponent":"UHC Drei","startTime":"2026-11-23T12:30:00+01:00","home":true}""")
                .statusCode(400).body("fields[0].field", equalTo("startTime"));
        matchAdd(petra, T04, """
                {"opponent":"","startTime":"2026-11-22T12:30:00+01:00","home":true}""").statusCode(400);
        matchAdd(petra, T04, match).statusCode(201);
        assertThat(event(petra, T04).extract().jsonPath().getList("matches.opponent"),
                contains("UHC Musterdorf", "Unihockey Beispielstadt", "UHC Drei"));
    }

    @Test
    void auf15ResultCreateAlsoByTheMatchDay() {
        String petra = login("petra");
        clock("2026-11-23T08:00:00+01:00");
        matchUpdate(petra, T04, MATCH_01, """
                {"opponent":"UHC Musterdorf","startTime":"2026-11-22T09:30:00+01:00","home":true,"result":"5:3"}""")
                .statusCode(200).body("result", equalTo("5:3"));
        assertThat(event(login("anna"), T04).extract().path("matches[0].result"), equalTo("5:3"));
        matchUpdate(petra, T04, MATCH_01, """
                {"opponent":"UHC Musterdorf","startTime":"2026-11-22T09:30:00+01:00","home":true,"result":"5-3"}""")
                .statusCode(400);
        matchUpdate(login("anna"), T04, MATCH_01, """
                {"opponent":"UHC Musterdorf","startTime":"2026-11-22T09:30:00+01:00","home":true,"result":"1:0"}""")
                .statusCode(403);
    }

    @Test
    void suh06ImportedMatchesAreProtected() {
        String petra = login("petra");
        matchUpdate(petra, T04, MATCH_02, """
                {"opponent":"Andere","startTime":"2026-11-22T11:45:00+01:00","home":false}""")
                .statusCode(409).body("rule", equalTo("SUH-06"));
        matchDelete(petra, T04, MATCH_02).statusCode(409);
        // Spiel eines anderen Termins
        matchUpdate(petra, T04, MATCH_03, """
                {"opponent":"UH Testingen","startTime":"2026-11-22T11:45:00+01:00","home":true}""").statusCode(404);
    }

    @Test
    void fromHandCreatedMatchUpdateAndDelete() {
        String marc = login("marc");
        matchUpdate(marc, T05, MATCH_03, """
                {"opponent":"UH Testingen II","startTime":"2026-11-28T14:15:00+01:00","home":false}""")
                .statusCode(200).body("opponent", equalTo("UH Testingen II")).body("home", equalTo(false));
        matchDelete(login("petra"), T05, MATCH_03).statusCode(403);
        matchDelete(marc, T05, MATCH_03).statusCode(204);
        assertThat(event(marc, T05).extract().jsonPath().getList("matches"), equalTo(List.of()));
        matchDelete(marc, T05, MATCH_03).statusCode(404);
    }

    @Test
    void matchDayMoveTakesFromHandCreatedMatchesWith() {
        String marc = login("marc");
        given().cookie("stv_session", marc).contentType(ContentType.JSON).body("""
                        {"type":"MATCH_DAY","title":"Spieltag","start":"2026-11-29T14:00:00+01:00",
                         "end":"2026-11-29T16:00:00+01:00","teamIds":["10000000-0000-4000-8000-000000000002"]}""")
                .put("/api/events/" + T05).then().statusCode(200)
                .body("matches[0].startTime", equalTo("2026-11-29T14:00:00+01:00"));
    }

    @Test
    void o48ImportedMatchDayOnlyAtSameDayReschedulableResultStaysCreatable() {
        String petra = login("petra");
        String rescheduled = """
                {"type":"MATCH_DAY","title":"Spieltag","start":"2026-11-29T09:30:00+01:00",
                 "end":"2026-11-29T13:00:00+01:00","teamIds":["%s"]}""".formatted(TEAM_1);
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(rescheduled)
                .put("/api/events/" + T04).then().statusCode(422)
                .body("rule", equalTo("SUH-05")).body("fields[0].field", equalTo("start"));
        // Spieltag und Spiele bleiben am 22.11.
        event(petra, T04).body("start", equalTo("2026-11-22T09:30:00+01:00"))
                .body("matches[0].startTime", equalTo("2026-11-22T09:30:00+01:00"));
        // Am selben Tag verschieben geht
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("""
                        {"type":"MATCH_DAY","title":"Spieltag","start":"2026-11-22T09:00:00+01:00",
                         "end":"2026-11-22T13:00:00+01:00","teamIds":["%s"]}""".formatted(TEAM_1))
                .put("/api/events/" + T04).then().statusCode(200);
        matchUpdate(petra, T04, MATCH_01, """
                {"opponent":"UHC Musterdorf","startTime":"2026-11-22T09:30:00+01:00","home":true,"result":"2:1"}""")
                .statusCode(200).body("result", equalTo("2:1"));
    }

    @Test
    void auf01MatchDayWithMatchesStaysMatchDay() {
        given().cookie("stv_session", login("petra")).contentType(ContentType.JSON).body("""
                        {"type":"PRACTICE","title":"Training","start":"2026-11-22T09:30:00+01:00",
                         "end":"2026-11-22T13:00:00+01:00","teamIds":["%s"]}""".formatted(TEAM_1))
                .put("/api/events/" + T04).then().statusCode(409).body("rule", equalTo("AUF-01"));
    }

    @Test
    void auf03MatchDayBelongsToExactlyATeam() {
        // bestehend aus F-03, hier nur als Kontrolle, dass Spieltage weiterhin ein Team haben
        JsonPath json = event(login("alex"), T04).statusCode(200).extract().jsonPath();
        assertThat(json.getList("teams.id"), containsInAnyOrder(TEAM_1));
    }
}
