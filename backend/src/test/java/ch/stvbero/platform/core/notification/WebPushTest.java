package ch.stvbero.platform.core.notification;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F-19, criterion 4: Web Push encryption (RFC 8291) and VAPID (RFC 8292) without a library. */
class WebPushTest {

    // RFC 8291, appendix A
    static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    static final String AS_PUBLIC = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    static final String UA_PUBLIC = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    static final String BODY = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    @Test
    void encryptionMatchesTheExampleOfRfc8291() {
        byte[] body = WebPush.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8), WebPush.decode(UA_PUBLIC),
                WebPush.decode(AUTH), WebPush.keyPair(AS_PUBLIC, AS_PRIVATE), WebPush.decode(SALT));
        assertEquals(BODY, WebPush.encode(body));
    }

    @Test
    void theBrowserCanDecryptWhatWeSend() throws Exception {
        KeyPair browser = WebPush.newKeyPair();
        byte[] auth = new byte[16];
        Arrays.fill(auth, (byte) 7);
        String payload = DeliveryRules.pushPayload(java.util.UUID.randomUUID(), "Abgesagt: Training Di 24.11.",
                "Training Di 24.11. ist abgesagt: Halle besetzt", "/termine/x");
        byte[] body = WebPush.encrypt(payload.getBytes(StandardCharsets.UTF_8),
                WebPush.raw((ECPublicKey) browser.getPublic()), auth);
        assertEquals(payload, new String(WebPush.decrypt(body, browser, auth), StandardCharsets.UTF_8));
        // Every push has its own server key and salt
        byte[] again = WebPush.encrypt(payload.getBytes(StandardCharsets.UTF_8),
                WebPush.raw((ECPublicKey) browser.getPublic()), auth);
        assertTrue(!Arrays.equals(body, again));
    }

    @Test
    void keysRoundTripAndInvalidKeysAreRefused() {
        KeyPair keys = WebPush.keyPair(UA_PUBLIC, UA_PRIVATE);
        assertEquals(UA_PUBLIC, WebPush.encode(WebPush.raw((ECPublicKey) keys.getPublic())));
        assertThrows(IllegalArgumentException.class, () -> WebPush.publicKey(new byte[65]));
        assertThrows(IllegalArgumentException.class, () -> WebPush.publicKey(WebPush.decode(AUTH)));
        assertThrows(IllegalArgumentException.class, () -> WebPush.keyPair(UA_PUBLIC, AUTH));
        assertThrows(IllegalArgumentException.class,
                () -> WebPush.encrypt(new byte[WebPush.MAX_PLAINTEXT + 1], WebPush.decode(UA_PUBLIC), WebPush.decode(AUTH)));
    }

    @Test
    void vapidTokenIsSignedForTheOriginOfTheEndpoint() throws Exception {
        KeyPair vapid = WebPush.keyPair(AS_PUBLIC, AS_PRIVATE);
        Instant now = Instant.parse("2026-11-16T05:00:00Z");
        String header = WebPush.vapidAuthorization("https://fcm.googleapis.com/fcm/send/abc:def", vapid,
                "mailto:info@example.ch", now);
        assertTrue(header.startsWith("vapid t="), header);
        assertTrue(header.endsWith(", k=" + AS_PUBLIC), header);
        String token = header.substring("vapid t=".length(), header.indexOf(','));
        String[] parts = token.split("\\.");
        assertEquals("{\"typ\":\"JWT\",\"alg\":\"ES256\"}", new String(WebPush.decode(parts[0]), StandardCharsets.UTF_8));
        assertEquals("{\"aud\":\"https://fcm.googleapis.com\",\"exp\":" + (now.getEpochSecond() + 12 * 3600)
                        + ",\"sub\":\"mailto:info@example.ch\"}",
                new String(WebPush.decode(parts[1]), StandardCharsets.UTF_8));
        Signature verify = Signature.getInstance("SHA256withECDSAinP1363Format");
        verify.initVerify(vapid.getPublic());
        verify.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(verify.verify(WebPush.decode(parts[2])));
    }

    @Test
    void requestToThePushServiceAndItsAnswers() throws Exception {
        AtomicReference<String> headers = new AtomicReference<>();
        AtomicReference<byte[]> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            headers.set(exchange.getRequestMethod() + " " + exchange.getRequestHeaders().getFirst("TTL") + " "
                    + exchange.getRequestHeaders().getFirst("Content-Encoding") + " "
                    + exchange.getRequestHeaders().getFirst("Authorization"));
            received.set(exchange.getRequestBody().readAllBytes());
            int status = switch (exchange.getRequestURI().getPath()) {
                case "/gone" -> 410;
                case "/unknown" -> 404;
                case "/busy" -> 429;
                default -> 201;
            };
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient http = HttpClient.newHttpClient();
            byte[] body = {1, 2, 3};
            assertEquals(PushTransport.Result.SENT, WebPushTransport.send(http, base + "/ok", body, "vapid t=x, k=y"));
            assertEquals("POST 86400 aes128gcm vapid t=x, k=y", headers.get());
            assertArrayEquals(body, received.get());
            assertEquals(PushTransport.Result.EXPIRED, WebPushTransport.send(http, base + "/gone", body, "a"));
            assertEquals(PushTransport.Result.EXPIRED, WebPushTransport.send(http, base + "/unknown", body, "a"));
            assertThrows(IllegalStateException.class, () -> WebPushTransport.send(http, base + "/busy", body, "a"));
        } finally {
            server.stop(0);
        }
    }
}
