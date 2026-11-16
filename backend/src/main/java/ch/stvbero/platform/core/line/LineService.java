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
 * Linien und Goalies eines Teams in der aktuellen Saison (LIN). Verwalten dürfen die Coaches des Teams
 * und Admins (LIN-07, RR-03), ansehen alle Mitglieder des Teams.
 */
@ApplicationScoped
public class LineService {

    private static final String NAME_TAKEN = "line_group_name_unique";

    @Inject
    EntityManager em;

    /** Einteilung eines Teams in einer Saison: Gruppen in Anzeigereihenfolge, dann «Ohne Linie». */
    public static TeamDetailDto.Lineup lineup(Team team, Season season,
                                                             List<TeamMembership> memberships) {
        List<TeamMembership> players = memberships.stream()
                .filter(z -> z.teamRole == TeamRole.PLAYER).toList();
        Map<UUID, Set<UUID>> assignments = new HashMap<>();
        for (TeamMembership z : players) {
            assignments.put(z.person.id, z.groups.stream().map(g -> g.id).collect(Collectors.toSet()));
        }
        return LineRules.buildLineup(Group.of(team, season).stream().map(Group::info).toList(),
                players.stream().map(z -> PersonSummary.of(z.person)).toList(), assignments);
    }

    public TeamDetailDto.Lineup get(CurrentUser user, UUID teamId) {
        Context k = context(user, teamId);
        LineRules.checkCanView(user.admin(), k.myTeamRole());
        return lineup(k.team(), k.season(), k.memberships());
    }

    /** Neue Linie, ohne Angabe ans Ende (LIN-07, LIN-09). */
    @Transactional
    public GroupDto create(CurrentUser user, UUID teamId, LineInput input) {
        Context k = editable(user, teamId);
        List<Group> groups = Group.of(k.team(), k.season());
        String name = input.name().strip();
        LineRules.checkName(groups.stream().map(Group::info).toList(), name, null);
        List<Group> lines = lines(groups);
        Group newValue = Group.line(k.team(), k.season(), name, lines.size() + 1);
        newValue.persist();
        if (input.sortOrder() != null) {
            List<Group> withNew = new java.util.ArrayList<>(lines);
            withNew.add(newValue);
            order(withNew, LineRules.move(ids(withNew), newValue.id, input.sortOrder()));
        }
        // Gleichzeitiges Anlegen derselben Linie (Doppelklick): 409 statt 500.
        Unique.save(em, NAME_TAKEN, () -> nameTaken(name));
        return GroupDto.of(newValue);
    }

    /** Linie umbenennen und/oder an eine andere Position schieben (LIN-07, LIN-09). */
    @Transactional
    public GroupDto update(CurrentUser user, UUID teamId, UUID groupId, LineInput input) {
        Context k = editable(user, teamId);
        List<Group> groups = Group.of(k.team(), k.season());
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

    /** Linie löschen. Ihre Spielerinnen sind danach «ohne Linie» (LIN-06). Goalies bleiben (LIN-05). */
    @Transactional
    public void delete(CurrentUser user, UUID teamId, UUID groupId) {
        Context k = editable(user, teamId);
        List<Group> groups = Group.of(k.team(), k.season());
        Group group = find(groups, groupId);
        LineRules.checkIsLine(group.info(), "löschen");
        for (TeamMembership z : k.memberships()) {
            z.groups.removeIf(g -> g.id.equals(group.id));
        }
        group.delete();
        List<Group> rest = lines(groups).stream().filter(g -> !g.id.equals(group.id)).toList();
        order(rest, ids(rest));
    }

    /**
     * Spielerin einer Gruppe zuweisen oder (mit {@code null}) auf «ohne Linie» setzen. Eine bestehende
     * Zuweisung im Team wird ersetzt (LIN-02). Nur Spielerinnen des Teams (LIN-10).
     */
    @Transactional
    public void assign(CurrentUser user, UUID teamId, UUID personId, UUID groupId) {
        Context k = editable(user, teamId);
        TeamMembership z = k.memberships().stream().filter(t -> t.person.id.equals(personId)).findFirst()
                .orElseThrow(() -> DomainError.notFound("Diese Person ist nicht in diesem Team."));
        LineRules.checkAssignable(PersonSummary.of(z.person), z.teamRole);
        List<Group> groups = Group.of(k.team(), k.season());
        if (groupId != null) {
            find(groups, groupId);
        }
        Set<UUID> newValue = LineRules.assign(z.groups.stream().map(g -> g.id).toList(), groupId);
        z.groups.removeIf(g -> !newValue.contains(g.id));
        // Erst die alte Zuweisung entfernen, dann die neue einfügen (Index gruppenzuweisung_eine_gruppe).
        em.flush();
        for (Group g : groups) {
            if (newValue.contains(g.id) && z.groups.stream().noneMatch(x -> x.id.equals(g.id))) {
                z.groups.add(g);
            }
        }
        // Zwei gleichzeitige Zuweisungen derselben Spielerin: 409 statt 500 (LIN-02).
        Unique.save(em, List.of("group_assignment_one_group", "group_assignment_pkey"), () -> DomainError.conflict("Gleichzeitig geändert",
                "Die Linie wurde gerade gleichzeitig geändert. Bitte lade die Seite neu.", "LIN-02"));
    }

    /**
     * Neue Saison mit Übernahme (SAI-04): Linien mit Namen und Reihenfolge kopieren und die Spielerinnen
     * ihren Linien bzw. den Goalies zuweisen. Die Goalies des Ziels bestehen schon ({@code Team.aufnehmenIn}).
     *
     * @param kopien Teamzugehörigkeit der Quelle → ihre Kopie im Ziel
     */
    public static void apply(Season source, Season target, Map<TeamMembership, TeamMembership> copies) {
        Map<UUID, Group> newGroup = new HashMap<>();
        for (Group g : Group.<Group>list("season.id", source.id)) {
            Group newValue;
            if (g.type == GroupType.GOALIES) {
                newValue = Group.<Group>find("team.id = ?1 and season.id = ?2 and type = ?3", g.team.id, target.id,
                        GroupType.GOALIES).firstResult();
            } else {
                newValue = Group.line(g.team, target, g.name, g.sortOrder);
                newValue.persist();
            }
            newGroup.put(g.id, newValue);
        }
        copies.forEach((old, copy) -> {
            if (copy.teamRole == TeamRole.PLAYER) {
                old.groups.stream().map(g -> newGroup.get(g.id)).filter(java.util.Objects::nonNull)
                        .forEach(copy.groups::add);
            }
        });
    }

    private record Context(Team team, Season season, List<TeamMembership> memberships, TeamRole myTeamRole) {
    }

    /** Team in der aktuellen Saison, mit Zugehörigkeiten und Funktion der angemeldeten Person. */
    private Context context(CurrentUser user, UUID teamId) {
        Season season = Season.currentOrFail();
        Team team = Team.<Team>findByIdOptional(teamId)
                .orElseThrow(() -> DomainError.notFound("Dieses Team gibt es nicht."));
        List<TeamMembership> memberships = TeamMembership.list("team.id = ?1 and season.id = ?2",
                team.id, season.id);
        TeamRole mine = memberships.stream().filter(z -> z.person.id.equals(user.personId()))
                .map(z -> z.teamRole).findFirst().orElse(null);
        if (!team.belongsTo(season)) {
            LineRules.checkCanView(user.admin(), mine);
            throw DomainError.notFound("Dieses Team gibt es in der Saison " + season.name + " nicht.");
        }
        return new Context(team, season, memberships, mine);
    }

    private Context editable(CurrentUser user, UUID teamId) {
        Context k = context(user, teamId);
        LineRules.checkCanEdit(user.admin(), k.myTeamRole());
        k.season().checkWritable();
        return k;
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

    /** Reihenfolge 1, 2, 3, … nach der angegebenen Ordnung. */
    private static void order(List<Group> lines, List<UUID> order) {
        for (Group g : lines) {
            g.sortOrder = order.indexOf(g.id) + 1;
        }
    }

    private static DomainError nameTaken(String name) {
        return DomainError.conflict("Name vergeben", "In diesem Team gibt es schon eine Gruppe «" + name + "».", "LIN-09");
    }
}
