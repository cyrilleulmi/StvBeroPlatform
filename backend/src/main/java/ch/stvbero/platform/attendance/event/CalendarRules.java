package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.team.TeamRole;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Rules of the calendar view (KAL): period, teams shown by default and which teams someone may show. */
public final class CalendarRules {

    /** Six weeks: a month grid including the days of the neighbouring months. */
    static final int MAX_DAYS = 42;

    private CalendarRules() {
    }

    /** KAL-01: from and to are given, to is not before from, at most six weeks. */
    public static void checkPeriod(LocalDate from, LocalDate to) {
        if (from == null) {
            throw DomainError.invalidField("from", "Der erste Tag fehlt.");
        }
        if (to == null) {
            throw DomainError.invalidField("to", "Der letzte Tag fehlt.");
        }
        if (to.isBefore(from)) {
            throw DomainError.invalidField("to", "Der letzte Tag liegt vor dem ersten.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw DomainError.invalidField("to", "Der Kalender zeigt höchstens sechs Wochen auf einmal.");
        }
    }

    /**
     * KAL-03, KAL-04: teams shown when nothing is chosen. A coach sees the teams she coaches, everyone else all
     * own teams (a player in two teams sees both together). An admin without a team sees all teams.
     */
    public static List<UUID> defaults(Map<UUID, TeamRole> own, boolean coach, Collection<UUID> all) {
        if (coach) {
            List<UUID> coached = own.entrySet().stream().filter(e -> e.getValue() == TeamRole.COACH)
                    .map(Map.Entry::getKey).toList();
            if (!coached.isEmpty()) {
                return coached;
            }
        }
        if (!own.isEmpty()) {
            return List.copyOf(own.keySet());
        }
        return List.copyOf(all);
    }

    /**
     * KAL-02, KAL-04, RR-07: teams to show. «All» and teams of which the caller is not a member are for coaches and
     * admins only (403); an unknown team is 400. Nothing chosen: the defaults.
     */
    public static Set<UUID> selection(List<UUID> chosen, boolean all, boolean allTeams, Set<UUID> own,
                                      Collection<UUID> existing, List<UUID> defaults) {
        if (all) {
            if (!allTeams) {
                throw DomainError.forbidden();
            }
            return new LinkedHashSet<>(existing);
        }
        if (chosen == null || chosen.isEmpty()) {
            return new LinkedHashSet<>(defaults);
        }
        Set<UUID> selection = new LinkedHashSet<>();
        for (UUID id : chosen) {
            if (!allTeams && !own.contains(id)) {
                throw DomainError.forbidden();
            }
            if (!existing.contains(id)) {
                throw DomainError.invalidField("team", "Dieses Team gibt es nicht.");
            }
            selection.add(id);
        }
        return selection;
    }
}
