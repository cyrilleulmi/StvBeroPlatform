package ch.stvbero.platform.core.line;

import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.team.GroupType;
import ch.stvbero.platform.core.team.Team;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
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
@Table(name = "line_group")
public class Group extends PanacheEntityBase {

    /** Name der Goalie-Gruppe. */
    public static final String GOALIES = "Goalies";

    @Id
    public UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    public Team team;

    @ManyToOne(optional = false)
    @JoinColumn(name = "season_id")
    public Season season;

    @Enumerated(EnumType.STRING)
    public GroupType type;

    public String name;

    @Column(name = "sort_order")
    public Integer sortOrder;

    public static Group line(Team team, Season season, String name, int sortOrder) {
        return create(team, season, GroupType.LINE, name, sortOrder);
    }

    public static Group goalies(Team team, Season season) {
        return create(team, season, GroupType.GOALIES, GOALIES, null);
    }

    private static Group create(Team team, Season season, GroupType type, String name, Integer sortOrder) {
        Group g = new Group();
        g.id = UUID.randomUUID();
        g.team = team;
        g.season = season;
        g.type = type;
        g.name = name;
        g.sortOrder = sortOrder;
        return g;
    }

    /** Alle Gruppen eines Teams in einer Saison, in Anzeigereihenfolge (Linien, dann Goalies). */
    public static List<Group> of(Team team, Season season) {
        return Group.<Group>list("team.id = ?1 and season.id = ?2", team.id, season.id).stream()
                .sorted(java.util.Comparator.comparing(Group::info, LineRules.DISPLAY))
                .toList();
    }

    public LineRules.GroupInfo info() {
        return new LineRules.GroupInfo(id, type, name, sortOrder);
    }
}
