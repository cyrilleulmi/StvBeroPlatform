package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.anmeldung.Angemeldet;
import ch.stvbero.platform.kern.linie.Gruppe;
import ch.stvbero.platform.kern.person.PersonKurz;
import ch.stvbero.platform.kern.team.Funktion;
import ch.stvbero.platform.kern.team.GruppenArt;
import ch.stvbero.platform.kern.team.Team;
import ch.stvbero.platform.kern.team.Teamzugehoerigkeit;
import ch.stvbero.platform.kern.uhr.Uhr;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Aufgebot eines Spieltags (AUF-21 bis AUF-28): Auswahl nach Linien mit Antwortstatus, speichern und
 * veröffentlichen. Nur Coaches des Teams und Admins (RR-03), durchgesetzt hier im Backend (RR-07).
 */
@ApplicationScoped
public class AufgebotService {

    @Inject
    EntityManager em;

    @Inject
    Uhr uhr;

    @Inject
    AntwortService antworten;

    @Inject
    TerminBenachrichtigungen benachrichtigungen;

    public AufgebotDto lesen(Angemeldet wer, UUID terminId) {
        Termin t = TerminService.finden(terminId);
        pruefeDarf(wer, t);
        AufgebotRegeln.pruefeSpieltag(t.typ);
        return dto(t, Aufgebot.findById(t.id));
    }

    /**
     * Auswahl speichern (AUF-21, AUF-22). Jede Spielerin des Teams lässt sich aufbieten, unabhängig von
     * ihrer Antwort; Coaches nicht (AUF-28). Ist das Aufgebot veröffentlicht, bleibt es das, und wer neu
     * dazukommt oder herausfällt, wird benachrichtigt (AUF-27, BEN-05).
     */
    @Transactional
    public AufgebotDto setzen(Angemeldet wer, UUID terminId, AufgebotSetzen eingabe) {
        Termin t = TerminService.finden(terminId);
        pruefeDarf(wer, t);
        AufgebotRegeln.pruefeSpieltag(t.typ);
        TerminRegeln.pruefeAenderbar(t.status, t.ende, uhr.jetzt());
        t.saison.pruefeBeschreibbar();
        Set<UUID> auswahl = new LinkedHashSet<>(eingabe.personIds());
        AufgebotRegeln.pruefeAufbietbar(auswahl, spielerinnen(t));
        Aufgebot a = gesperrt(t.id);
        Set<UUID> bisher = new LinkedHashSet<>(a.personIds);
        a.personIds.clear();
        a.personIds.addAll(auswahl);
        if (a.veroeffentlichtAm != null) {
            benachrichtigungen.aufgebotGeaendert(wer, t, bisher, auswahl);
        }
        return dto(t, a);
    }

    /**
     * Aufgebot veröffentlichen (AUF-23, AUF-24). Danach sehen alle Spielerinnen des Teams, ob sie aufgeboten
     * sind und werden benachrichtigt (BEN-03).
     */
    @Transactional
    public AufgebotDto veroeffentlichen(Angemeldet wer, UUID terminId) {
        Termin t = TerminService.finden(terminId);
        pruefeDarf(wer, t);
        AufgebotRegeln.pruefeSpieltag(t.typ);
        TerminRegeln.pruefeAenderbar(t.status, t.ende, uhr.jetzt());
        t.saison.pruefeBeschreibbar();
        Aufgebot a = gesperrt(t.id);
        a.personIds.retainAll(spielerinnen(t));
        AufgebotRegeln.pruefeVeroeffentlichbar(a.status());
        a.veroeffentlichtAm = uhr.jetzt();
        benachrichtigungen.veroeffentlicht(wer, t, spielerinnen(t), a.personIds);
        return dto(t, a);
    }

    private void pruefeDarf(Angemeldet wer, Termin t) {
        AufgebotRegeln.pruefeDarfBearbeiten(wer.admin(), TerminService.funktionen(wer, t.teams, t.saison));
    }

    /** Aufgebot des Termins, für die Transaktion gesperrt; zwei gleichzeitige Anfragen ergeben nie 500. */
    private Aufgebot gesperrt(UUID terminId) {
        em.createNativeQuery("insert into aufgebot (termin_id) values (?) on conflict do nothing")
                .setParameter(1, terminId).executeUpdate();
        return em.find(Aufgebot.class, terminId, LockModeType.PESSIMISTIC_WRITE);
    }

    /** Personen mit Funktion Spielerin im Team des Spieltags (in seiner Saison). */
    private Set<UUID> spielerinnen(Termin t) {
        Set<UUID> ids = new LinkedHashSet<>();
        antworten.mitglieder(t).stream().filter(z -> z.funktion == Funktion.SPIELERIN).forEach(z -> ids.add(z.person.id));
        return ids;
    }

    /** Alle Spielerinnen nach Linie, dann Goalies, dann «Ohne Linie»; leere Gruppen fehlen (wie die Übersicht). */
    private AufgebotDto dto(Termin t, Aufgebot a) {
        Set<UUID> aufgeboten = a == null ? Set.of() : a.personIds;
        Map<UUID, AntwortService.Gespeichert> gespeichert = antworten.antworten(t.id);
        List<Teamzugehoerigkeit> spielerinnen = antworten.mitglieder(t).stream()
                .filter(z -> z.funktion == Funktion.SPIELERIN).toList();
        Map<UUID, PersonKurz> personen = new HashMap<>();
        spielerinnen.forEach(z -> personen.put(z.person.id, PersonKurz.von(z.person)));

        Map<UUID, List<AufgebotDto.Eintrag>> proGruppe = new LinkedHashMap<>();
        List<AufgebotDto.Eintrag> ohneLinie = new ArrayList<>();
        int anzahl = 0;
        for (Teamzugehoerigkeit z : spielerinnen) {
            boolean ja = aufgeboten.contains(z.person.id);
            anzahl += ja ? 1 : 0;
            AufgebotDto.Eintrag e = new AufgebotDto.Eintrag(personen.get(z.person.id),
                    AntwortService.antwort(gespeichert.get(z.person.id), true, personen), ja);
            if (z.gruppen.isEmpty()) {
                ohneLinie.add(e);
            } else {
                z.gruppen.forEach(g -> proGruppe.computeIfAbsent(g.id, k -> new ArrayList<>()).add(e));
            }
        }
        List<AufgebotDto.Gruppe> gruppen = new ArrayList<>();
        for (Team team : t.teams) {
            for (Gruppe g : Gruppe.von(team, t.saison)) {
                List<AufgebotDto.Eintrag> eintraege = proGruppe.get(g.id);
                if (eintraege != null && !eintraege.isEmpty()) {
                    gruppen.add(new AufgebotDto.Gruppe(g.id, g.art == GruppenArt.GOALIES
                            ? UebersichtDto.Art.GOALIES : UebersichtDto.Art.LINIE, g.name, sortiert(eintraege)));
                }
            }
        }
        if (!ohneLinie.isEmpty()) {
            gruppen.add(new AufgebotDto.Gruppe(null, UebersichtDto.Art.OHNE_LINIE, AntwortService.OHNE_LINIE,
                    sortiert(ohneLinie)));
        }
        boolean veroeffentlicht = a != null && a.veroeffentlichtAm != null;
        return new AufgebotDto(t.id, AufgebotRegeln.status(veroeffentlicht, anzahl),
                a == null ? null : TerminLeser.zeit(a.veroeffentlichtAm), anzahl, gruppen);
    }

    private static List<AufgebotDto.Eintrag> sortiert(List<AufgebotDto.Eintrag> eintraege) {
        return eintraege.stream().sorted(Comparator.comparing(AufgebotDto.Eintrag::person, PersonKurz.REIHENFOLGE)).toList();
    }
}
