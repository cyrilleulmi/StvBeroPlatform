package ch.stvbero.platform.core.team;

import ch.stvbero.platform.core.line.Group;
import ch.stvbero.platform.core.person.Person;
import ch.stvbero.platform.core.season.Season;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
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

/** Links a person, a team and a team role in one season (PTF-04, PTF-10). */
@Entity
@Table(name = "team_membership")
public class TeamMembership extends PanacheEntityBase {

    @Id
    public UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "person_id")
    public Person person;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    public Team team;

    @ManyToOne(optional = false)
    @JoinColumn(name = "season_id")
    public Season season;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_role")
    public TeamRole teamRole;

    /**
     * Line or goalies, only for the team role PLAYER (LIN-01, LIN-10). A set, so that "several lines per
     * player" can be added later without rework (LIN-04); today at most one (LIN-02).
     */
    @ManyToMany
    @JoinTable(name = "group_assignment",
            joinColumns = @JoinColumn(name = "team_membership_id"),
            inverseJoinColumns = @JoinColumn(name = "group_id"))
    public Set<Group> groups = new LinkedHashSet<>();

    public static TeamMembership create(Person person, Team team, Season season, TeamRole teamRole) {
        TeamMembership membership = new TeamMembership();
        membership.id = UUID.randomUUID();
        membership.person = person;
        membership.team = team;
        membership.season = season;
        membership.teamRole = teamRole;
        return membership;
    }
}
