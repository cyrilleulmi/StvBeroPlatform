package ch.stvbero.platform.kern.team;

import ch.stvbero.platform.kern.saison.Saison;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Eine Mannschaft des Vereins (PTF-02). Gehört zu einer oder mehreren Saisons. */
@Entity
@Table(name = "team")
public class Team extends PanacheEntityBase {

    @Id
    public UUID id;

    public String name;

    /** Team-ID bei swiss unihockey (F-07). */
    @Column(name = "su_team_id")
    public Integer suTeamId;

    @ManyToMany
    @JoinTable(name = "team_saison",
            joinColumns = @JoinColumn(name = "team_id"),
            inverseJoinColumns = @JoinColumn(name = "saison_id"))
    public Set<Saison> saisons = new HashSet<>();

    public boolean gehoertZu(Saison saison) {
        return saisons.stream().anyMatch(s -> s.id.equals(saison.id));
    }
}
