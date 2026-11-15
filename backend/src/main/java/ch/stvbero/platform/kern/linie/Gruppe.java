package ch.stvbero.platform.kern.linie;

import ch.stvbero.platform.kern.saison.Saison;
import ch.stvbero.platform.kern.team.GruppenArt;
import ch.stvbero.platform.kern.team.Team;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.List;
import java.util.UUID;

/**
 * Eine Linie oder die Goalies eines Teams in einer Saison (LIN-03, LIN-05, LIN-11).
 * Linien haben eine Reihenfolge (LIN-09), Goalies nicht.
 */
@Entity
@Table(name = "gruppe")
public class Gruppe extends PanacheEntityBase {

    /** Name der Goalie-Gruppe. */
    public static final String GOALIES = "Goalies";

    @Id
    public UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    public Team team;

    @ManyToOne(optional = false)
    @JoinColumn(name = "saison_id")
    public Saison saison;

    @Enumerated(EnumType.STRING)
    public GruppenArt art;

    public String name;

    public Integer reihenfolge;

    public static Gruppe linie(Team team, Saison saison, String name, int reihenfolge) {
        return neu(team, saison, GruppenArt.LINIE, name, reihenfolge);
    }

    public static Gruppe goalies(Team team, Saison saison) {
        return neu(team, saison, GruppenArt.GOALIES, GOALIES, null);
    }

    private static Gruppe neu(Team team, Saison saison, GruppenArt art, String name, Integer reihenfolge) {
        Gruppe g = new Gruppe();
        g.id = UUID.randomUUID();
        g.team = team;
        g.saison = saison;
        g.art = art;
        g.name = name;
        g.reihenfolge = reihenfolge;
        return g;
    }

    /** Alle Gruppen eines Teams in einer Saison, in Anzeigereihenfolge (Linien, dann Goalies). */
    public static List<Gruppe> von(Team team, Saison saison) {
        return Gruppe.<Gruppe>list("team.id = ?1 and saison.id = ?2", team.id, saison.id).stream()
                .sorted(java.util.Comparator.comparing(Gruppe::info, LinienRegeln.ANZEIGE))
                .toList();
    }

    public LinienRegeln.GruppeInfo info() {
        return new LinienRegeln.GruppeInfo(id, art, name, reihenfolge);
    }
}
