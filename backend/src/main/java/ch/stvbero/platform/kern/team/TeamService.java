package ch.stvbero.platform.kern.team;

import ch.stvbero.platform.kern.anmeldung.Angemeldet;
import ch.stvbero.platform.kern.fehler.Eindeutig;
import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.linie.LinienService;
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

    private static final Comparator<TeamDto> NACH_NAME = Comparator
            .comparing(TeamDto::name, String.CASE_INSENSITIVE_ORDER).thenComparing(TeamDto::id);

    /** Teamzugehörigkeiten nach Teamname in ihrer Saison (SAI-02). */
    static final Comparator<TeamzugehoerigkeitDto> NACH_TEAMNAME = Comparator
            .comparing((TeamzugehoerigkeitDto z) -> z.team().name(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(z -> z.team().id());

    /** Teams einer Saison. Admins sehen alle, sonst nur die eigenen (PTF-12). */
    public List<TeamDto> liste(Angemeldet wer, UUID saisonId) {
        Saison saison = Saison.oderAktuelle(saisonId);
        Map<UUID, Funktion> meine = Teamzugehoerigkeit.<Teamzugehoerigkeit>list("person.id = ?1 and saison.id = ?2",
                        wer.personId(), saison.id).stream()
                .collect(Collectors.toMap(z -> z.team.id, z -> z.funktion));
        List<TeamSaison> teams = TeamSaison.list("saison.id", saison.id);
        return teams.stream()
                .filter(ts -> wer.admin() || meine.containsKey(ts.team.id))
                .map(ts -> TeamDto.von(ts, meine.get(ts.team.id)))
                .sorted(NACH_NAME)
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
        // Spielerinnen nach Linie, dann Goalies, dann «Ohne Linie» (LIN-06, LIN-09)
        return new TeamDetailDto(team.id, team.nameIn(saison), team.suTeamId, meine.orElse(null), aufteilung.coaches(),
                LinienService.einteilung(team, saison, zugehoerigkeiten));
    }

    @Transactional
    public TeamDto anlegen(Angemeldet wer, UUID saisonId, TeamSchreiben eingabe) {
        wer.pruefeAdmin();
        Saison saison = Saison.oderAktuelle(saisonId);
        saison.pruefeBeschreibbar();
        Team team = new Team();
        team.id = UUID.randomUUID();
        team.persist();
        team.aufnehmenIn(saison, eingabe.name().strip());
        return TeamDto.von(team.in(saison).orElseThrow(), null);
    }

    /**
     * Team umbenennen. Der Name gilt nur in dieser Saison (Standard: aktuelle). Archivierte Saisons
     * bleiben unverändert (409, SAI-02).
     */
    @Transactional
    public TeamDto aendern(Angemeldet wer, UUID teamId, UUID saisonId, TeamSchreiben eingabe) {
        wer.pruefeAdmin();
        Team team = finden(teamId);
        Saison saison = Saison.oderAktuelle(saisonId);
        TeamSaison ts = team.in(saison).orElseThrow(
                () -> FachFehler.nichtGefunden("Dieses Team gibt es in der Saison " + saison.name + " nicht."));
        saison.pruefeBeschreibbar();
        ts.name = eingabe.name().strip();
        return TeamDto.von(ts, null);
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
        // Kommt das Team neu in die Saison, übernimmt es den Namen aus der letzten Saison, in der es bestand.
        team.aufnehmenIn(saison, letzterName(team));
        Teamzugehoerigkeit z = Teamzugehoerigkeit.neu(person, team, saison, eingabe.funktion());
        z.persist();
        // Gleichzeitige Aufnahme derselben Person (Doppelklick): 409 statt 500 (PTF-04).
        Eindeutig.speichern(Team.getEntityManager(), "teamzugehoerigkeit_eine_funktion",
                () -> FachFehler.konflikt("Schon im Team", person.vorname + " " + person.nachname
                        + " ist schon in diesem Team. Pro Team gibt es nur eine Funktion.", "PTF-04"));
        return TeamzugehoerigkeitDto.von(z);
    }

    @Transactional
    public TeamzugehoerigkeitDto funktionAendern(Angemeldet wer, UUID teamId, UUID personId, UUID saisonId,
                                                 Funktion funktion) {
        wer.pruefeAdmin();
        Teamzugehoerigkeit z = zugehoerigkeit(teamId, personId, saisonId);
        z.saison.pruefeBeschreibbar();
        z.funktion = funktion;
        // Nur Spielerinnen sind in einer Linie (LIN-10): Beim Wechsel zur Coach entfällt die Zuweisung.
        if (funktion != Funktion.SPIELERIN) {
            z.gruppen.clear();
        }
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
        return Teamzugehoerigkeit.<Teamzugehoerigkeit>list("person.id = ?1 and saison.id = ?2", personId, saison.id)
                .stream().map(TeamzugehoerigkeitDto::von).sorted(NACH_TEAMNAME).toList();
    }

    private Teamzugehoerigkeit zugehoerigkeit(UUID teamId, UUID personId, UUID saisonId) {
        Team team = finden(teamId);
        Saison saison = Saison.oderAktuelle(saisonId);
        return Teamzugehoerigkeit.<Teamzugehoerigkeit>find("team.id = ?1 and person.id = ?2 and saison.id = ?3",
                        team.id, personId, saison.id).firstResultOptional()
                .orElseThrow(() -> FachFehler.nichtGefunden("Diese Person ist in dieser Saison nicht im Team."));
    }

    private static String letzterName(Team team) {
        return team.saisons.stream().max(Comparator.comparing((TeamSaison ts) -> ts.saison.von))
                .map(ts -> ts.name).orElseThrow();
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
