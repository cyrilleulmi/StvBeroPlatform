package ch.stvbero.platform.core.notification;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One e-mail or one push of a notification (F-19). Recorded in the transaction of the triggering change and
 * delivered by {@link Deliveries} after it (outbox). At most one per notification, channel and address.
 */
@Entity
@Table(name = "delivery")
public class Delivery extends PanacheEntityBase {

    @Id
    public UUID id;

    /** Stable order for deliveries with the same time; assigned by the database. */
    @Column(name = "sequence_number", insertable = false, updatable = false)
    public Long sequenceNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "notification_id")
    public Notification notification;

    @Enumerated(EnumType.STRING)
    public NotificationChannel channel;

    /** Device of a push; {@code null} for e-mails and once the device was removed. */
    @ManyToOne
    @JoinColumn(name = "push_subscription_id")
    public PushSubscription pushSubscription;

    /** E-mail address or push endpoint at the time of the notification. */
    public String address;

    public String subject;

    public String text;

    /** Path in the app, e.g. {@code /termine/<id>}. */
    public String path;

    @Enumerated(EnumType.STRING)
    public DeliveryStatus status;

    public int attempts;

    @Column(name = "next_attempt_at")
    public Instant nextAttemptAt;

    @Column(name = "last_error")
    public String lastError;

    @Column(name = "created_at")
    public Instant createdAt;

    @Column(name = "sent_at")
    public Instant sentAt;

    static Delivery pending(UUID notificationId, NotificationChannel channel, String address, String subject,
                            String text, String path, Instant now) {
        Delivery delivery = new Delivery();
        delivery.id = UUID.randomUUID();
        delivery.notification = Notification.getEntityManager().getReference(Notification.class, notificationId);
        delivery.channel = channel;
        delivery.address = address;
        delivery.subject = subject;
        delivery.text = text;
        delivery.path = path;
        delivery.status = DeliveryStatus.PENDING;
        delivery.createdAt = now;
        delivery.nextAttemptAt = now;
        return delivery;
    }
}
