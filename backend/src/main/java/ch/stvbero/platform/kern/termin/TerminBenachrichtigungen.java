package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.anmeldung.Angemeldet;
import ch.stvbero.platform.kern.benachrichtigung.BenachrichtigungArt;
import ch.stvbero.platform.kern.benachrichtigung.Benachrichtigungen;
import ch.stvbero.platform.kern.benachrichtigung.Nachricht;
import ch.stvbero.platform.kern.person.Person;
import ch.stvbero.platform.kern.team.Funktion;
import ch.stvbero.platform.kern.team.Teamzugehoerigkeit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Auslöser der Benachrichtigungen rund um Termine, Antworten und Aufgebote (BEN-02 bis BEN-05, BEN-07).
 * Bestimmt Empfänger und Text nach {@link BenachrichtigungsRegeln} und versendet in der Transaktion der
 * Änderung über {@link Benachrichtigungen}.
 */
@ApplicationScoped
public class TerminBenachrichtigungen {

    @Inject
    Benachrichtigungen benachrichtigungen;

    /**
     * Nach einer gespeicherten Antwort: Abmeldung einer Aufgebotenen (BEN-04) oder kurzfristige Absage
     * (BEN-02) an die Coaches der Teams, in denen die Person beim Termin Spielerin ist (O-17). Trifft beides
     * zu, geht nur die Abmeldung der Aufgebotenen raus.
     */
    void antwortGeaendert(Angemeldet wer, Termin t, List<Teamzugehoerigkeit> mitglieder, UUID personId,
                          AntwortStatus bisher, AntwortStatus neu, boolean kurzfristig) {
        Aufgebot a = t.typ == TerminTyp.SPIELTAG ? Aufgebot.findById(t.id) : null;
        boolean aufgeboten = a != null && a.personIds.contains(personId);
        BenachrichtigungArt art;
        if (BenachrichtigungsRegeln.abmeldungAufgebotene(aufgeboten, a != null && a.veroeffentlichtAm != null, bisher, neu)) {
            art = BenachrichtigungArt.AUFGEBOTENE_ABGEMELDET;
        } else if (BenachrichtigungsRegeln.kurzfristigeAbsage(bisher, neu, kurzfristig)) {
            art = BenachrichtigungArt.KURZFRISTIGE_ABSAGE;
        } else {
            return;
        }
        Set<UUID> teams = new LinkedHashSet<>();
        mitglieder.stream().filter(z -> z.person.id.equals(personId) && z.funktion == Funktion.SPIELERIN)
                .forEach(z -> teams.add(z.team.id));
        List<UUID> coaches = mitglieder.stream()
                .filter(z -> z.funktion == Funktion.COACH && teams.contains(z.team.id))
                .map(z -> z.person.id).toList();
        Set<UUID> empfaenger = BenachrichtigungsRegeln.ohneAusloeser(coaches, wer.personId());
        empfaenger.remove(personId);
        String person = name(personId);
        String termin = bezeichnung(t);
        String text = art == BenachrichtigungArt.AUFGEBOTENE_ABGEMELDET
                ? BenachrichtigungsRegeln.textAufgeboteneAbgemeldet(person, termin, neu)
                : BenachrichtigungsRegeln.textKurzfristig(person, termin, neu);
        senden(empfaenger, p -> new Nachricht(art, p, t.id, text));
    }

    /** Aufgebot veröffentlicht: alle Spielerinnen des Teams, je mit «aufgeboten» oder «nicht aufgeboten» (BEN-03). */
    void veroeffentlicht(Angemeldet wer, Termin t, Collection<UUID> spielerinnen, Set<UUID> aufgeboten) {
        String termin = bezeichnung(t);
        senden(BenachrichtigungsRegeln.ohneAusloeser(spielerinnen, wer.personId()),
                p -> new Nachricht(BenachrichtigungArt.AUFGEBOT_VEROEFFENTLICHT, p, t.id,
                        BenachrichtigungsRegeln.textVeroeffentlicht(termin, aufgeboten.contains(p))));
    }

    /** Veröffentlichtes Aufgebot geändert: nur wer neu dazukommt oder herausfällt (BEN-05, AUF-27). */
    void aufgebotGeaendert(Angemeldet wer, Termin t, Set<UUID> bisher, Set<UUID> jetzt) {
        BenachrichtigungsRegeln.Aenderung aenderung = BenachrichtigungsRegeln.aufgebotGeaendert(bisher, jetzt);
        Set<UUID> betroffene = new LinkedHashSet<>(aenderung.neu());
        betroffene.addAll(aenderung.entfernt());
        String termin = bezeichnung(t);
        senden(BenachrichtigungsRegeln.ohneAusloeser(betroffene, wer.personId()),
                p -> new Nachricht(BenachrichtigungArt.AUFGEBOT_GEAENDERT, p, t.id,
                        BenachrichtigungsRegeln.textAufgebotGeaendert(termin, aenderung.neu().contains(p))));
    }

    /** Termin abgesagt: alle Mitglieder der beteiligten Teams (BEN-07, TER-12). */
    void abgesagt(Angemeldet wer, Termin t) {
        String text = BenachrichtigungsRegeln.textAbgesagt(bezeichnung(t), t.absagegrund);
        senden(BenachrichtigungsRegeln.ohneAusloeser(mitglieder(t), wer.personId()),
                p -> new Nachricht(BenachrichtigungArt.TERMIN_ABGESAGT, p, t.id, text));
    }

    /**
     * Termin verschoben: alle Mitglieder der beteiligten Teams (BEN-07, TER-12), vor und nach der Änderung,
     * damit auch ein entferntes Team davon erfährt. Bezeichnung mit dem bisherigen Datum.
     */
    void verschoben(Angemeldet wer, Termin t, String alteBezeichnung, Collection<UUID> bisherigeMitglieder) {
        Set<UUID> alle = new LinkedHashSet<>(bisherigeMitglieder);
        alle.addAll(mitglieder(t));
        String text = BenachrichtigungsRegeln.textVerschoben(alteBezeichnung, t.beginn);
        senden(BenachrichtigungsRegeln.ohneAusloeser(alle, wer.personId()),
                p -> new Nachricht(BenachrichtigungArt.TERMIN_VERSCHOBEN, p, t.id, text));
    }

    /** Alle Mitglieder (jede Funktion) der beteiligten Teams in der Saison des Termins, nach Name. */
    static List<UUID> mitglieder(Termin t) {
        List<UUID> teams = t.teams.stream().map(team -> team.id).toList();
        if (teams.isEmpty()) {
            return List.of();
        }
        return Teamzugehoerigkeit.getEntityManager().createQuery("""
                        select distinct z.person.id, z.person.nachname, z.person.vorname from Teamzugehoerigkeit z
                        where z.saison.id = :saison and z.team.id in :teams
                        order by z.person.nachname, z.person.vorname, z.person.id""", Object[].class)
                .setParameter("saison", t.saison.id).setParameter("teams", teams).getResultList()
                .stream().map(z -> (UUID) z[0]).toList();
    }

    static String bezeichnung(Termin t) {
        return BenachrichtigungsRegeln.bezeichnung(t.titel, t.beginn);
    }

    private static String name(UUID personId) {
        Person p = Person.findById(personId);
        return p.vorname + " " + p.nachname;
    }

    private void senden(Collection<UUID> empfaenger, java.util.function.Function<UUID, Nachricht> nachricht) {
        List<Nachricht> nachrichten = new ArrayList<>();
        empfaenger.forEach(p -> nachrichten.add(nachricht.apply(p)));
        benachrichtigungen.senden(nachrichten);
    }
}
