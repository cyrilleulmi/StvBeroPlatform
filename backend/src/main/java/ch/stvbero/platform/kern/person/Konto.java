package ch.stvbero.platform.kern.person;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Optional;
import java.util.UUID;

/** Ein Login. Getrennt von der Person (PTF-09); heute gehört zu jedem Konto genau eine Person. */
@Entity
@Table(name = "konto")
public class Konto extends PanacheEntityBase {

    @Id
    public UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "person_id")
    public Person person;

    /** Schlüssel für die Anmeldung im Testmodus, z.B. «petra». */
    public String schluessel;

    public static Optional<Konto> mitSchluessel(String schluessel) {
        return find("schluessel", schluessel).firstResultOptional();
    }
}
