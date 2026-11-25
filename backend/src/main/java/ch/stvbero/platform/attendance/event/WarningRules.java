package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.line.LineRules;
import ch.stvbero.platform.core.team.GroupType;
import ch.stvbero.platform.core.team.WarningMinimums;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Domain rules for the warnings of events and call-ups (F-16). Without a database, so that unit tests can check
 * them. A warning applies per team, with the minimums of that team.
 */
public final class WarningRules {

    private WarningRules() {
    }

    /**
     * A player of the team with her group(s) (none: «Ohne Linie»). {@code counted}: responded «dabei» (overview)
     * or is selected (call-up).
     */
    public record Player(UUID personId, Collection<LineRules.GroupInfo> groups, boolean counted) {
    }

    /** Only coaches and admins see warnings (F-16, criterion 6); players get none, also not from the API. */
    public static boolean visible(boolean allTeams) {
        return allTeams;
    }

    /**
     * Warnings exist only for upcoming, not cancelled practices and match days (criterion 5). Other events (e.g.
     * «Teamessen») have no line-up and therefore no warnings (assumption).
     */
    public static boolean applies(EventType type, EventStatus status, Instant end, Instant now) {
        return type != EventType.OTHER && status == EventStatus.PLANNED && end.isAfter(now);
    }

    /** Minimum of field players for the event type: match days and practices have their own (criterion 4). */
    public static int fieldPlayerMinimum(EventType type, WarningMinimums minimums) {
        return type == EventType.MATCH_DAY ? minimums.fieldPlayersMatchDay() : minimums.fieldPlayersPractice();
    }

    /**
     * Warnings of one team, in this order: goalies, field players, lines by display order.
     * <ul>
     *   <li>Fewer goalies counted than the minimum (default 1: «Kein Goalie angemeldet»).</li>
     *   <li>Fewer field players counted than the minimum. Field players are all counted players who are not in
     *   the goalies, including «Ohne Linie».</li>
     *   <li>A line with at least one, but fewer counted players than the line minimum. A line nobody of comes is
     *   no gap within a line, but is already covered by the field players (assumption).</li>
     * </ul>
     * A minimum of 0 switches the warning off.
     */
    public static List<WarningDto> warnings(EventType type, OverviewDto.TeamName team, WarningMinimums minimums,
                                            Collection<Player> players) {
        Set<UUID> goalies = new HashSet<>();
        Set<UUID> fieldPlayers = new HashSet<>();
        Map<LineRules.GroupInfo, Set<UUID>> lines = new TreeMap<>(LineRules.DISPLAY);
        for (Player player : players) {
            if (!player.counted()) {
                continue;
            }
            boolean goalie = player.groups().stream().anyMatch(g -> g.type() == GroupType.GOALIES);
            (goalie ? goalies : fieldPlayers).add(player.personId());
            player.groups().stream().filter(g -> g.type() == GroupType.LINE)
                    .forEach(g -> lines.computeIfAbsent(g, k -> new HashSet<>()).add(player.personId()));
        }
        fieldPlayers.removeAll(goalies);
        List<WarningDto> warnings = new ArrayList<>();
        if (goalies.size() < minimums.goalies()) {
            warnings.add(new WarningDto(WarningType.GOALIES, team, null, null, goalies.size(), minimums.goalies()));
        }
        int fieldMinimum = fieldPlayerMinimum(type, minimums);
        if (fieldPlayers.size() < fieldMinimum) {
            warnings.add(new WarningDto(WarningType.FIELD_PLAYERS, team, null, null, fieldPlayers.size(), fieldMinimum));
        }
        lines.forEach((line, counted) -> {
            if (counted.size() < minimums.perLine()) {
                warnings.add(new WarningDto(WarningType.LINE, team, line.id(), line.name(), counted.size(),
                        minimums.perLine()));
            }
        });
        return warnings;
    }

    /**
     * Warnings about imported matches (SUH-12): {@code changed} matches that swiss unihockey changed and no coach
     * or admin confirmed yet, {@code missing} matches it no longer delivers. One warning per kind with the number
     * of matches as {@code count}; they belong to the match day, {@code team} is its first team.
     */
    public static List<WarningDto> suWarnings(OverviewDto.TeamName team, int changed, int missing) {
        List<WarningDto> warnings = new ArrayList<>();
        if (changed > 0) {
            warnings.add(new WarningDto(WarningType.MATCH_CHANGED, team, null, null, changed, 0));
        }
        if (missing > 0) {
            warnings.add(new WarningDto(WarningType.MATCH_MISSING, team, null, null, missing, 0));
        }
        return warnings;
    }
}
