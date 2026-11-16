package ch.stvbero.platform.kern.benachrichtigung;

import ch.stvbero.platform.kern.person.Person;
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
@Table(name = "benachrichtigung")
public class Benachrichtigung extends PanacheEntityBase {

    @Id
    public UUID id;

    /** Stabile Reihenfolge bei gleichem Zeitpunkt; vergibt die Datenbank. */
    @Column(insertable = false, updatable = false)
    public Long laufnummer;

    @Enumerated(EnumType.STRING)
    public BenachrichtigungArt art;

    @ManyToOne(optional = false)
    @JoinColumn(name = "empfaenger_id")
    public Person empfaenger;

    public String text;

    @Column(name = "termin_id")
    public UUID terminId;

    @Column(name = "erstellt_am")
    public Instant erstelltAm;

    @Column(name = "gelesen_am")
    public Instant gelesenAm;

    public boolean gelesen() {
        return gelesenAm != null;
    }
}
