package ch.stvbero.platform.core.event;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Aufgebot eines Spieltags (AUF-02): die ausgewählten Spielerinnen und ob es veröffentlicht ist (AUF-23). */
@Entity
@Table(name = "call_up")
public class CallUp extends PanacheEntityBase {

    @Id
    @Column(name = "event_id")
    public UUID eventId;

    @Column(name = "published_at")
    public Instant publishedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "call_up_entry", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "person_id")
    public Set<UUID> personIds = new LinkedHashSet<>();

    public CallUpStatus status() {
        return CallUpRules.status(publishedAt != null, personIds.size());
    }
}
