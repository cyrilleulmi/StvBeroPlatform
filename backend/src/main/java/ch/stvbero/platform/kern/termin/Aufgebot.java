package ch.stvbero.platform.kern.termin;

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
@Table(name = "aufgebot")
public class Aufgebot extends PanacheEntityBase {

    @Id
    @Column(name = "termin_id")
    public UUID terminId;

    @Column(name = "veroeffentlicht_am")
    public Instant veroeffentlichtAm;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "aufgebot_eintrag", joinColumns = @JoinColumn(name = "termin_id"))
    @Column(name = "person_id")
    public Set<UUID> personIds = new LinkedHashSet<>();

    public AufgebotStatus status() {
        return AufgebotRegeln.status(veroeffentlichtAm != null, personIds.size());
    }
}
