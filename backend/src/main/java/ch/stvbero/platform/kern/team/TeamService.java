package ch.stvbero.platform.kern.team;

import ch.stvbero.platform.kern.anmeldung.Angemeldet;
import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.person.Person;
import ch.stvbero.platform.kern.person.PersonKurz;
import ch.stvbero.platform.kern.person.PersonService;
import ch.stvbero.platform.kern.saison.Saison;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Teams und Teamzugehörigkeiten (PTF). Verwalten dürfen nur Admins (RR-04, PTF-11). */
@ApplicationScoped
public class TeamService {

    private static final Comparator<Team> NACH_NAME = Comparator
            .comparing((Team t) -> t.name, String.CASE_INSENSITIVE_ORDER).thenComparing(t -> t.id);

    /** Teams einer Saison. Admins sehen alle, sonst nur die eigenen (PTF-12). */
    public List<TeamDto> liste(Angemeldet wer, UUID saisonId) {
        Saison saison = Saison.oderAktuelle(saisonId);
        Map<UUID, Funktion> meine = Teamzugehoerigkeit.<Teamzugehoerigkeit>list("person.id = ?1 and saison.id = ?2",
                        wer.personId(), saison.id).stream()
                .collect(Collectors.toMap(z -> z.team.id, z -> z.funktion));
        List<Team> teams = Team.list("select t from Team t join t.saisons s where s.id = ?1", saison.id);
        return teams.stream()
                .filter(t -> wer.admin() || meine.containsKey(t.id))
                .sorted(NACH_NAME)
                .map(t -> TeamDto.von(t, meine.get(t.id)))
                .toList();
    }

    /** Team mit Coaches und Spielerinnen. Nur Mitglieder des Teams in dieser Saison und Admins. */
    public TeamDetailDto lesen(Angemeldet wer, UUID teamId, UUID saisonId) {
        Team team = finden(teamId);
        Saison saison = Saison.oderAktuelle(saisonId);
        List<Teamzugehoerigkeit> zugehoerigkeiten = imTeam(team, saison);
        Optional<Funktion> meine = zugehoerigkeiten.stream()
                .filter(z -> z.person.id.equals(wer.personId())).map(z -> z.funktion).findFirst();
        if (!wer.admin() && meine.isEmpty()) {
            throw FachFehler.keineBerechtigung();
        }
        if (!team.gehoertZu(saison)) {
            throw FachFehler.nichtGefunden("Dieses Team gibt es in der Saison " + saison.name + " nicht.");
        }
        TeamRegeln.Aufteilung aufteilung = TeamRegeln.aufteilen(mitglieder(zugehoerigkeiten));
        // Linien und Goalies kommen mit F-02. Bis dahin stehen alle Spielerinnen unter «ohne Linie».
        return new TeamDetailDto(team.id, team.name, team.suTeamId, meine.orElse(null), aufteilung.coaches(),
                new TeamDetailDto.Gruppeneinteilung(List.of(), aufteilung.spielerinnen()));
    }

    @Transactional
    public TeamDto anlegen(Angemeldet wer, UUID saisonId, TeamSchreiben eingabe) {
        wer.pruefeAdmin();
        Saison saison = Saison.oderAktuelle(saisonId);
        saison.pruefeBeschreibbar();
        Team team = new Team();
        team.id = UUID.randomUUID();
        team.name = eingabe.name().strip();
        team.saisons.add(saison);
        team.persist();
        return TeamDto.von(team, null);
    }

    @Transactional
    public TeamDto aendern(Angemeldet wer, UUID teamId, TeamSchreiben eingabe) {
        wer.pruefeAdmin();
        Team team = finden(teamId);
        team.name = eingabe.name().strip();
        return TeamDto.von(team, null);
    }

    /** Person mit Funktion ins Team aufnehmen. Pro Team und Saison genau eine Funktion (PTF-04). */
    @Transactional
    public TeamzugehoerigkeitDto aufnehmen(Angemeldet wer, UUID teamId, UUID saisonId, MitgliedSchreiben eingabe) {
        wer.pruefeAdmin();
        Team team = finden(teamId);
        Saison saison = Saison.oderAktuelle(saisonId);
        saison.pruefeBeschreibbar();
        Person person = PersonService.finden(eingabe.personId());
        TeamRegeln.pruefeAufnahme(mitglieder(imTeam(team, saison)), person.id);
        if (!team.gehoertZu(saison)) {
            team.saisons.add(saison);
        }
        Teamzugehoerigkeit z = Teamzugehoerigkeit.neu(person, team, saison, eingabe.funktion());
        z.persist();
        return TeamzugehoerigkeitDto.von(z);
    }

    @Transactional
    public TeamzugehoerigkeitDto funktionAendern(Angemeldet wer, UUID teamId, UUID personId, UUID saisonId,
                                                 Funktion funktion) {
        wer.pruefeAdmin();
        Teamzugehoerigkeit z = zugehoerigkeit(teamId, personId, saisonId);
        z.saison.pruefeBeschreibbar();
        // Linienzuweisung entfällt beim Wechsel zur Coach, sobald es Linien gibt (F-02).
        z.funktion = funktion;
        return TeamzugehoerigkeitDto.von(z);
    }

    @Transactional
    public void entfernen(Angemeldet wer, UUID teamId, UUID personId, UUID saisonId) {
        wer.pruefeAdmin();
        Teamzugehoerigkeit z = zugehoerigkeit(teamId, personId, saisonId);
        z.saison.pruefeBeschreibbar();
        z.delete();
    }

    /** Teamzugehörigkeiten der angemeldeten Person in der aktuellen Saison, nach Teamname (PTF-12). */
    public List<TeamzugehoerigkeitDto> meine(UUID personId, Saison saison) {
        return Teamzugehoerigkeit.<Teamzugehoerigkeit>list("person.id = ?1 and saison.id = ?2 order by team.name",
                personId, saison.id).stream().map(TeamzugehoerigkeitDto::von).toList();
    }

    private Teamzugehoerigkeit zugehoerigkeit(UUID teamId, UUID personId, UUID saisonId) {
        Team team = finden(teamId);
        Saison saison = Saison.oderAktuelle(saisonId);
        return Teamzugehoerigkeit.<Teamzugehoerigkeit>find("team.id = ?1 and person.id = ?2 and saison.id = ?3",
                        team.id, personId, saison.id).firstResultOptional()
                .orElseThrow(() -> FachFehler.nichtGefunden("Diese Person ist in dieser Saison nicht im Team."));
    }

    private static List<Teamzugehoerigkeit> imTeam(Team team, Saison saison) {
        return Teamzugehoerigkeit.list("team.id = ?1 and saison.id = ?2", team.id, saison.id);
    }

    private static List<TeamRegeln.Mitglied> mitglieder(List<Teamzugehoerigkeit> zugehoerigkeiten) {
        return zugehoerigkeiten.stream().map(z -> new TeamRegeln.Mitglied(PersonKurz.von(z.person), z.funktion)).toList();
    }

    private static Team finden(UUID id) {
        return Team.<Team>findByIdOptional(id)
                .orElseThrow(() -> FachFehler.nichtGefunden("Dieses Team gibt es nicht."));
    }
}
