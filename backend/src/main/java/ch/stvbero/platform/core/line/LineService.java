package ch.stvbero.platform.core.line;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.error.Unique;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.person.PersonSummary;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.team.TeamRole;
import ch.stvbero.platform.core.team.GroupType;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.team.TeamDetailDto;
import ch.stvbero.platform.core.team.TeamMembership;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lines and goalies of a team in the current season (LIN). The coaches of the team and admins may manage
 * them (LIN-07, RR-03); all members of the team may view them.
 */
@ApplicationScoped
public class LineService {

    private static final String NAME_TAKEN = "line_group_name_unique";

    @Inject
    EntityManager em;

    /** Lineup of a team in a season: groups in display order, then «Ohne Linie». */
    public static TeamDetailDto.Lineup lineup(Team team, Season season,
                                                             List<TeamMembership> memberships) {
        List<TeamMembership> players = memberships.stream()
                .filter(m -> m.teamRole == TeamRole.PLAYER).toList();
        Map<UUID, Set<UUID>> assignments = new HashMap<>();
        for (TeamMembership player : players) {
            assignments.put(player.person.id, player.groups.stream().map(g -> g.id).collect(Collectors.toSet()));
        }
        return LineRules.buildLineup(Group.of(team, season).stream().map(Group::info).toList(),
                players.stream().map(m -> PersonSummary.of(m.person)).toList(), assignments);
    }

    public TeamDetailDto.Lineup get(CurrentUser user, UUID teamId) {
        Context ctx = context(user, teamId);
        LineRules.checkCanView(user.admin(), ctx.myTeamRole());
        return lineup(ctx.team(), ctx.season(), ctx.memberships());
    }

    /** New line; without a sort order it goes to the end (LIN-07, LIN-09). */
    @Transactional
    public GroupDto create(CurrentUser user, UUID teamId, LineInput input) {
        Context ctx = editable(user, teamId);
        List<Group> groups = Group.of(ctx.team(), ctx.season());
        String name = input.name().strip();
        LineRules.checkName(groups.stream().map(Group::info).toList(), name, null);
        List<Group> lines = lines(groups);
        Group line = Group.line(ctx.team(), ctx.season(), name, lines.size() + 1);
        line.persist();
        if (input.sortOrder() != null) {
            List<Group> withNew = new java.util.ArrayList<>(lines);
            withNew.add(line);
            order(withNew, LineRules.move(ids(withNew), line.id, input.sortOrder()));
        }
        // The same line created twice at the same time (double click): 409 instead of 500.
        Unique.save(em, NAME_TAKEN, () -> nameTaken(name));
        return GroupDto.of(line);
    }

    /** Renames a line and/or moves it to another position (LIN-07, LIN-09). */
    @Transactional
    public GroupDto update(CurrentUser user, UUID teamId, UUID groupId, LineInput input) {
        Context ctx = editable(user, teamId);
        List<Group> groups = Group.of(ctx.team(), ctx.season());
        Group group = find(groups, groupId);
        String name = input.name().strip();
        boolean rename = !name.equals(group.name);
        boolean move = input.sortOrder() != null && !input.sortOrder().equals(group.sortOrder);
        if (rename) {
            LineRules.checkIsLine(group.info(), "umbenennen");
        }
        if (move) {
            LineRules.checkIsLine(group.info(), "verschieben");
        }
        if (rename) {
            LineRules.checkName(groups.stream().map(Group::info).toList(), name, group.id);
            group.name = name;
        }
        if (move) {
            List<Group> lines = lines(groups);
            order(lines, LineRules.move(ids(lines), group.id, input.sortOrder()));
        }
        Unique.save(em, NAME_TAKEN, () -> nameTaken(name));
        return GroupDto.of(group);
    }

    /** Deletes a line. Its players then have no line (LIN-06). The goalies stay (LIN-05). */
    @Transactional
    public void delete(CurrentUser user, UUID teamId, UUID groupId) {
        Context ctx = editable(user, teamId);
        List<Group> groups = Group.of(ctx.team(), ctx.season());
        Group group = find(groups, groupId);
        LineRules.checkIsLine(group.info(), "löschen");
        for (TeamMembership membership : ctx.memberships()) {
            membership.groups.removeIf(g -> g.id.equals(group.id));
        }
        group.delete();
        List<Group> rest = lines(groups).stream().filter(g -> !g.id.equals(group.id)).toList();
        order(rest, ids(rest));
    }

    /**
     * Assigns a player to a group or (with {@code null}) sets them to "no line". An existing assignment
     * in the team is replaced (LIN-02). Only players of the team (LIN-10).
     */
    @Transactional
    public void assign(CurrentUser user, UUID teamId, UUID personId, UUID groupId) {
        Context ctx = editable(user, teamId);
        TeamMembership membership = ctx.memberships().stream().filter(m -> m.person.id.equals(personId)).findFirst()
                .orElseThrow(() -> DomainError.notFound("Diese Person ist nicht in diesem Team."));
        LineRules.checkAssignable(PersonSummary.of(membership.person), membership.teamRole);
        List<Group> groups = Group.of(ctx.team(), ctx.season());
        if (groupId != null) {
            find(groups, groupId);
        }
        Set<UUID> assigned = LineRules.assign(membership.groups.stream().map(g -> g.id).toList(), groupId);
        membership.groups.removeIf(g -> !assigned.contains(g.id));
        // Remove the old assignment first, then insert the new one (index group_assignment_one_group).
        em.flush();
        for (Group g : groups) {
            if (assigned.contains(g.id) && membership.groups.stream().noneMatch(x -> x.id.equals(g.id))) {
                membership.groups.add(g);
            }
        }
        // Two simultaneous assignments of the same player: 409 instead of 500 (LIN-02).
        Unique.save(em, List.of("group_assignment_one_group", "group_assignment_pkey"), () -> DomainError.conflict("Gleichzeitig geändert",
                "Die Linie wurde gerade gleichzeitig geändert. Bitte lade die Seite neu.", "LIN-02"));
    }

    /**
     * New season with carry-over (SAI-04): copies the lines with name and sort order and assigns the
     * players to their lines or to the goalies. The goalies of the target already exist
     * ({@link Team#addToSeason}).
     *
     * @param copies team membership in the source → its copy in the target
     */
    public static void carryOver(Season source, Season target, Map<TeamMembership, TeamMembership> copies) {
        Map<UUID, Group> copiedGroups = new HashMap<>();
        for (Group g : Group.<Group>list("season.id", source.id)) {
            Group copy;
            if (g.type == GroupType.GOALIES) {
                copy = Group.<Group>find("team.id = ?1 and season.id = ?2 and type = ?3", g.team.id, target.id,
                        GroupType.GOALIES).firstResult();
            } else {
                copy = Group.line(g.team, target, g.name, g.sortOrder);
                copy.persist();
            }
            copiedGroups.put(g.id, copy);
        }
        copies.forEach((original, copy) -> {
            if (copy.teamRole == TeamRole.PLAYER) {
                original.groups.stream().map(g -> copiedGroups.get(g.id)).filter(java.util.Objects::nonNull)
                        .forEach(copy.groups::add);
            }
        });
    }

    private record Context(Team team, Season season, List<TeamMembership> memberships, TeamRole myTeamRole) {
    }

    /** Team in the current season, with its memberships and the team role of the signed-in user. */
    private Context context(CurrentUser user, UUID teamId) {
        Season season = Season.currentOrFail();
        Team team = Team.<Team>findByIdOptional(teamId)
                .orElseThrow(() -> DomainError.notFound("Dieses Team gibt es nicht."));
        List<TeamMembership> memberships = TeamMembership.list("team.id = ?1 and season.id = ?2",
                team.id, season.id);
        TeamRole mine = memberships.stream().filter(m -> m.person.id.equals(user.personId()))
                .map(m -> m.teamRole).findFirst().orElse(null);
        if (!team.belongsTo(season)) {
            LineRules.checkCanView(user.admin(), mine);
            throw DomainError.notFound("Dieses Team gibt es in der Saison " + season.name + " nicht.");
        }
        return new Context(team, season, memberships, mine);
    }

    private Context editable(CurrentUser user, UUID teamId) {
        Context ctx = context(user, teamId);
        LineRules.checkCanEdit(user.admin(), ctx.myTeamRole());
        ctx.season().checkWritable();
        return ctx;
    }

    private static Group find(List<Group> groups, UUID id) {
        return groups.stream().filter(g -> g.id.equals(id)).findFirst()
                .orElseThrow(() -> DomainError.notFound("Diese Linie gibt es in diesem Team nicht."));
    }

    private static List<Group> lines(List<Group> groups) {
        return groups.stream().filter(g -> g.type == GroupType.LINE).toList();
    }

    private static List<UUID> ids(List<Group> groups) {
        return groups.stream().map(g -> g.id).toList();
    }

    /** Sets the sort order 1, 2, 3, … following the given order. */
    private static void order(List<Group> lines, List<UUID> order) {
        for (Group g : lines) {
            g.sortOrder = order.indexOf(g.id) + 1;
        }
    }

    private static DomainError nameTaken(String name) {
        return DomainError.conflict("Name vergeben", "In diesem Team gibt es schon eine Gruppe «" + name + "».", "LIN-09");
    }
}
