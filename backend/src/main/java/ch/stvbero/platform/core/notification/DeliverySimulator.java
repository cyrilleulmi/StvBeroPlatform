package ch.stvbero.platform.core.notification;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Replaces SMTP and the push services in the profiles {@code dev} and {@code test} (F-19, criterion 8): nothing
 * leaves the app. Every e-mail and push stays recorded as a delivery and can be read through
 * {@code GET /api/test/notifications}. With {@code PUT /api/test/delivery} a channel can fail (to see the retry)
 * and push endpoints can count as expired. The VAPID key exists only in memory and changes with every start.
 */
@ApplicationScoped
@IfBuildProperty(name = "stv.test-support", stringValue = "true")
public class DeliverySimulator implements EmailTransport, PushTransport {

    private final String publicKey = WebPush.encode(
            WebPush.raw((java.security.interfaces.ECPublicKey) WebPush.newKeyPair().getPublic()));
    private final Set<NotificationChannel> failing = ConcurrentHashMap.newKeySet();
    private final Set<String> expired = ConcurrentHashMap.newKeySet();

    /** Sets which channels fail and which push endpoints the «push service» reports as gone. */
    public void set(Set<NotificationChannel> failingChannels, Set<String> expiredEndpoints) {
        failing.clear();
        failing.addAll(failingChannels.isEmpty() ? EnumSet.noneOf(NotificationChannel.class) : failingChannels);
        expired.clear();
        expired.addAll(expiredEndpoints);
    }

    public void reset() {
        set(Set.of(), Set.of());
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String publicKey() {
        return publicKey;
    }

    @Override
    public void send(DeliveryRules.Email email) {
        if (failing.contains(NotificationChannel.EMAIL)) {
            throw new IllegalStateException("Simulated: mail server not reachable");
        }
    }

    @Override
    public Result send(Target target, String payload) {
        if (expired.contains(target.endpoint())) {
            return Result.EXPIRED;
        }
        if (failing.contains(NotificationChannel.PUSH)) {
            throw new IllegalStateException("Simulated: push service not reachable");
        }
        return Result.SENT;
    }
}
