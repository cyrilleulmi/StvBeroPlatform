package ch.stvbero.platform.notification;

import ch.stvbero.platform.core.clock.BackendClock;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Optional;

/**
 * Web Push in profile {@code prod} (F-19, criterion 4): encrypts the push for the device and sends it with a
 * VAPID signature to its push service. On only if {@code STV_VAPID_PUBLIC_KEY}, {@code STV_VAPID_PRIVATE_KEY}
 * and {@code STV_VAPID_SUBJECT} are set (docs/requirements/08-notifications.md).
 */
@ApplicationScoped
@UnlessBuildProperty(name = "stv.test-support", stringValue = "true")
public class WebPushTransport implements PushTransport {

    private static final Logger LOG = Logger.getLogger(WebPushTransport.class);

    /** How long the push service keeps a push for a device that is offline. */
    static final Duration TTL = Duration.ofDays(1);

    @ConfigProperty(name = "stv.push.vapid-public-key")
    Optional<String> publicKey;

    @ConfigProperty(name = "stv.push.vapid-private-key")
    Optional<String> privateKey;

    @ConfigProperty(name = "stv.push.vapid-subject")
    Optional<String> subject;

    @Inject
    BackendClock clock;

    private KeyPair keys;
    private HttpClient http;

    @PostConstruct
    void init() {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        if (publicKey.isEmpty() || privateKey.isEmpty() || subject.isEmpty()) {
            return;
        }
        try {
            keys = WebPush.keyPair(publicKey.get(), privateKey.get());
        } catch (IllegalArgumentException e) {
            LOG.error("STV_VAPID_PUBLIC_KEY / STV_VAPID_PRIVATE_KEY are invalid; push stays off", e);
        }
    }

    @Override
    public boolean available() {
        return keys != null;
    }

    @Override
    public String publicKey() {
        return available() ? publicKey.get().trim() : null;
    }

    @Override
    public Result send(Target target, String payload) {
        if (!available()) {
            throw new IllegalStateException("Push is not configured");
        }
        byte[] body = WebPush.encrypt(payload.getBytes(StandardCharsets.UTF_8), WebPush.decode(target.p256dh()),
                WebPush.decode(target.auth()));
        return send(http, target.endpoint(), body,
                WebPush.vapidAuthorization(target.endpoint(), keys, subject.get().trim(), clock.now()));
    }

    /** One request to the push service (RFC 8030); separate so that the test can use a local server. */
    static Result send(HttpClient http, String endpoint, byte[] body, String authorization) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("TTL", Long.toString(TTL.toSeconds()))
                .header("Urgency", "normal")
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Push service not reachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
        if (DeliveryRules.pushExpired(response.statusCode())) {
            return Result.EXPIRED;
        }
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Push service answered " + response.statusCode());
        }
        return Result.SENT;
    }
}
