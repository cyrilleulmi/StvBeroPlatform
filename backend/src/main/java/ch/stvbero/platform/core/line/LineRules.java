package ch.stvbero.platform.core.line;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.person.PersonSummary;
import ch.stvbero.platform.core.team.TeamRole;
import ch.stvbero.platform.core.team.GroupType;
import ch.stvbero.platform.core.team.TeamDetailDto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Domain rules for lines and goalies (LIN). Without a database, so that unit tests can check them. */
public final class LineRules {

    /**
     * At most this many groups per player and team (LIN-02). The limit is a rule, not a fixed structure
     * (LIN-04): for "several lines per player" only this number (and the index
     * {@code group_assignment_one_group}) has to change.
     */
    public static final int MAX_GROUPS_PER_TEAM = 1;

    /** A group without the database. */
    public record GroupInfo(UUID id, GroupType type, String name, Integer sortOrder) {
    }

    /** Display order: lines by sort order, then goalies (LIN-09, display order section). */
    public static final Comparator<GroupInfo> DISPLAY = Comparator
            .comparing((GroupInfo g) -> g.type() == GroupType.GOALIES ? 1 : 0)
            .thenComparing(g -> g.sortOrder() == null ? Integer.MAX_VALUE : g.sortOrder())
            .thenComparing(GroupInfo::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(GroupInfo::id);

    private LineRules() {
    }

    /** Managing lines and assigning players: coach of the team or admin (LIN-07, RR-03, RR-07). */
    public static void checkCanEdit(boolean admin, TeamRole teamRoleInTeam) {
        if (!admin && teamRoleInTeam != TeamRole.COACH) {
            throw DomainError.forbidden();
        }
    }

    /** Viewing the lineup: members of the team (any team role) and admins. */
    public static void checkCanView(boolean admin, TeamRole teamRoleInTeam) {
        if (!admin && teamRoleInTeam == null) {
            throw DomainError.forbidden();
        }
    }

    /** Only players of the team can be assigned to a line or to the goalies (LIN-10). */
    public static void checkAssignable(PersonSummary person, TeamRole teamRoleInTeam) {
        if (teamRoleInTeam != TeamRole.PLAYER) {
            throw DomainError.conflict("Keine Spielerin",
                    person.firstName() + " " + person.lastName()
                            + " ist in diesem Team keine Spielerin und kann keiner Linie zugewiesen werden.", "LIN-10");
        }
    }

    /** Every team has exactly one goalie group: it cannot be deleted, renamed or moved (LIN-05). */
    public static void checkIsLine(GroupInfo group, String action) {
        if (group.type() == GroupType.GOALIES) {
            throw DomainError.conflict("Goalies bleiben",
                    "Die Goalies gehören fest zu jedem Team und lassen sich nicht " + action + ".", "LIN-05");
        }
    }

    /** Names are unique per team, also compared with «Goalies» (assumption for LIN-09). */
    public static void checkName(Collection<GroupInfo> groups, String name, UUID except) {
        String stripped = name.strip();
        boolean taken = groups.stream()
                .anyMatch(g -> !g.id().equals(except) && g.name().strip().equalsIgnoreCase(stripped));
        if (taken) {
            throw DomainError.conflict("Name vergeben", "In diesem Team gibt es schon eine Gruppe «" + stripped + "».", "LIN-09");
        }
    }

    /**
     * New assignments of a player in a team. {@code target = null} means "no line".
     * If there are more than {@link #MAX_GROUPS_PER_TEAM}, the oldest ones are dropped: with one group
     * per team the new assignment replaces the previous one (LIN-02).
     */
    public static Set<UUID> assign(List<UUID> previous, UUID target) {
        if (target == null) {
            return Set.of();
        }
        List<UUID> assigned = new ArrayList<>(previous);
        assigned.remove(target);
        assigned.add(target);
        while (assigned.size() > MAX_GROUPS_PER_TEAM) {
            assigned.remove(0);
        }
        return new LinkedHashSet<>(assigned);
    }

    /**
     * Order after moving a line to position {@code target} (1 = top). Positions outside the valid range
     * are clamped to it. Result: line IDs, position = index + 1.
     */
    public static List<UUID> move(List<UUID> linesInSortOrder, UUID id, int target) {
        List<UUID> reordered = new ArrayList<>(linesInSortOrder);
        if (!reordered.remove(id)) {
            throw new IllegalArgumentException("Line not in the list: " + id);
        }
        int index = Math.max(0, Math.min(target - 1, reordered.size()));
        reordered.add(index, id);
        return reordered;
    }

    /**
     * Lineup of a team for display: all groups (empty ones too) in display order, within each the players
     * by last name, then first name. Players without a group are listed under «Ohne Linie» (LIN-06).
     *
     * @param assignments person ID → group IDs of this person in the team
     */
    public static TeamDetailDto.Lineup buildLineup(Collection<GroupInfo> groups,
                                                            Collection<PersonSummary> players,
                                                            Map<UUID, Set<UUID>> assignments) {
        List<GroupInfo> sorted = groups.stream().sorted(DISPLAY).toList();
        List<TeamDetailDto.GroupWithPlayers> result = new ArrayList<>();
        for (GroupInfo g : sorted) {
            List<PersonSummary> inGroup = players.stream()
                    .filter(p -> assignments.getOrDefault(p.id(), Set.of()).contains(g.id()))
                    .sorted(PersonSummary.ORDER).toList();
            result.add(new TeamDetailDto.GroupWithPlayers(g.id(), g.type(), g.name(), g.sortOrder(), inGroup));
        }
        Set<UUID> groupIds = sorted.stream().map(GroupInfo::id).collect(java.util.stream.Collectors.toSet());
        List<PersonSummary> noLine = players.stream()
                .filter(p -> assignments.getOrDefault(p.id(), Set.of()).stream().noneMatch(groupIds::contains))
                .sorted(PersonSummary.ORDER).toList();
        return new TeamDetailDto.Lineup(result, noLine);
    }
}
