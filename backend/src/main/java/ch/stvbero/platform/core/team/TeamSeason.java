package ch.stvbero.platform.core.team;

import ch.stvbero.platform.core.season.Season;
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
@Table(name = "team_season")
@IdClass(TeamSeason.Key.class)
public class TeamSeason extends PanacheEntityBase {

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    public Team team;

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "season_id")
    public Season season;

    public String name;

    public static TeamSeason create(Team team, Season season, String name) {
        TeamSeason ts = new TeamSeason();
        ts.team = team;
        ts.season = season;
        ts.name = name;
        return ts;
    }

    /** Zusammengesetzter Schlüssel (team_id, saison_id). */
    public static class Key implements Serializable {
        public UUID team;
        public UUID season;

        @Override
        public boolean equals(Object o) {
            return o instanceof Key s && Objects.equals(team, s.team) && Objects.equals(season, s.season);
        }

        @Override
        public int hashCode() {
            return Objects.hash(team, season);
        }
    }
}
