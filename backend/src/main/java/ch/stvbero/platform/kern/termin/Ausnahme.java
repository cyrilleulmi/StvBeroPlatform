package ch.stvbero.platform.kern.termin;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** Zeitraum, der von einer Serie ausgenommen ist, z.B. Ferien (TER-07). */
@Entity
@Table(name = "ausnahme")
public class Ausnahme extends PanacheEntityBase {

    @Id
    public UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "serie_id")
    public Serie serie;

    public LocalDate von;

    public LocalDate bis;

    public String grund;
}
