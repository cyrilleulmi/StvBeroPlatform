package ch.stvbero.platform.kern.anmeldung;

import ch.stvbero.platform.kern.person.Konto;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sitzung")
public class Sitzung extends PanacheEntityBase {

    @Id
    public String token;

    @ManyToOne(optional = false)
    @JoinColumn(name = "konto_id")
    public Konto konto;

    @Column(name = "erstellt_am")
    public Instant erstelltAm;
}
