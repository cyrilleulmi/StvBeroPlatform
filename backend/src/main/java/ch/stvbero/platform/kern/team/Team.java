package ch.stvbero.platform.kern.team;

import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.saison.Saison;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Eine Mannschaft des Vereins (PTF-02). Gehört zu einer oder mehreren Saisons.
 * Der Name gilt pro Saison ({@link TeamSaison}), damit das Archiv unverändert bleibt (SAI-02).
 */
@Entity
@Table(name = "team")
public class Team extends PanacheEntityBase {

    @Id
    public UUID id;

    /** Team-ID bei swiss unihockey (F-07). */
    @Column(name = "su_team_id")
    public Integer suTeamId;

    @OneToMany(mappedBy = "team", cascade = CascadeType.PERSIST)
    public List<TeamSaison> saisons = new ArrayList<>();

    public Optional<TeamSaison> in(Saison saison) {
        return saisons.stream().filter(ts -> ts.saison.id.equals(saison.id)).findFirst();
    }

    public boolean gehoertZu(Saison saison) {
        return in(saison).isPresent();
    }

    /** Name in dieser Saison. */
    public String nameIn(Saison saison) {
        return in(saison).map(ts -> ts.name)
                .orElseThrow(() -> FachFehler.nichtGefunden("Dieses Team gibt es in der Saison " + saison.name + " nicht."));
    }

    /** Team in die Saison aufnehmen, mit dem angegebenen Namen. Ist es schon dabei, bleibt alles, wie es ist. */
    public void aufnehmenIn(Saison saison, String name) {
        if (!gehoertZu(saison)) {
            TeamSaison ts = TeamSaison.neu(this, saison, name);
            saisons.add(ts);
            ts.persist();
        }
    }
}
