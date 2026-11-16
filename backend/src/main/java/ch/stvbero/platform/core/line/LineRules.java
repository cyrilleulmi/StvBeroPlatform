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

/** Fachregeln zu Linien und Goalies (LIN). Ohne Datenbank, damit sie als Unit-Test prüfbar sind. */
public final class LineRules {

    /**
     * Höchstens so viele Gruppen pro Spielerin und Team (LIN-02). Die Begrenzung ist eine Regel, keine
     * feste Struktur (LIN-04): Für «mehrere Linien pro Spielerin» wird nur diese Zahl (und der Index
     * {@code gruppenzuweisung_eine_gruppe}) angepasst.
     */
    public static final int MAX_GROUPS_PER_TEAM = 1;

    /** Eine Gruppe ohne Datenbank. */
    public record GroupInfo(UUID id, GroupType type, String name, Integer sortOrder) {
    }

    /** Anzeigereihenfolge: Linien nach Reihenfolge, dann Goalies (LIN-09, Abschnitt «Anzeige-Reihenfolge»). */
    public static final Comparator<GroupInfo> DISPLAY = Comparator
            .comparing((GroupInfo g) -> g.type() == GroupType.GOALIES ? 1 : 0)
            .thenComparing(g -> g.sortOrder() == null ? Integer.MAX_VALUE : g.sortOrder())
            .thenComparing(GroupInfo::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(GroupInfo::id);

    private LineRules() {
    }

    /** Linien verwalten und Spielerinnen zuweisen: Coach des Teams oder Admin (LIN-07, RR-03, RR-07). */
    public static void checkCanEdit(boolean admin, TeamRole teamRoleInTeam) {
        if (!admin && teamRoleInTeam != TeamRole.COACH) {
            throw DomainError.forbidden();
        }
    }

    /** Einteilung ansehen: Mitglieder des Teams (jede Funktion) und Admins. */
    public static void checkCanView(boolean admin, TeamRole teamRoleInTeam) {
        if (!admin && teamRoleInTeam == null) {
            throw DomainError.forbidden();
        }
    }

    /** Nur Spielerinnen des Teams können einer Linie oder den Goalies zugewiesen werden (LIN-10). */
    public static void checkAssignable(PersonSummary person, TeamRole teamRoleInTeam) {
        if (teamRoleInTeam != TeamRole.PLAYER) {
            throw DomainError.conflict("Keine Spielerin",
                    person.firstName() + " " + person.lastName()
                            + " ist in diesem Team keine Spielerin und kann keiner Linie zugewiesen werden.", "LIN-10");
        }
    }

    /** Goalies gibt es pro Team genau einmal: nicht löschen, nicht umbenennen, nicht verschieben (LIN-05). */
    public static void checkIsLine(GroupInfo group, String action) {
        if (group.type() == GroupType.GOALIES) {
            throw DomainError.conflict("Goalies bleiben",
                    "Die Goalies gehören fest zu jedem Team und lassen sich nicht " + action + ".", "LIN-05");
        }
    }

    /** Namen sind pro Team eindeutig, auch gegenüber «Goalies» (Annahme zu LIN-09). */
    public static void checkName(Collection<GroupInfo> groups, String name, UUID except) {
        String n = name.strip();
        boolean taken = groups.stream()
                .anyMatch(g -> !g.id().equals(except) && g.name().strip().equalsIgnoreCase(n));
        if (taken) {
            throw DomainError.conflict("Name vergeben", "In diesem Team gibt es schon eine Gruppe «" + n + "».", "LIN-09");
        }
    }

    /**
     * Neue Zuweisungen einer Spielerin in einem Team. {@code ziel = null} heisst «ohne Linie».
     * Sind es mehr als {@link #HOECHSTENS_GRUPPEN_PRO_TEAM}, fallen die ältesten weg: Bei einer Gruppe
     * pro Team ersetzt die neue Zuweisung die bisherige (LIN-02).
     */
    public static Set<UUID> assign(List<UUID> previous, UUID target) {
        if (target == null) {
            return Set.of();
        }
        List<UUID> newValue = new ArrayList<>(previous);
        newValue.remove(target);
        newValue.add(target);
        while (newValue.size() > MAX_GROUPS_PER_TEAM) {
            newValue.remove(0);
        }
        return new LinkedHashSet<>(newValue);
    }

    /**
     * Reihenfolge nach dem Verschieben einer Linie an Position {@code ziel} (1 = zuoberst). Positionen
     * ausserhalb werden auf den gültigen Bereich begrenzt. Ergebnis: Linien-IDs, Position = Index + 1.
     */
    public static List<UUID> move(List<UUID> linesInSortOrder, UUID id, int target) {
        List<UUID> newValue = new ArrayList<>(linesInSortOrder);
        if (!newValue.remove(id)) {
            throw new IllegalArgumentException("Linie nicht in der Liste: " + id);
        }
        int index = Math.max(0, Math.min(target - 1, newValue.size()));
        newValue.add(index, id);
        return newValue;
    }

    /**
     * Einteilung eines Teams für die Anzeige: alle Gruppen (auch leere) in Anzeigereihenfolge, darin die
     * Spielerinnen nach Nachname, dann Vorname. Spielerinnen ohne Gruppe stehen unter «Ohne Linie» (LIN-06).
     *
     * @param zuweisungen Person-ID → Gruppen-IDs dieser Person im Team
     */
    public static TeamDetailDto.Lineup buildLineup(Collection<GroupInfo> groups,
                                                            Collection<PersonSummary> players,
                                                            Map<UUID, Set<UUID>> assignments) {
        List<GroupInfo> sorted = groups.stream().sorted(DISPLAY).toList();
        List<TeamDetailDto.GroupWithPlayers> result = new ArrayList<>();
        for (GroupInfo g : sorted) {
            List<PersonSummary> inIt = players.stream()
                    .filter(p -> assignments.getOrDefault(p.id(), Set.of()).contains(g.id()))
                    .sorted(PersonSummary.ORDER).toList();
            result.add(new TeamDetailDto.GroupWithPlayers(g.id(), g.type(), g.name(), g.sortOrder(), inIt));
        }
        Set<UUID> groupsIds = sorted.stream().map(GroupInfo::id).collect(java.util.stream.Collectors.toSet());
        List<PersonSummary> noLine = players.stream()
                .filter(p -> assignments.getOrDefault(p.id(), Set.of()).stream().noneMatch(groupsIds::contains))
                .sorted(PersonSummary.ORDER).toList();
        return new TeamDetailDto.Lineup(result, noLine);
    }
}
