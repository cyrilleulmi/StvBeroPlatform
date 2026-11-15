package ch.stvbero.platform.kern.saison;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Zeitraum, für den Teamzugehörigkeiten und Linien gelten. */
@Entity
@Table(name = "saison")
public class Saison extends PanacheEntityBase {

    @Id
    public UUID id;

    public String name;

    public LocalDate von;

    public LocalDate bis;

    public boolean aktuell;

    public static Optional<Saison> aktuelle() {
        return find("aktuell", true).firstResultOptional();
    }
}
