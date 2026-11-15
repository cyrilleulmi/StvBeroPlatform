package ch.stvbero.platform.kern.team;

import ch.stvbero.platform.kern.linie.Gruppe;
import ch.stvbero.platform.kern.person.Person;
import ch.stvbero.platform.kern.saison.Saison;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Verbindung von Person, Team und Funktion in einer Saison (PTF-04, PTF-10). */
@Entity
@Table(name = "teamzugehoerigkeit")
public class Teamzugehoerigkeit extends PanacheEntityBase {

    @Id
    public UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "person_id")
    public Person person;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    public Team team;

    @ManyToOne(optional = false)
    @JoinColumn(name = "saison_id")
    public Saison saison;

    @Enumerated(EnumType.STRING)
    public Funktion funktion;

    /**
     * Linie oder Goalies, nur bei Funktion SPIELERIN (LIN-01, LIN-10). Als Menge, damit «mehrere Linien
     * pro Spielerin» später ohne Umbau geht (LIN-04); heute höchstens eine (LIN-02).
     */
    @ManyToMany
    @JoinTable(name = "gruppenzuweisung",
            joinColumns = @JoinColumn(name = "teamzugehoerigkeit_id"),
            inverseJoinColumns = @JoinColumn(name = "gruppe_id"))
    public Set<Gruppe> gruppen = new LinkedHashSet<>();

    public static Teamzugehoerigkeit neu(Person person, Team team, Saison saison, Funktion funktion) {
        Teamzugehoerigkeit z = new Teamzugehoerigkeit();
        z.id = UUID.randomUUID();
        z.person = person;
        z.team = team;
        z.saison = saison;
        z.funktion = funktion;
        return z;
    }
}
