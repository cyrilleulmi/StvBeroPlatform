package ch.stvbero.platform.kern.linie;

import ch.stvbero.platform.kern.anmeldung.Angemeldet;
import ch.stvbero.platform.kern.fehler.Eindeutig;
import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.person.PersonKurz;
import ch.stvbero.platform.kern.saison.Saison;
import ch.stvbero.platform.kern.team.Funktion;
import ch.stvbero.platform.kern.team.GruppenArt;
import ch.stvbero.platform.kern.team.Team;
import ch.stvbero.platform.kern.team.TeamDetailDto;
import ch.stvbero.platform.kern.team.Teamzugehoerigkeit;
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
public class LinienService {

    private static final String NAME_VERGEBEN = "gruppe_name_eindeutig";

    @Inject
    EntityManager em;

    /** Einteilung eines Teams in einer Saison: Gruppen in Anzeigereihenfolge, dann «Ohne Linie». */
    public static TeamDetailDto.Gruppeneinteilung einteilung(Team team, Saison saison,
                                                             List<Teamzugehoerigkeit> zugehoerigkeiten) {
        List<Teamzugehoerigkeit> spielerinnen = zugehoerigkeiten.stream()
                .filter(z -> z.funktion == Funktion.SPIELERIN).toList();
        Map<UUID, Set<UUID>> zuweisungen = new HashMap<>();
        for (Teamzugehoerigkeit z : spielerinnen) {
            zuweisungen.put(z.person.id, z.gruppen.stream().map(g -> g.id).collect(Collectors.toSet()));
        }
        return LinienRegeln.einteilen(Gruppe.von(team, saison).stream().map(Gruppe::info).toList(),
                spielerinnen.stream().map(z -> PersonKurz.von(z.person)).toList(), zuweisungen);
    }

    public TeamDetailDto.Gruppeneinteilung lesen(Angemeldet wer, UUID teamId) {
        Kontext k = kontext(wer, teamId);
        LinienRegeln.pruefeDarfLesen(wer.admin(), k.meineFunktion());
        return einteilung(k.team(), k.saison(), k.zugehoerigkeiten());
    }

    /** Neue Linie, ohne Angabe ans Ende (LIN-07, LIN-09). */
    @Transactional
    public GruppeDto anlegen(Angemeldet wer, UUID teamId, LinieSchreiben eingabe) {
        Kontext k = bearbeitbar(wer, teamId);
        List<Gruppe> gruppen = Gruppe.von(k.team(), k.saison());
        String name = eingabe.name().strip();
        LinienRegeln.pruefeName(gruppen.stream().map(Gruppe::info).toList(), name, null);
        List<Gruppe> linien = linien(gruppen);
        Gruppe neu = Gruppe.linie(k.team(), k.saison(), name, linien.size() + 1);
        neu.persist();
        if (eingabe.reihenfolge() != null) {
            List<Gruppe> mitNeuer = new java.util.ArrayList<>(linien);
            mitNeuer.add(neu);
            ordnen(mitNeuer, LinienRegeln.verschieben(ids(mitNeuer), neu.id, eingabe.reihenfolge()));
        }
        // Gleichzeitiges Anlegen derselben Linie (Doppelklick): 409 statt 500.
        Eindeutig.speichern(em, NAME_VERGEBEN, () -> nameVergeben(name));
        return GruppeDto.von(neu);
    }

    /** Linie umbenennen und/oder an eine andere Position schieben (LIN-07, LIN-09). */
    @Transactional
    public GruppeDto aendern(Angemeldet wer, UUID teamId, UUID gruppeId, LinieSchreiben eingabe) {
        Kontext k = bearbeitbar(wer, teamId);
        List<Gruppe> gruppen = Gruppe.von(k.team(), k.saison());
        Gruppe gruppe = finden(gruppen, gruppeId);
        String name = eingabe.name().strip();
        boolean umbenennen = !name.equals(gruppe.name);
        boolean verschieben = eingabe.reihenfolge() != null && !eingabe.reihenfolge().equals(gruppe.reihenfolge);
        if (umbenennen) {
            LinienRegeln.pruefeLinie(gruppe.info(), "umbenennen");
        }
        if (verschieben) {
            LinienRegeln.pruefeLinie(gruppe.info(), "verschieben");
        }
        if (umbenennen) {
            LinienRegeln.pruefeName(gruppen.stream().map(Gruppe::info).toList(), name, gruppe.id);
            gruppe.name = name;
        }
        if (verschieben) {
            List<Gruppe> linien = linien(gruppen);
            ordnen(linien, LinienRegeln.verschieben(ids(linien), gruppe.id, eingabe.reihenfolge()));
        }
        Eindeutig.speichern(em, NAME_VERGEBEN, () -> nameVergeben(name));
        return GruppeDto.von(gruppe);
    }

    /** Linie löschen. Ihre Spielerinnen sind danach «ohne Linie» (LIN-06). Goalies bleiben (LIN-05). */
    @Transactional
    public void loeschen(Angemeldet wer, UUID teamId, UUID gruppeId) {
        Kontext k = bearbeitbar(wer, teamId);
        List<Gruppe> gruppen = Gruppe.von(k.team(), k.saison());
        Gruppe gruppe = finden(gruppen, gruppeId);
        LinienRegeln.pruefeLinie(gruppe.info(), "löschen");
        for (Teamzugehoerigkeit z : k.zugehoerigkeiten()) {
            z.gruppen.removeIf(g -> g.id.equals(gruppe.id));
        }
        gruppe.delete();
        List<Gruppe> rest = linien(gruppen).stream().filter(g -> !g.id.equals(gruppe.id)).toList();
        ordnen(rest, ids(rest));
    }

    /**
     * Spielerin einer Gruppe zuweisen oder (mit {@code null}) auf «ohne Linie» setzen. Eine bestehende
     * Zuweisung im Team wird ersetzt (LIN-02). Nur Spielerinnen des Teams (LIN-10).
     */
    @Transactional
    public void zuweisen(Angemeldet wer, UUID teamId, UUID personId, UUID gruppeId) {
        Kontext k = bearbeitbar(wer, teamId);
        Teamzugehoerigkeit z = k.zugehoerigkeiten().stream().filter(t -> t.person.id.equals(personId)).findFirst()
                .orElseThrow(() -> FachFehler.nichtGefunden("Diese Person ist nicht in diesem Team."));
        LinienRegeln.pruefeZuweisbar(PersonKurz.von(z.person), z.funktion);
        List<Gruppe> gruppen = Gruppe.von(k.team(), k.saison());
        if (gruppeId != null) {
            finden(gruppen, gruppeId);
        }
        Set<UUID> neu = LinienRegeln.zuweisen(z.gruppen.stream().map(g -> g.id).toList(), gruppeId);
        z.gruppen.removeIf(g -> !neu.contains(g.id));
        // Erst die alte Zuweisung entfernen, dann die neue einfügen (Index gruppenzuweisung_eine_gruppe).
        em.flush();
        for (Gruppe g : gruppen) {
            if (neu.contains(g.id) && z.gruppen.stream().noneMatch(x -> x.id.equals(g.id))) {
                z.gruppen.add(g);
            }
        }
        // Zwei gleichzeitige Zuweisungen derselben Spielerin: 409 statt 500 (LIN-02).
        Eindeutig.speichern(em, List.of("gruppenzuweisung_eine_gruppe", "gruppenzuweisung_pkey"), () -> FachFehler.konflikt("Gleichzeitig geändert",
                "Die Linie wurde gerade gleichzeitig geändert. Bitte lade die Seite neu.", "LIN-02"));
    }

    /**
     * Neue Saison mit Übernahme (SAI-04): Linien mit Namen und Reihenfolge kopieren und die Spielerinnen
     * ihren Linien bzw. den Goalies zuweisen. Die Goalies des Ziels bestehen schon ({@code Team.aufnehmenIn}).
     *
     * @param kopien Teamzugehörigkeit der Quelle → ihre Kopie im Ziel
     */
    public static void uebernehmen(Saison quelle, Saison ziel, Map<Teamzugehoerigkeit, Teamzugehoerigkeit> kopien) {
        Map<UUID, Gruppe> neueGruppe = new HashMap<>();
        for (Gruppe g : Gruppe.<Gruppe>list("saison.id", quelle.id)) {
            Gruppe neu;
            if (g.art == GruppenArt.GOALIES) {
                neu = Gruppe.<Gruppe>find("team.id = ?1 and saison.id = ?2 and art = ?3", g.team.id, ziel.id,
                        GruppenArt.GOALIES).firstResult();
            } else {
                neu = Gruppe.linie(g.team, ziel, g.name, g.reihenfolge);
                neu.persist();
            }
            neueGruppe.put(g.id, neu);
        }
        kopien.forEach((alt, kopie) -> {
            if (kopie.funktion == Funktion.SPIELERIN) {
                alt.gruppen.stream().map(g -> neueGruppe.get(g.id)).filter(java.util.Objects::nonNull)
                        .forEach(kopie.gruppen::add);
            }
        });
    }

    private record Kontext(Team team, Saison saison, List<Teamzugehoerigkeit> zugehoerigkeiten, Funktion meineFunktion) {
    }

    /** Team in der aktuellen Saison, mit Zugehörigkeiten und Funktion der angemeldeten Person. */
    private Kontext kontext(Angemeldet wer, UUID teamId) {
        Saison saison = Saison.aktuelleOderFehler();
        Team team = Team.<Team>findByIdOptional(teamId)
                .orElseThrow(() -> FachFehler.nichtGefunden("Dieses Team gibt es nicht."));
        List<Teamzugehoerigkeit> zugehoerigkeiten = Teamzugehoerigkeit.list("team.id = ?1 and saison.id = ?2",
                team.id, saison.id);
        Funktion meine = zugehoerigkeiten.stream().filter(z -> z.person.id.equals(wer.personId()))
                .map(z -> z.funktion).findFirst().orElse(null);
        if (!team.gehoertZu(saison)) {
            LinienRegeln.pruefeDarfLesen(wer.admin(), meine);
            throw FachFehler.nichtGefunden("Dieses Team gibt es in der Saison " + saison.name + " nicht.");
        }
        return new Kontext(team, saison, zugehoerigkeiten, meine);
    }

    private Kontext bearbeitbar(Angemeldet wer, UUID teamId) {
        Kontext k = kontext(wer, teamId);
        LinienRegeln.pruefeDarfBearbeiten(wer.admin(), k.meineFunktion());
        k.saison().pruefeBeschreibbar();
        return k;
    }

    private static Gruppe finden(List<Gruppe> gruppen, UUID id) {
        return gruppen.stream().filter(g -> g.id.equals(id)).findFirst()
                .orElseThrow(() -> FachFehler.nichtGefunden("Diese Linie gibt es in diesem Team nicht."));
    }

    private static List<Gruppe> linien(List<Gruppe> gruppen) {
        return gruppen.stream().filter(g -> g.art == GruppenArt.LINIE).toList();
    }

    private static List<UUID> ids(List<Gruppe> gruppen) {
        return gruppen.stream().map(g -> g.id).toList();
    }

    /** Reihenfolge 1, 2, 3, … nach der angegebenen Ordnung. */
    private static void ordnen(List<Gruppe> linien, List<UUID> ordnung) {
        for (Gruppe g : linien) {
            g.reihenfolge = ordnung.indexOf(g.id) + 1;
        }
    }

    private static FachFehler nameVergeben(String name) {
        return FachFehler.konflikt("Name vergeben", "In diesem Team gibt es schon eine Gruppe «" + name + "».", "LIN-09");
    }
}
