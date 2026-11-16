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
 * A line or the goalies of a team in a season (LIN-03, LIN-05, LIN-11).
 * Lines have a sort order (LIN-09), goalies do not.
 */
@Entity
@Table(name = "line_group")
public class Group extends PanacheEntityBase {

    /** Name of the goalie group. */
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
        Group group = new Group();
        group.id = UUID.randomUUID();
        group.team = team;
        group.season = season;
        group.type = type;
        group.name = name;
        group.sortOrder = sortOrder;
        return group;
    }

    /** All groups of a team in a season, in display order (lines, then goalies). */
    public static List<Group> of(Team team, Season season) {
        return Group.<Group>list("team.id = ?1 and season.id = ?2", team.id, season.id).stream()
                .sorted(java.util.Comparator.comparing(Group::info, LineRules.DISPLAY))
                .toList();
    }

    public LineRules.GroupInfo info() {
        return new LineRules.GroupInfo(id, type, name, sortOrder);
    }
}
