package ch.stvbero.platform.core.team;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.line.Group;
import ch.stvbero.platform.core.season.Season;
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
    public List<TeamSeason> seasons = new ArrayList<>();

    public Optional<TeamSeason> in(Season season) {
        return seasons.stream().filter(ts -> ts.season.id.equals(season.id)).findFirst();
    }

    public boolean belongsTo(Season season) {
        return in(season).isPresent();
    }

    /** Name in dieser Saison. */
    public String nameIn(Season season) {
        return in(season).map(ts -> ts.name)
                .orElseThrow(() -> DomainError.notFound("Dieses Team gibt es in der Saison " + season.name + " nicht."));
    }

    /**
     * Team in die Saison aufnehmen, mit dem angegebenen Namen. Es bekommt dort seine Goalies (LIN-05).
     * Ist es schon dabei, bleibt alles, wie es ist.
     */
    public void addToSeason(Season season, String name) {
        if (!belongsTo(season)) {
            TeamSeason ts = TeamSeason.create(this, season, name);
            seasons.add(ts);
            ts.persist();
            Group.goalies(this, season).persist();
        }
    }
}
