package ch.stvbero.platform.core.notification;

import ch.stvbero.platform.core.person.Person;
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

/** Benachrichtigung in der App (E-56): ungelesen, bis die Person sie öffnet (zustaende.md). */
@Entity
@Table(name = "notification")
public class Notification extends PanacheEntityBase {

    @Id
    public UUID id;

    /** Stabile Reihenfolge bei gleichem Zeitpunkt; vergibt die Datenbank. */
    @Column(name = "sequence_number", insertable = false, updatable = false)
    public Long sequenceNumber;

    @Enumerated(EnumType.STRING)
    public NotificationType type;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recipient_id")
    public Person recipient;

    public String text;

    @Column(name = "event_id")
    public UUID eventId;

    @Column(name = "created_at")
    public Instant createdAt;

    @Column(name = "read_at")
    public Instant readAt;

    public boolean read() {
        return readAt != null;
    }
}
