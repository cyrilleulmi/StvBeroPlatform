package ch.stvbero.platform.notification;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F-19: who gets e-mails, what e-mails and pushes say, when a failed delivery is retried. */
class DeliveryRulesTest {

    static final UUID EVENT = UUID.fromString("40000000-0000-4000-8000-000000000003");
    static final Instant TUE_24_11_1830 = Instant.parse("2026-11-24T17:30:00Z");
    static final Instant NOW = Instant.parse("2026-11-16T05:00:00Z");

    @Test
    void criterion2EmailIsOnByDefaultWithAnAddress() {
        assertTrue(DeliveryRules.emailOn("anna@example.ch", null));
        assertTrue(DeliveryRules.emailOn("anna@example.ch", true));
        assertFalse(DeliveryRules.emailOn("anna@example.ch", false));
        // Without an address never, whatever was chosen
        assertFalse(DeliveryRules.emailOn(null, null));
        assertFalse(DeliveryRules.emailOn("  ", true));
    }

    @Test
    void criterion7RetriesAfter1_5_30And120MinutesThenGivesUp() {
        assertEquals(Optional.of(NOW.plusSeconds(60)), DeliveryRules.nextAttempt(1, NOW));
        assertEquals(Optional.of(NOW.plusSeconds(300)), DeliveryRules.nextAttempt(2, NOW));
        assertEquals(Optional.of(NOW.plusSeconds(1800)), DeliveryRules.nextAttempt(3, NOW));
        assertEquals(Optional.of(NOW.plusSeconds(7200)), DeliveryRules.nextAttempt(4, NOW));
        assertEquals(5, DeliveryRules.MAX_ATTEMPTS);
        assertEquals(Optional.empty(), DeliveryRules.nextAttempt(5, NOW));
        assertEquals(Optional.empty(), DeliveryRules.nextAttempt(0, NOW));
    }

    @Test
    void criterion7PushServiceReportsExpiredWith404Or410() {
        assertTrue(DeliveryRules.pushExpired(410));
        assertTrue(DeliveryRules.pushExpired(404));
        assertFalse(DeliveryRules.pushExpired(429));
        assertFalse(DeliveryRules.pushExpired(500));
        assertFalse(DeliveryRules.pushExpired(201));
    }

    @Test
    void criterion5SubjectWithTheEssence() {
        assertEquals("Abgesagt: Training Di 24.11.",
                DeliveryRules.subject(NotificationType.EVENT_CANCELLED, "Training", TUE_24_11_1830));
        assertEquals("Bitte gib Bescheid: Training Di 24.11.",
                DeliveryRules.subject(NotificationType.REMINDER, "Training", TUE_24_11_1830));
        assertEquals("Aufgebot ist da: Spieltag Di 24.11.",
                DeliveryRules.subject(NotificationType.CALL_UP_PUBLISHED, "Spieltag", TUE_24_11_1830));
        assertEquals("Aufgebot geändert: Spieltag Di 24.11.",
                DeliveryRules.subject(NotificationType.CALL_UP_CHANGED, "Spieltag", TUE_24_11_1830));
        assertEquals("Kurzfristige Abmeldung: Training Di 24.11.",
                DeliveryRules.subject(NotificationType.LATE_CANCELLATION, "Training", TUE_24_11_1830));
        assertEquals("Abmeldung im Aufgebot: Spieltag Di 24.11.",
                DeliveryRules.subject(NotificationType.CALLED_UP_PLAYER_DECLINED, "Spieltag", TUE_24_11_1830));
        assertEquals("Spiel geändert: Spieltag Di 24.11.",
                DeliveryRules.subject(NotificationType.MATCH_CHANGED, "Spieltag", TUE_24_11_1830));
        // A rescheduling: the text names old and new date, the subject only the event
        assertEquals("Verschoben: Training",
                DeliveryRules.subject(NotificationType.EVENT_RESCHEDULED, "Training", TUE_24_11_1830));
        assertEquals("Abgesagt", DeliveryRules.subject(NotificationType.EVENT_CANCELLED, null, null));
    }

    @Test
    void ben12PathLeadsToTheEvent() {
        assertEquals("/termine/" + EVENT, DeliveryRules.path(EVENT));
        assertEquals("/benachrichtigungen", DeliveryRules.path(null));
    }

    @Test
    void criterion5EmailInGermanWithButtonAndHowToSwitchOff() {
        DeliveryRules.Email email = DeliveryRules.email("anna@example.ch", "Anna", "Abgesagt: Training Di 24.11.",
                "Training Di 24.11. ist abgesagt: Halle besetzt", "https://app.example.ch/", "/termine/" + EVENT);
        assertEquals("anna@example.ch", email.to());
        assertEquals("Abgesagt: Training Di 24.11.", email.subject());
        assertEquals("""
                Hallo Anna

                Training Di 24.11. ist abgesagt: Halle besetzt

                Termin ansehen: https://app.example.ch/termine/40000000-0000-4000-8000-000000000003

                --\s
                STV Bero
                Du bekommst diese E-Mail, weil E-Mails in deinem Profil eingeschaltet sind. Ausschalten kannst du sie im Profil unter «Benachrichtigungen»: https://app.example.ch/profil
                """, email.text());
        String html = email.html();
        assertTrue(html.contains("<html lang=\"de-CH\">"), html);
        assertTrue(html.contains("name=\"viewport\" content=\"width=device-width, initial-scale=1\""), html);
        assertTrue(html.contains("max-width:480px"), html);
        assertTrue(html.contains("<p style=\"margin:0 0 24px;font-size:17px;line-height:1.5;\">Training Di 24.11. ist abgesagt: Halle besetzt</p>"), html);
        assertTrue(html.contains("<a href=\"https://app.example.ch/termine/" + EVENT + "\""), html);
        assertTrue(html.contains(">Termin ansehen</a>"), html);
        assertTrue(html.contains("<a href=\"https://app.example.ch/profil\""), html);
        assertTrue(html.contains("Ausschalten kannst du sie im Profil unter «Benachrichtigungen»"), html);
    }

    @Test
    void emailEscapesHtmlAndWorksWithoutEventAndName() {
        DeliveryRules.Email email = DeliveryRules.email("x@example.ch", null, "Abgesagt",
                "Training <b>ist</b> abgesagt: \"Halle\" & Co", "http://localhost:4200", "/benachrichtigungen");
        assertTrue(email.text().startsWith("Hallo\n\n"), email.text());
        assertTrue(email.text().contains("In der App öffnen: http://localhost:4200/benachrichtigungen"), email.text());
        assertTrue(email.html().contains("Training &lt;b&gt;ist&lt;/b&gt; abgesagt: &quot;Halle&quot; &amp; Co"));
        assertFalse(email.html().contains("<b>"));
    }

    @Test
    void criterion4PushPayloadForTheServiceWorker() {
        UUID id = UUID.fromString("90000000-0000-4000-8000-000000000001");
        assertEquals("{\"title\":\"Abgesagt: Training Di 24.11.\",\"body\":\"Training \\\"Di\\\" 24.11.\\nabgesagt\","
                        + "\"url\":\"/termine/" + EVENT + "\",\"tag\":\"" + id + "\"}",
                DeliveryRules.pushPayload(id, "Abgesagt: Training Di 24.11.", "Training \"Di\" 24.11.\nabgesagt",
                        "/termine/" + EVENT));
    }

    @Test
    void criterion3DeviceLabelFromTheUserAgent() {
        assertEquals("Chrome auf Android", DeliveryRules.deviceLabel(
                "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"));
        assertEquals("Safari auf iPhone", DeliveryRules.deviceLabel(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"));
        assertEquals("Firefox auf Windows", DeliveryRules.deviceLabel(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0"));
        assertEquals("Edge auf Windows", DeliveryRules.deviceLabel(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36 Edg/130.0"));
        assertEquals("Safari auf Mac", DeliveryRules.deviceLabel(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"));
        assertEquals("Unbekanntes Gerät", DeliveryRules.deviceLabel(null));
        assertEquals("Unbekanntes Gerät", DeliveryRules.deviceLabel("curl/8.0"));
    }
}
