package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.anmeldung.Angemeldet;
import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.uhr.Uhr;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Spiele eines Spieltags von Hand erfassen, ändern (z.B. Resultat) und löschen (AUF-14 bis AUF-16).
 * Nur Coaches des Teams und Admins (TER-11, RR-06).
 */
@ApplicationScoped
public class SpielService {

    @Inject
    Uhr uhr;

    @Transactional
    public SpielDto hinzufuegen(Angemeldet wer, UUID terminId, SpielSchreiben eingabe) {
        Termin t = TerminService.finden(terminId);
        pruefeDarf(wer, t);
        AufgebotRegeln.pruefeSpieltag(t.typ);
        TerminRegeln.pruefeAenderbar(t.status, t.ende, uhr.jetzt());
        t.saison.pruefeBeschreibbar();
        Instant anspielzeit = eingabe.anspielzeit().toInstant();
        SpielRegeln.pruefeAmSpieltag(anspielzeit, t.beginn);
        Spiel s = Spiel.neu(t);
        uebernehmen(s, eingabe);
        s.persist();
        return s.dto();
    }

    /**
     * Spiel ändern. Das Resultat lässt sich auch nach dem Spieltag erfassen (AUF-15), deshalb gilt hier nur
     * «nicht abgesagt». Bei importierten Spielen sind Gegner, Anspielzeit und Heim/Auswärts geschützt.
     */
    @Transactional
    public SpielDto aendern(Angemeldet wer, UUID terminId, UUID spielId, SpielSchreiben eingabe) {
        Termin t = TerminService.finden(terminId);
        pruefeDarf(wer, t);
        Spiel s = finden(t, spielId);
        SpielRegeln.pruefeNichtAbgesagt(t.status);
        t.saison.pruefeBeschreibbar();
        Instant anspielzeit = eingabe.anspielzeit().toInstant();
        SpielRegeln.pruefeAenderbar(s.quelle, s.gegner, s.anspielzeit, s.heim,
                eingabe.gegner().strip(), anspielzeit, eingabe.heim());
        SpielRegeln.pruefeAmSpieltag(anspielzeit, t.beginn);
        uebernehmen(s, eingabe);
        return s.dto();
    }

    @Transactional
    public void loeschen(Angemeldet wer, UUID terminId, UUID spielId) {
        Termin t = TerminService.finden(terminId);
        pruefeDarf(wer, t);
        Spiel s = finden(t, spielId);
        TerminRegeln.pruefeAenderbar(t.status, t.ende, uhr.jetzt());
        t.saison.pruefeBeschreibbar();
        SpielRegeln.pruefeLoeschbar(s.quelle, s.beiSuNichtMehrVorhanden);
        s.delete();
    }

    private static void pruefeDarf(Angemeldet wer, Termin t) {
        TerminRegeln.pruefeDarfErfassen(wer.admin(), TerminService.funktionen(wer, t.teams, t.saison));
    }

    private static Spiel finden(Termin t, UUID spielId) {
        return Spiel.<Spiel>findByIdOptional(spielId).filter(s -> s.termin.id.equals(t.id))
                .orElseThrow(() -> FachFehler.nichtGefunden("Dieses Spiel gibt es nicht."));
    }

    private static void uebernehmen(Spiel s, SpielSchreiben e) {
        s.gegner = e.gegner().strip();
        s.anspielzeit = e.anspielzeit().toInstant();
        s.heim = e.heim();
        s.resultat = TerminRegeln.leerZuNull(e.resultat());
    }
}
