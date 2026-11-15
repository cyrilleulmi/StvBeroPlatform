package ch.stvbero.platform.kern.team;

import ch.stvbero.platform.kern.saison.Saison;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Ein Team in einer Saison (SAI-01, SAI-04), mit dem Namen, den es in dieser Saison trägt.
 * Der Name hängt an der Saison, damit das Archiv den damaligen Namen behält (SAI-02).
 */
@Entity
@Table(name = "team_saison")
@IdClass(TeamSaison.Schluessel.class)
public class TeamSaison extends PanacheEntityBase {

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    public Team team;

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "saison_id")
    public Saison saison;

    public String name;

    public static TeamSaison neu(Team team, Saison saison, String name) {
        TeamSaison ts = new TeamSaison();
        ts.team = team;
        ts.saison = saison;
        ts.name = name;
        return ts;
    }

    /** Zusammengesetzter Schlüssel (team_id, saison_id). */
    public static class Schluessel implements Serializable {
        public UUID team;
        public UUID saison;

        @Override
        public boolean equals(Object o) {
            return o instanceof Schluessel s && Objects.equals(team, s.team) && Objects.equals(saison, s.saison);
        }

        @Override
        public int hashCode() {
            return Objects.hash(team, saison);
        }
    }
}
