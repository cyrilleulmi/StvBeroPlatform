package ch.stvbero.platform.core.notification;

import ch.stvbero.platform.core.error.Concurrently;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * F-19: notifications by e-mail and push (BEN-10, BEN-11, E-71), criteria 1 to 3 and 6 to 10. In the test profile
 * the simulator replaces SMTP and the push services; {@code GET /api/test/notifications} shows every e-mail and push
 * with its channel. Seed: Kim switched e-mails off, Lea has push on one phone (docs/test-data.md).
 */
@QuarkusTest
class NotificationChannelsTest {

    static final String T01 = "40000000-0000-4000-8000-000000000001";
    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String LEA = "20000000-0000-4000-8000-000000000006";
    static final String KIM = "20000000-0000-4000-8000-000000000011";
    static final String LEA_PHONE = "a0000000-0000-4000-8000-000000000001";
    static final String LEA_ENDPOINT = "https://push.example.test/seed-lea-phone";
    /** Browser keys of RFC 8291, appendix A: a valid P-256 key and a 16-byte secret. */
    static final String P256DH = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    static final String ANDROID = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/130.0 Mobile Safari/537.36";
    static final String IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1";

    /** Members of Team 1 and Team 2 except Petra, who cancels. */
    static final String[] RECIPIENTS_T01 = {
            "20000000-0000-4000-8000-000000000003", ANNA, LEA, "20000000-0000-4000-8000-000000000007",
            "20000000-0000-4000-8000-000000000008", "20000000-0000-4000-8000-000000000009",
            "20000000-0000-4000-8000-000000000010", KIM, "20000000-0000-4000-8000-000000000012",
            "20000000-0000-4000-8000-000000000013", "20000000-0000-4000-8000-000000000014",
            "20000000-0000-4000-8000-000000000015"};

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    // ------------------------------------------------------------------ Helpers

    private static void clock(String now) {
        given().contentType(ContentType.JSON).body("{\"now\":\"" + now + "\"}").put("/api/test/clock").then().statusCode(204);
    }

    private static void deliveryMode(String json) {
        given().contentType(ContentType.JSON).body(json).put("/api/test/delivery").then().statusCode(204);
    }

    private static void cancelT01() {
        given().cookie("stv_session", login("petra")).contentType(ContentType.JSON).body("{\"reason\":\"Halle gesperrt\"}")
                .post("/api/events/" + T01 + "/cancel").then().statusCode(200);
    }

    /** Newly created notifications (without seed) of one type, each with its deliveries. */
    private static List<Map<String, Object>> created(String type) {
        JsonPath json = given().get("/api/test/notifications").then().statusCode(200).extract().jsonPath();
        return json.getList("findAll { it.type == '" + type + "' && !it.id.startsWith('90000000') }");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> deliveries(List<Map<String, Object>> notifications, String channel) {
        return notifications.stream()
                .flatMap(n -> ((List<Map<String, Object>>) n.get("deliveries")).stream())
                .filter(d -> channel.equals(d.get("channel"))).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> of(List<Map<String, Object>> notifications, String personId) {
        return notifications.stream()
                .filter(n -> personId.equals(((Map<String, Object>) n.get("recipient")).get("id"))).findFirst()
                .orElseThrow();
    }

    private static io.restassured.response.ValidatableResponse register(String session, String endpoint,
                                                                         String userAgent) {
        return given().cookie("stv_session", session).header("User-Agent", userAgent).contentType(ContentType.JSON)
                .body("{\"endpoint\":\"" + endpoint + "\",\"keys\":{\"p256dh\":\"" + P256DH + "\",\"auth\":\"" + AUTH + "\"}}")
                .post("/api/me/push-subscriptions").then();
    }

    private static JsonPath settings(String session) {
        return given().cookie("stv_session", session).get("/api/me/notification-settings").then().statusCode(200)
                .extract().jsonPath();
    }

    // ------------------------------------------------------------------ Criteria 1, 6, 8, 10

    @Test
    void criterion1EveryNotificationInTheAppByEmailAndOnEveryDevice() {
        cancelT01();
        List<Map<String, Object>> notifications = created("EVENT_CANCELLED");
        // App: everyone (criterion 1)
        assertThat(notifications.stream().map(n -> ((Map<?, ?>) n.get("recipient")).get("id")).toList(),
                containsInAnyOrder((Object[]) RECIPIENTS_T01));
        // E-mail: everyone except Kim, who switched it off; exactly one each (criterion 10)
        List<Map<String, Object>> emails = deliveries(notifications, "EMAIL");
        assertThat(emails, hasSize(RECIPIENTS_T01.length - 1));
        assertThat(deliveries(List.of(of(notifications, KIM)), "EMAIL"), empty());
        Map<String, Object> anna = deliveries(List.of(of(notifications, ANNA)), "EMAIL").getFirst();
        assertEquals("anna@example.ch", anna.get("address"));
        assertEquals("Abgesagt: Training Di 17.11.", anna.get("subject"));
        assertEquals("Training Di 17.11. ist abgesagt: Halle gesperrt", anna.get("text"));
        assertEquals("/termine/" + T01, anna.get("path"));
        // Nothing goes out in the test profile, but it counts as handed over (criterion 8)
        assertThat(emails.stream().map(d -> d.get("status")).toList(), everyItem(equalTo("SENT")));
        assertThat(emails.stream().map(d -> d.get("attempts")).toList(), everyItem(equalTo(1)));
        // Push: only Lea, on her one phone
        List<Map<String, Object>> pushes = deliveries(notifications, "PUSH");
        assertThat(pushes, hasSize(1));
        assertEquals(LEA_ENDPOINT, pushes.getFirst().get("address"));
        assertThat(deliveries(List.of(of(notifications, LEA)), "PUSH"), hasSize(1));
        assertEquals("SENT", pushes.getFirst().get("status"));
    }

    @Test
    void criterion6ReminderUsesTheChannelsToo() {
        clock("2026-11-16T06:31:00+01:00");
        List<Map<String, Object>> reminders = created("REMINDER");
        assertThat(reminders, hasSize(2));
        List<Map<String, Object>> emails = deliveries(reminders, "EMAIL");
        assertThat(emails.stream().map(d -> d.get("address")).toList(),
                containsInAnyOrder("nina@example.ch", "ella@example.ch"));
        assertThat(emails.stream().map(d -> d.get("subject")).toList(),
                everyItem(equalTo("Bitte gib Bescheid: Training Di 17.11.")));
    }

    @Test
    void criterion10OnePushPerDeviceAndOneEmailPerNotification() {
        String lea = login("lea");
        register(lea, "https://push.example.test/lea-laptop", "Mozilla/5.0 (Windows NT 10.0) Firefox/132.0")
                .statusCode(201);
        cancelT01();
        Map<String, Object> notification = of(created("EVENT_CANCELLED"), LEA);
        assertThat(deliveries(List.of(notification), "EMAIL"), hasSize(1));
        assertThat(deliveries(List.of(notification), "PUSH").stream().map(d -> d.get("address")).toList(),
                contains(LEA_ENDPOINT, "https://push.example.test/lea-laptop"));
    }

    // ------------------------------------------------------------------ Criterion 2: settings

    @Test
    void criterion2EmailOnByDefaultPushOffWithoutDevice() {
        JsonPath anna = settings(login("anna"));
        assertEquals(true, anna.getBoolean("email"));
        assertEquals("anna@example.ch", anna.getString("emailAddress"));
        assertEquals(true, anna.getBoolean("emailAvailable"));
        assertEquals(true, anna.getBoolean("pushAvailable"));
        assertEquals(65, WebPush.decode(anna.getString("pushPublicKey")).length);
        assertThat(anna.getList("devices"), empty());

        assertEquals(false, settings(login("kim")).getBoolean("email"));
        JsonPath lea = settings(login("lea"));
        assertThat(lea.getList("devices.id"), contains(LEA_PHONE));
        assertThat(lea.getList("devices.label"), contains("Chrome auf Android"));
        assertThat(lea.getList("devices.endpoint"), contains(LEA_ENDPOINT));
    }

    @Test
    void criterion2SwitchingEmailOffAndOn() {
        String anna = login("anna");
        given().cookie("stv_session", anna).contentType(ContentType.JSON).body("{\"email\":false}")
                .put("/api/me/notification-settings").then().statusCode(200).body("email", equalTo(false));
        cancelT01();
        assertThat(deliveries(List.of(of(created("EVENT_CANCELLED"), ANNA)), "EMAIL"), empty());

        given().cookie("stv_session", anna).contentType(ContentType.JSON).body("{\"email\":true}")
                .put("/api/me/notification-settings").then().statusCode(200).body("email", equalTo(true));
        assertEquals(true, settings(anna).getBoolean("email"));
        // Kim switches it on again
        given().cookie("stv_session", login("kim")).contentType(ContentType.JSON).body("{\"email\":true}")
                .put("/api/me/notification-settings").then().statusCode(200).body("email", equalTo(true));
    }

    @Test
    void criterion2WithoutAnAddressNoEmails() {
        // The administration removes Anna's address
        given().cookie("stv_session", login("alex")).contentType(ContentType.JSON)
                .body("{\"firstName\":\"Anna\",\"lastName\":\"Muster\",\"admin\":false}")
                .put("/api/persons/" + ANNA).then().statusCode(200);
        String anna = login("anna");
        JsonPath json = settings(anna);
        assertEquals(false, json.getBoolean("email"));
        assertThat(json.get("emailAddress"), nullValue());
        given().cookie("stv_session", anna).contentType(ContentType.JSON).body("{\"email\":true}")
                .put("/api/me/notification-settings").then().statusCode(422)
                .body("rule", equalTo("BEN-10")).body("fields[0].field", equalTo("email"));
        given().cookie("stv_session", anna).contentType(ContentType.JSON).body("{}")
                .put("/api/me/notification-settings").then().statusCode(400);
        cancelT01();
        assertThat(deliveries(List.of(of(created("EVENT_CANCELLED"), ANNA)), "EMAIL"), empty());
    }

    // ------------------------------------------------------------------ Criterion 3: devices

    @Test
    void criterion3RegisterSeveralDevicesAndRegisterAgain() {
        String anna = login("anna");
        register(anna, "https://push.example.test/anna-phone", ANDROID).statusCode(201)
                .body("id", notNullValue()).body("label", equalTo("Chrome auf Android"))
                .body("endpoint", equalTo("https://push.example.test/anna-phone"));
        register(anna, "https://push.example.test/anna-ipad", IPHONE).statusCode(201)
                .body("label", equalTo("Safari auf iPhone"));
        // The same device again: no second entry
        register(anna, "https://push.example.test/anna-phone", ANDROID).statusCode(201);
        assertThat(settings(anna).getList("devices.endpoint"),
                contains("https://push.example.test/anna-phone", "https://push.example.test/anna-ipad"));

        cancelT01();
        assertThat(deliveries(List.of(of(created("EVENT_CANCELLED"), ANNA)), "PUSH"), hasSize(2));
    }

    @Test
    void criterion3DeviceMovesToThePersonSignedInOnIt() {
        register(login("anna"), LEA_ENDPOINT, ANDROID).statusCode(201);
        assertThat(settings(login("lea")).getList("devices"), empty());
        assertThat(settings(login("anna")).getList("devices.endpoint"), contains(LEA_ENDPOINT));
    }

    @Test
    void criterion3InvalidSubscriptionsAreRefused() {
        String anna = login("anna");
        given().cookie("stv_session", anna).contentType(ContentType.JSON)
                .body("{\"endpoint\":\"http://push.example.test/x\",\"keys\":{\"p256dh\":\"" + P256DH + "\",\"auth\":\"" + AUTH + "\"}}")
                .post("/api/me/push-subscriptions").then().statusCode(400).body("fields[0].field", equalTo("endpoint"));
        given().cookie("stv_session", anna).contentType(ContentType.JSON)
                .body("{\"endpoint\":\"https://push.example.test/x\",\"keys\":{\"p256dh\":\"" + AUTH + "\",\"auth\":\"" + AUTH + "\"}}")
                .post("/api/me/push-subscriptions").then().statusCode(400).body("fields[0].field", equalTo("keys.p256dh"));
        given().cookie("stv_session", anna).contentType(ContentType.JSON)
                .body("{\"endpoint\":\"https://push.example.test/x\",\"keys\":{\"p256dh\":\"" + P256DH + "\",\"auth\":\"abc\"}}")
                .post("/api/me/push-subscriptions").then().statusCode(400).body("fields[0].field", equalTo("keys.auth"));
        given().cookie("stv_session", anna).contentType(ContentType.JSON).body("{\"endpoint\":\"https://push.example.test/x\"}")
                .post("/api/me/push-subscriptions").then().statusCode(400);
        assertThat(settings(anna).getList("devices"), empty());
    }

    @Test
    void criterion3SameDeviceRegisteredConcurrentlyNeverGives500() {
        String anna = login("anna");
        for (int round = 0; round < 5; round++) {
            String endpoint = "https://push.example.test/anna-new-" + round;
            List<Integer> codes = Concurrently.statusCodes(8,
                    () -> register(anna, endpoint, ANDROID).extract().statusCode());
            assertThat(codes, everyItem(equalTo(201)));
        }
        assertThat(settings(anna).getList("devices"), hasSize(5));
    }

    @Test
    void criterion3SameDeviceRemovedConcurrentlyNeverGives500() {
        // Lea switches push off on her phone while removing the phone on her laptop (BEN-16)
        String lea = login("lea");
        List<Integer> codes = Concurrently.statusCodes(6,
                () -> given().cookie("stv_session", lea).delete("/api/me/push-subscriptions/" + LEA_PHONE).then()
                        .extract().statusCode());
        assertThat(codes, everyItem(anyOf(equalTo(204), equalTo(404))));
        assertThat(codes, hasItem(204));
        assertThat(settings(lea).getList("devices"), empty());
    }

    @Test
    void criterion2EmailChangedConcurrentlyNeverGives500() {
        // Mia has no stored choice yet: every request wants to create it
        String mia = login("mia");
        List<Integer> codes = Concurrently.statusCodes(4,
                () -> given().cookie("stv_session", mia).contentType(ContentType.JSON).body("{\"email\":false}")
                        .put("/api/me/notification-settings").then().extract().statusCode());
        assertThat(codes, everyItem(equalTo(200)));
        assertEquals(false, settings(mia).getBoolean("email"));
    }

    @Test
    void rr07OnlyTheOwnDevicesAndSettings() {
        given().cookie("stv_session", login("anna")).delete("/api/me/push-subscriptions/" + LEA_PHONE).then()
                .statusCode(403).body("rule", equalTo("RR-07"));
        // Admins neither: it is personal
        given().cookie("stv_session", login("alex")).delete("/api/me/push-subscriptions/" + LEA_PHONE).then()
                .statusCode(403);
        given().cookie("stv_session", login("anna"))
                .delete("/api/me/push-subscriptions/a0000000-0000-4000-8000-000000000099").then().statusCode(404);
        assertThat(settings(login("lea")).getList("devices"), hasSize(1));

        given().cookie("stv_session", login("lea")).delete("/api/me/push-subscriptions/" + LEA_PHONE).then().statusCode(204);
        assertThat(settings(login("lea")).getList("devices"), empty());

        given().get("/api/me/notification-settings").then().statusCode(401);
        given().contentType(ContentType.JSON).body("{\"email\":false}").put("/api/me/notification-settings").then()
                .statusCode(401);
        given().contentType(ContentType.JSON)
                .body("{\"endpoint\":\"https://push.example.test/x\",\"keys\":{\"p256dh\":\"" + P256DH + "\",\"auth\":\"" + AUTH + "\"}}")
                .post("/api/me/push-subscriptions").then().statusCode(401);
        given().delete("/api/me/push-subscriptions/" + LEA_PHONE).then().statusCode(401);
    }

    // ------------------------------------------------------------------ Criterion 7: failures

    @Test
    void criterion7FailedEmailDoesNotBlockTheActionAndIsRetried() {
        deliveryMode("{\"failing\":[\"EMAIL\"]}");
        cancelT01(); // 200: the cancellation went through
        List<Map<String, Object>> emails = deliveries(created("EVENT_CANCELLED"), "EMAIL");
        assertThat(emails.stream().map(d -> d.get("status")).toList(), everyItem(equalTo("PENDING")));
        assertThat(emails.stream().map(d -> d.get("attempts")).toList(), everyItem(equalTo(1)));
        assertThat(emails.stream().map(d -> d.get("nextAttemptAt")).toList(),
                everyItem(equalTo("2026-11-16T06:01:00+01:00")));
        assertThat(emails.stream().map(d -> d.get("lastError")).toList(),
                everyItem(equalTo("Simulated: mail server not reachable")));
        // Push and the app are not affected
        assertEquals("SENT", deliveries(created("EVENT_CANCELLED"), "PUSH").getFirst().get("status"));
        given().cookie("stv_session", login("anna")).get("/api/notifications?unread=true").then()
                .body("type", contains("EVENT_CANCELLED", "EVENT_CANCELLED"));

        // Before the waiting time: no new attempt
        clock("2026-11-16T06:00:30+01:00");
        assertThat(deliveries(created("EVENT_CANCELLED"), "EMAIL").stream().map(d -> d.get("attempts")).toList(),
                everyItem(equalTo(1)));
        // The mail server is back: the next attempt goes through
        deliveryMode("{}");
        clock("2026-11-16T06:01:00+01:00");
        emails = deliveries(created("EVENT_CANCELLED"), "EMAIL");
        assertThat(emails.stream().map(d -> d.get("status")).toList(), everyItem(equalTo("SENT")));
        assertThat(emails.stream().map(d -> d.get("attempts")).toList(), everyItem(equalTo(2)));
        assertThat(emails.stream().map(d -> d.get("lastError")).toList(), everyItem(nullValue()));
    }

    @Test
    void criterion7GivesUpAfterFiveAttempts() {
        deliveryMode("{\"failing\":[\"PUSH\"]}");
        cancelT01();
        for (String time : List.of("06:01", "06:06", "06:36", "08:36")) {
            clock("2026-11-16T" + time + ":00+01:00");
        }
        Map<String, Object> push = deliveries(created("EVENT_CANCELLED"), "PUSH").getFirst();
        assertEquals("FAILED", push.get("status"));
        assertEquals(5, push.get("attempts"));
        assertThat(push.get("nextAttemptAt"), nullValue());
        // Nothing more after that
        clock("2026-11-17T08:00:00+01:00");
        assertEquals(5, deliveries(created("EVENT_CANCELLED"), "PUSH").getFirst().get("attempts"));
    }

    @Test
    void criterion7ExpiredPushSubscriptionIsRemoved() {
        deliveryMode("{\"expiredEndpoints\":[\"" + LEA_ENDPOINT + "\"]}");
        cancelT01();
        Map<String, Object> push = deliveries(created("EVENT_CANCELLED"), "PUSH").getFirst();
        assertEquals("DISCARDED", push.get("status"));
        assertThat(settings(login("lea")).getList("devices"), empty());
        // The e-mail to Lea is not affected
        assertEquals("SENT", deliveries(List.of(of(created("EVENT_CANCELLED"), LEA)), "EMAIL").getFirst().get("status"));
    }

    @Test
    void removedDeviceGetsNoPendingPush() {
        deliveryMode("{\"failing\":[\"PUSH\"]}");
        cancelT01();
        // The first attempt runs in the background right after the commit; wait for it before removing the device
        created("EVENT_CANCELLED");
        given().cookie("stv_session", login("lea")).delete("/api/me/push-subscriptions/" + LEA_PHONE).then().statusCode(204);
        deliveryMode("{}");
        clock("2026-11-16T06:01:00+01:00");
        Map<String, Object> push = deliveries(created("EVENT_CANCELLED"), "PUSH").getFirst();
        assertEquals("DISCARDED", push.get("status"));
        assertEquals(1, push.get("attempts"));
    }

    @Test
    void triggerWithoutChannelsKeepsWorkingForPetra() {
        // Petra triggers: she gets nothing herself (assumption F-06), the others get e-mails
        cancelT01();
        assertThat(created("EVENT_CANCELLED").stream().map(n -> ((Map<?, ?>) n.get("recipient")).get("id")).toList(),
                everyItem(org.hamcrest.Matchers.not(equalTo(PETRA))));
    }
}
