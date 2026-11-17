package ch.stvbero.platform.core.notification;

import ch.stvbero.platform.core.clock.BackendClock;
import io.quarkus.arc.Arc;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Delivers recorded e-mails and pushes (F-19, outbox). Right after the triggering change is committed, a
 * background thread sends what is due; a failure therefore never blocks the change (criterion 7). Failed
 * attempts are retried after {@link DeliveryRules#RETRY_DELAYS}; the scheduler checks every minute. Every
 * delivery is attempted in its own transaction, so one failure does not touch the others.
 */
@ApplicationScoped
public class Deliveries {

    private static final Logger LOG = Logger.getLogger(Deliveries.class);
    private static final String REGISTERED = Deliveries.class.getName() + ".afterCommit";
    private static final int ERROR_LENGTH = 500;

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    @Inject
    EmailTransport email;

    @Inject
    PushTransport push;

    @Inject
    TransactionSynchronizationRegistry transactions;

    /** Address of the app, for the button in the e-mail (criterion 5). */
    @ConfigProperty(name = "stv.app-url")
    String appUrl;

    private final ExecutorService background = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "stv-deliveries");
        thread.setDaemon(true);
        return thread;
    });

    /** One run at a time in this process; {@code skip locked} keeps several processes apart. */
    private final ReentrantLock running = new ReentrantLock();

    void stop(@Observes ShutdownEvent event) {
        background.shutdownNow();
    }

    /** Called by the channels inside the triggering transaction: deliver right after the commit. */
    void afterCommit() {
        if (transactions.getResource(REGISTERED) != null) {
            return;
        }
        transactions.putResource(REGISTERED, Boolean.TRUE);
        transactions.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) {
                    background.execute(() -> {
                        try {
                            Arc.container().instance(Deliveries.class).get().processDue();
                        } catch (RuntimeException e) {
                            LOG.warn("Delivering notifications failed", e);
                        }
                    });
                }
            }
        });
    }

    @Scheduled(every = "60s", delayed = "15s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void regularly() {
        processDue();
    }

    /**
     * Sends everything that is due, by the backend clock. Returns the number of attempts. Callers wait while
     * another run is busy, so that afterwards everything that was due is processed (test support relies on it).
     */
    @ActivateRequestContext
    public int processDue() {
        running.lock();
        try {
            Instant now = clock.now();
            List<UUID> due = QuarkusTransaction.requiringNew().call(() -> em.createQuery("""
                            select d.id from Delivery d where d.status = :pending and d.nextAttemptAt <= :now
                            order by d.nextAttemptAt, d.sequenceNumber""", UUID.class)
                    .setParameter("pending", DeliveryStatus.PENDING).setParameter("now", now)
                    .getResultList());
            int attempts = 0;
            for (UUID id : due) {
                if (QuarkusTransaction.requiringNew().call(() -> attempt(id, now))) {
                    attempts++;
                }
            }
            return attempts;
        } finally {
            running.unlock();
        }
    }

    /** Runs {@code action} while no delivery runs (test support: reset). */
    public void exclusively(Runnable action) {
        running.lock();
        try {
            action.run();
        } finally {
            running.unlock();
        }
    }

    private boolean attempt(UUID id, Instant now) {
        Delivery delivery = em.find(Delivery.class, id, LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout", -2));
        if (delivery == null || delivery.status != DeliveryStatus.PENDING || delivery.nextAttemptAt.isAfter(now)) {
            return false;
        }
        if (delivery.channel == NotificationChannel.PUSH && delivery.pushSubscription == null) {
            // Push switched off for the device in the meantime: nothing to send, no attempt
            discard(delivery, "Gerät entfernt");
            return true;
        }
        delivery.attempts++;
        try {
            switch (delivery.channel) {
                case EMAIL -> sendEmail(delivery);
                case PUSH -> sendPush(delivery);
            }
            if (delivery.status == DeliveryStatus.PENDING) {
                delivery.status = DeliveryStatus.SENT;
                delivery.sentAt = now;
                delivery.nextAttemptAt = null;
                delivery.lastError = null;
            }
        } catch (RuntimeException e) {
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            delivery.lastError = DeliveryRules.truncate(error, ERROR_LENGTH);
            DeliveryRules.nextAttempt(delivery.attempts, now).ifPresentOrElse(
                    next -> delivery.nextAttemptAt = next,
                    () -> {
                        delivery.status = DeliveryStatus.FAILED;
                        delivery.nextAttemptAt = null;
                    });
            LOG.warnf("%s to %s failed (attempt %d): %s", delivery.channel, delivery.notification.recipient.id,
                    delivery.attempts, error);
        }
        return true;
    }

    private void sendEmail(Delivery delivery) {
        email.send(DeliveryRules.email(delivery.address, delivery.notification.recipient.firstName,
                delivery.subject, delivery.text, appUrl, delivery.path));
    }

    private void sendPush(Delivery delivery) {
        PushSubscription device = delivery.pushSubscription;
        PushTransport.Result result = push.send(new PushTransport.Target(device.endpoint, device.p256dh, device.auth),
                DeliveryRules.pushPayload(delivery.notification.id, delivery.subject, delivery.text, delivery.path));
        if (result == PushTransport.Result.EXPIRED) {
            // The push service no longer knows the device: remove it (criterion 7)
            discard(delivery, "Abo vom Push-Dienst als abgelaufen gemeldet");
            delivery.pushSubscription = null;
            em.flush();
            device.delete();
        }
    }

    private static void discard(Delivery delivery, String reason) {
        delivery.status = DeliveryStatus.DISCARDED;
        delivery.nextAttemptAt = null;
        delivery.lastError = reason;
    }
}
