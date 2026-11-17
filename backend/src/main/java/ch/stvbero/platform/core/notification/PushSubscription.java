package ch.stvbero.platform.core.notification;

import ch.stvbero.platform.core.person.Person;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A device on which a person allowed push (F-19, Web Push). A person can have several. */
@Entity
@Table(name = "push_subscription")
public class PushSubscription extends PanacheEntityBase {

    @Id
    public UUID id;

    /** Order of registration; assigned by the database. */
    @Column(name = "sequence_number", insertable = false, updatable = false)
    public Long sequenceNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "person_id")
    public Person person;

    /** Address at the push service of the browser; identifies the device. */
    public String endpoint;

    /** Public key of the browser (P-256, base64url) for the encryption (RFC 8291). */
    public String p256dh;

    /** Authentication secret of the browser (base64url, RFC 8291). */
    public String auth;

    /** Readable name, e.g. «Chrome auf Android». */
    public String label;

    @Column(name = "created_at")
    public Instant createdAt;
}
