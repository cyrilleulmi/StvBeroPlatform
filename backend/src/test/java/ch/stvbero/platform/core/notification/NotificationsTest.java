package ch.stvbero.platform.core.notification;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;

/**
 * F-06: notifications (BEN-01 to BEN-05, BEN-07, BEN-11 to BEN-13), scenarios S5, S6, S9, S10 from
 * docs/test-data.md. Default "now": Mon 16.11.2026 06:00.
 */
@QuarkusTest
class NotificationsTest {

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
    static final String T02 = "40000000-0000-4000-8000-000000000002";
    static final String T03 = "40000000-0000-4000-8000-000000000003";
    static final String T04 = "40000000-0000-4000-8000-000000000004";
    static final String T05 = "40000000-0000-4000-8000-000000000005";
    static final String SERIES_1 = "70000000-0000-4000-8000-000000000001";
    static final String SERIES_2 = "70000000-0000-4000-8000-000000000002";
    /** Series 01 on Tue 01.12.2026, without responses (docs/test-data.md). */
    static final String SERIES_1_01_12 = UUID.nameUUIDFromBytes("termin-1-2026-12-01".getBytes(StandardCharsets.UTF_8)).toString();
    static final String ANNA_CANCELLATION_T03 = "90000000-0000-4000-8000-000000000002";
    static final String LEA_CANCELLATION_T03 = "90000000-0000-4000-8000-000000000003";

    /** All members of Team 1 and Team 2 except Petra. */
    static final String[] TEAM_1_AND_2_WITHOUT_PETRA = {ANNA, LEA, MIA, SARA, NINA, JANA, KIM, MARC, EVA, LARA, ZOE, ELLA};

    @Inject
    TestChannel testChannel;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
        testChannel.kept().clear();
    }

    private static void clock(String now) {
        given().contentType(ContentType.JSON).body("{\"now\":\"" + now + "\"}").put("/api/test/clock").then().statusCode(204);
    }

    private static ValidatableResponse listOwn(String session, String query) {
        return given().cookie("stv_session", session).get("/api/notifications" + query).then();
    }

    private static ValidatableResponse markRead(String session, String id) {
        return given().cookie("stv_session", session).post("/api/notifications/" + id + "/read").then();
    }

    private static void respond(String session, String event, String status) {
        given().cookie("stv_session", session).contentType(ContentType.JSON).body("{\"status\":\"" + status + "\"}")
                .put("/api/events/" + event + "/responses/me").then().statusCode(200);
    }

    /** Newly created notifications of one type for an event (without seed). */
    private static List<Map<String, Object>> newOnes(String type, String event) {
        JsonPath json = given().get("/api/test/notifications").then().statusCode(200).extract().jsonPath();
        return json.<Map<String, Object>>getList("findAll { it.type == '" + type + "' && it.eventId == '" + event
                + "' && !it.id.startsWith('90000000') }");
    }

    @SuppressWarnings("unchecked")
    private static List<String> recipientIds(List<Map<String, Object>> list) {
        return list.stream().map(notification -> (String) ((Map<String, Object>) notification.get("recipient")).get("id")).toList();
    }

    private static List<String> texts(List<Map<String, Object>> list) {
        return list.stream().map(notification -> (String) notification.get("text")).toList();
    }

    // ------------------------------------------------------------------ List and read (K1, K2)

    @Test
    void k1OwnNotificationsNewestFirstWithUnreadFilter() {
        String lea = login("lea");
        JsonPath json = listOwn(lea, "").statusCode(200).extract().jsonPath();
        assertThat(json.getList("type"), contains("CALL_UP_PUBLISHED", "EVENT_CANCELLED"));
        assertThat(json.getList("eventId"), contains(T05, T03));
        assertThat(json.getList("recipient.id"), everyItem(equalTo(LEA)));
        assertThat(json.getList("read"), contains(false, false));
        assertThat(json.getList("text"), contains("Das Aufgebot für Spieltag Sa 28.11. ist da: Du bist nicht aufgeboten.",
                "Training Di 24.11. ist abgesagt: Halle besetzt"));

        // Petra has read hers; unread=true leaves it out
        assertThat(listOwn(login("petra"), "").extract().jsonPath().getList("read"), contains(true));
        assertThat(listOwn(login("petra"), "?unread=true").extract().jsonPath().getList("id"), empty());
        // Alex is in no team
        assertThat(listOwn(login("alex"), "").extract().jsonPath().getList("id"), empty());
    }

    @Test
    void k2MarkAsRead() {
        String anna = login("anna");
        assertThat(listOwn(anna, "?unread=true").extract().jsonPath().getList("id"), contains(ANNA_CANCELLATION_T03));
        markRead(anna, ANNA_CANCELLATION_T03).statusCode(204);
        assertThat(listOwn(anna, "?unread=true").extract().jsonPath().getList("id"), empty());
        assertThat(listOwn(anna, "").extract().jsonPath().getList("read"), contains(true));
        // Marking again changes nothing
        markRead(anna, ANNA_CANCELLATION_T03).statusCode(204);
    }

    @Test
    void rr07CannotMarkSomeoneElsesNotification() {
        markRead(login("anna"), LEA_CANCELLATION_T03).statusCode(403).body("rule", equalTo("RR-07"));
        // Lea's notification stays unread
        assertThat(listOwn(login("lea"), "?unread=true").extract().jsonPath().getList("id"), hasSize(2));
        markRead(login("anna"), "90000000-0000-4000-8000-000000000099").statusCode(404);
        given().get("/api/notifications").then().statusCode(401);
        given().post("/api/notifications/" + ANNA_CANCELLATION_T03 + "/read").then().statusCode(401);
    }

    // ------------------------------------------------------------------ Triggers

    @Test
    void s5LateCancellationNotifiesPetraNotMarc() {
        clock("2026-11-16T19:00:00+01:00");
        respond(login("anna"), T01, "DECLINED");
        List<Map<String, Object>> created = newOnes("LATE_CANCELLATION", T01);
        assertThat(recipientIds(created), contains(PETRA));
        assertThat(texts(created), contains("Anna Muster hat sich kurzfristig für Training Di 17.11. abgemeldet."));

        // A further change that stays a late cancellation: no second notification
        given().cookie("stv_session", login("anna")).contentType(ContentType.JSON)
                .body("{\"status\":\"MAYBE\",\"comment\":\"Evtl. später\"}")
                .put("/api/events/" + T01 + "/responses/me").then().statusCode(200).body("lateCancellation", equalTo(true));
        assertThat(newOnes("LATE_CANCELLATION", T01), hasSize(1));
    }

    @Test
    void ben02NoNotificationBeforeTheDeadline() {
        respond(login("anna"), T01, "DECLINED");
        assertThat(newOnes("LATE_CANCELLATION", T01), empty());
    }

    @Test
    void s6ReminderExactlyOnceToNinaAndElla() {
        clock("2026-11-16T06:29:00+01:00");
        assertThat(newOnes("REMINDER", T01), empty());

        clock("2026-11-16T06:31:00+01:00");
        List<Map<String, Object>> created = newOnes("REMINDER", T01);
        assertThat(recipientIds(created), containsInAnyOrder(NINA, ELLA));
        assertThat(texts(created), everyItem(equalTo(
                "Erinnerung: Bitte gib bis Mo 16.11. 18:30 Bescheid, ob du bei Training Di 17.11. dabei bist.")));
        // Nobody else, not for other events either
        JsonPath all = given().get("/api/test/notifications").then().extract().jsonPath();
        assertThat(all.getList("findAll { it.type == 'REMINDER' }"), hasSize(2));

        // BEN-13: repeated checks do not remind again
        clock("2026-11-16T07:00:00+01:00");
        clock("2026-11-16T18:00:00+01:00");
        assertThat(newOnes("REMINDER", T01), hasSize(2));
        // Nina sees it in her list
        assertThat(listOwn(login("nina"), "?unread=true").extract().jsonPath().getList("type"),
                contains("REMINDER", "EVENT_CANCELLED"));
    }

    @Test
    void ben01NoReminderAfterTheDeadline() {
        clock("2026-11-16T18:31:00+01:00");
        assertThat(newOnes("REMINDER", T01), empty());
    }

    @Test
    void s9PublishingNotifiesAllPlayersOfTheTeam() {
        given().cookie("stv_session", login("petra")).post("/api/events/" + T04 + "/call-up/publish")
                .then().statusCode(200);
        List<Map<String, Object>> created = newOnes("CALL_UP_PUBLISHED", T04);
        assertThat(recipientIds(created), containsInAnyOrder(ANNA, LEA, MIA, SARA, NINA, JANA, KIM));
        assertThat(texts(created.stream().filter(notification -> recipientIds(List.of(notification)).contains(ANNA)).toList()),
                contains("Das Aufgebot für Spieltag So 22.11. ist da: Du bist aufgeboten."));
        assertThat(texts(created.stream().filter(notification -> recipientIds(List.of(notification)).contains(KIM)).toList()),
                contains("Das Aufgebot für Spieltag So 22.11. ist da: Du bist nicht aufgeboten."));
    }

    @Test
    void s10CalledUpPlayerDecliningNotifiesTheCoaches() {
        respond(login("eva"), T05, "DECLINED");
        List<Map<String, Object>> created = newOnes("CALLED_UP_PLAYER_DECLINED", T05);
        assertThat(recipientIds(created), contains(MARC));
        assertThat(texts(created), contains("Eva Steiner ist für Spieltag Sa 28.11. aufgeboten und hat sich abgemeldet."));
        assertThat(newOnes("LATE_CANCELLATION", T05), empty());

        // Lea is not called up: no notification
        respond(login("lea"), T05, "DECLINED");
        assertThat(newOnes("CALLED_UP_PLAYER_DECLINED", T05), hasSize(1));
    }

    @Test
    void ben05ChangingAPublishedCallUpNotifiesOnlyAffected() {
        String petra = login("petra");
        // In draft: no notification
        setCallUp(petra, T04, ANNA, LEA, MIA, SARA, JANA, NINA);
        assertThat(newOnes("CALL_UP_CHANGED", T04), empty());

        given().cookie("stv_session", petra).post("/api/events/" + T04 + "/call-up/publish").then().statusCode(200);
        // Mia drops out, Kim is added
        setCallUp(petra, T04, ANNA, LEA, SARA, JANA, NINA, KIM);
        List<Map<String, Object>> created = newOnes("CALL_UP_CHANGED", T04);
        assertThat(recipientIds(created), containsInAnyOrder(MIA, KIM));
        assertThat(texts(created), containsInAnyOrder(
                "Das Aufgebot für Spieltag So 22.11. wurde geändert: Du bist nicht mehr aufgeboten.",
                "Das Aufgebot für Spieltag So 22.11. wurde geändert: Du bist jetzt aufgeboten."));
    }

    @Test
    void ben07CancellationNotifiesAllMembersOfTheInvolvedTeams() {
        given().cookie("stv_session", login("petra")).contentType(ContentType.JSON).body("{\"reason\":\"Halle gesperrt\"}")
                .post("/api/events/" + T01 + "/cancel").then().statusCode(200);
        List<Map<String, Object>> created = newOnes("EVENT_CANCELLED", T01);
        assertThat(recipientIds(created), containsInAnyOrder(TEAM_1_AND_2_WITHOUT_PETRA));
        assertThat(texts(created), everyItem(equalTo("Training Di 17.11. ist abgesagt: Halle gesperrt")));
    }

    @Test
    void ben07ReschedulingNotifiesAllMembersOfTheInvolvedTeams() {
        String petra = login("petra");
        // Only the end changes: no rescheduling
        update(petra, "Training", "2026-11-17T18:30:00+01:00", "2026-11-17T20:15:00+01:00");
        assertThat(newOnes("EVENT_RESCHEDULED", T01), empty());

        update(petra, "Training", "2026-11-18T19:00:00+01:00", "2026-11-18T20:30:00+01:00");
        List<Map<String, Object>> created = newOnes("EVENT_RESCHEDULED", T01);
        assertThat(recipientIds(created), containsInAnyOrder(TEAM_1_AND_2_WITHOUT_PETRA));
        assertThat(texts(created), everyItem(equalTo("Training Di 17.11. ist verschoben auf Mi 18.11. 19:00.")));
    }

    @Test
    void ben07CancellationBySeriesExceptionNotifiesAllMembers() {
        String petra = login("petra");
        given().cookie("stv_session", petra).get("/api/events/" + SERIES_1_01_12).then().statusCode(200);
        // 17.11. has responses (is cancelled), 24.11. is already cancelled, 01.12. has no responses (is removed)
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"startDate\":\"2026-11-17\",\"endDate\":\"2026-12-01\",\"reason\":\"Hallensperre\"}")
                .post("/api/series/" + SERIES_1 + "/exceptions").then().statusCode(201);

        List<Map<String, Object>> created = newOnes("EVENT_CANCELLED", T01);
        assertThat(recipientIds(created), containsInAnyOrder(TEAM_1_AND_2_WITHOUT_PETRA));
        assertThat(texts(created), everyItem(equalTo("Training Di 17.11. ist abgesagt: Hallensperre")));
        // The removed event without responses and the already cancelled event 03 trigger nothing
        given().cookie("stv_session", petra).get("/api/events/" + SERIES_1_01_12).then().statusCode(404);
        assertThat(newOnes("EVENT_CANCELLED", SERIES_1_01_12), empty());
        assertThat(newOnes("EVENT_CANCELLED", T03), empty());
        JsonPath all = given().get("/api/test/notifications").then().extract().jsonPath();
        assertThat(all.getList("findAll { it.type == 'EVENT_CANCELLED' && !it.id.startsWith('90000000') }"),
                hasSize(TEAM_1_AND_2_WITHOUT_PETRA.length));
    }

    @Test
    void ben07CancellationBySeriesChangeNotifiesAllMembers() {
        // Series 02 moved to Friday: event 02 (Thu 19.11.) has Anna's response and is cancelled, the other
        // Thursdays without responses are removed
        given().cookie("stv_session", login("petra")).contentType(ContentType.JSON).body("""
                        {"type":"PRACTICE","title":"Training","teamIds":["%s"],"weekday":"FRIDAY","startTime":"19:00",
                         "endTime":"20:30","validFrom":"2026-08-20","validUntil":"2027-04-29",
                         "location":{"name":"Sporthalle Nord"}}""".formatted(TEAM_1))
                .put("/api/series/" + SERIES_2).then().statusCode(200);

        List<Map<String, Object>> created = newOnes("EVENT_CANCELLED", T02);
        assertThat(recipientIds(created), containsInAnyOrder(ANNA, LEA, MIA, SARA, NINA, JANA, KIM));
        assertThat(texts(created), everyItem(equalTo("Training Do 19.11. ist abgesagt: Serie geändert")));
        JsonPath all = given().get("/api/test/notifications").then().extract().jsonPath();
        assertThat(all.getList("findAll { it.type == 'EVENT_CANCELLED' && !it.id.startsWith('90000000') }"),
                hasSize(7));
    }

    @Test
    void ben11EveryChannelGetsTheMessage() {
        respond(login("eva"), T05, "DECLINED");
        assertThat(testChannel.kept(), hasSize(1));
        Message message = testChannel.kept().get(0);
        assertThat(message.type(), equalTo(NotificationType.CALLED_UP_PLAYER_DECLINED));
        assertThat(message.recipientId().toString(), equalTo(MARC));
        assertThat(message.eventId().toString(), equalTo(T05));
    }

    private static void setCallUp(String session, String event, String... persons) {
        String ids = String.join(",", List.of(persons).stream().map(id -> "\"" + id + "\"").toList());
        given().cookie("stv_session", session).contentType(ContentType.JSON).body("{\"personIds\":[" + ids + "]}")
                .put("/api/events/" + event + "/call-up").then().statusCode(200);
    }

    private static void update(String session, String title, String start, String end) {
        given().cookie("stv_session", session).contentType(ContentType.JSON).body("""
                        {"type":"PRACTICE","title":"%s","start":"%s","end":"%s","teamIds":["%s","%s"],
                         "location":{"name":"Sporthalle Nord"}}""".formatted(title, start, end, TEAM_1, TEAM_2))
                .put("/api/events/" + T01).then().statusCode(200);
    }
}
