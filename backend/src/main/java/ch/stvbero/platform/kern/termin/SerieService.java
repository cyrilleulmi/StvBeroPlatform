package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.anmeldung.Angemeldet;
import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.saison.Saison;
import ch.stvbero.platform.kern.team.Team;
import ch.stvbero.platform.kern.uhr.Uhr;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Serien: einmal erfasst, erzeugen sie die Termine im Gültigkeitszeitraum (TER-05). Änderungen gelten für
 * künftige Termine (TER-08), Ausnahmen nehmen Zeiträume aus (TER-07). Rechte wie bei Terminen (TER-11, RR-06).
 */
@ApplicationScoped
public class SerieService {

    /** Grund, mit dem ein Termin abgesagt wird, der nach einer Änderung nicht mehr zur Serie passt. */
    static final String GRUND_SERIE_GEAENDERT = "Serie geändert";

    @Inject
    EntityManager em;

    @Inject
    Uhr uhr;

    /** Serie anlegen und ihre künftigen Termine erzeugen (TER-05). */
    @Transactional
    public SerieDto anlegen(Angemeldet wer, SerieSchreiben eingabe) {
        Set<Team> teams = TerminService.teams(eingabe.teamIds());
        Saison saison = TerminService.saisonAm(eingabe.gueltigVon(), null);
        TerminRegeln.pruefeDarfErfassen(wer.admin(), TerminService.funktionen(wer, teams, saison));
        pruefeEingabe(eingabe, teams, saison);
        Serie s = new Serie();
        s.id = UUID.randomUUID();
        s.saison = saison;
        uebernehmen(s, eingabe, teams);
        s.persist();
        Instant jetzt = uhr.jetzt();
        for (LocalDate tag : daten(s)) {
            if (TerminRegeln.zeitpunkt(tag, s.beginnZeit).isAfter(jetzt)) {
                erzeugen(s, tag).persist();
            }
        }
        return SerieDto.von(s);
    }

    public SerieDto lesen(Angemeldet wer, UUID serieId) {
        Serie s = finden(serieId);
        TerminRegeln.pruefeDarfLesen(wer.admin(), TerminService.funktionen(wer, s.teams, s.saison));
        return SerieDto.von(s);
    }

    /**
     * Serie ändern (TER-08). Künftige Termine übernehmen die neuen Angaben, Antworten bleiben erhalten.
     * Einzeln geänderte und abgesagte Termine bleiben, wie sie sind (TER-06). Termine, die nicht mehr zur
     * Serie passen, verschwinden bzw. werden abgesagt, wenn schon jemand geantwortet hat.
     */
    @Transactional
    public SerieDto aendern(Angemeldet wer, UUID serieId, SerieSchreiben eingabe) {
        Serie s = finden(serieId);
        TerminRegeln.pruefeDarfErfassen(wer.admin(), TerminService.funktionen(wer, s.teams, s.saison));
        Set<Team> teams = TerminService.teams(eingabe.teamIds());
        TerminRegeln.pruefeDarfErfassen(wer.admin(), TerminService.funktionen(wer, teams, s.saison));
        pruefeEingabe(eingabe, teams, s.saison);
        uebernehmen(s, eingabe, teams);

        Instant jetzt = uhr.jetzt();
        Map<LocalDate, Termin> alle = new HashMap<>();
        Map<LocalDate, TerminRegeln.Bestehend> kuenftige = new HashMap<>();
        for (Termin t : termine(s)) {
            alle.put(t.serieDatum, t);
            if (t.beginn.isAfter(jetzt)) {
                kuenftige.put(t.serieDatum, new TerminRegeln.Bestehend(t.status == TerminStatus.GEPLANT, t.einzelnGeaendert));
            }
        }
        List<LocalDate> soll = daten(s).stream()
                .filter(tag -> TerminRegeln.zeitpunkt(tag, s.beginnZeit).isAfter(jetzt))
                .filter(tag -> !alle.containsKey(tag) || kuenftige.containsKey(tag))
                .toList();
        TerminRegeln.Abgleich abgleich = TerminRegeln.abgleichen(kuenftige, soll);
        for (LocalDate tag : abgleich.uebernehmen()) {
            angleichen(alle.get(tag), s, tag);
        }
        for (LocalDate tag : abgleich.wegfallen()) {
            wegfallen(alle.get(tag), GRUND_SERIE_GEAENDERT);
        }
        em.flush();
        for (LocalDate tag : abgleich.erzeugen()) {
            erzeugen(s, tag).persist();
        }
        return SerieDto.von(s);
    }

    /**
     * Zeitraum ausnehmen, z.B. Ferien (TER-07). Künftige geplante Termine der Serie in diesem Zeitraum
     * verschwinden; haben sie schon Antworten, werden sie mit dem Grund der Ausnahme abgesagt.
     */
    @Transactional
    public SerieDto ausnahmeAnlegen(Angemeldet wer, UUID serieId, AusnahmeDto eingabe) {
        Serie s = finden(serieId);
        TerminRegeln.pruefeDarfErfassen(wer.admin(), TerminService.funktionen(wer, s.teams, s.saison));
        TerminRegeln.pruefeAusnahme(eingabe.von(), eingabe.bis());
        s.saison.pruefeBeschreibbar();
        Ausnahme a = new Ausnahme();
        a.id = UUID.randomUUID();
        a.serie = s;
        a.von = eingabe.von();
        a.bis = eingabe.bis();
        a.grund = eingabe.grund().strip();
        s.ausnahmen.add(a);

        Instant jetzt = uhr.jetzt();
        TerminRegeln.Zeitraum zeitraum = new TerminRegeln.Zeitraum(a.von, a.bis);
        for (Termin t : termine(s)) {
            if (t.status == TerminStatus.GEPLANT && t.beginn.isAfter(jetzt)
                    && zeitraum.enthaelt(TerminRegeln.datum(t.beginn))) {
                wegfallen(t, a.grund);
            }
        }
        return SerieDto.von(s);
    }

    private static Serie finden(UUID id) {
        return Serie.<Serie>findByIdOptional(id)
                .orElseThrow(() -> FachFehler.nichtGefunden("Diese Serie gibt es nicht."));
    }

    private static List<Termin> termine(Serie s) {
        return Termin.list("serie.id = ?1 order by serieDatum", s.id);
    }

    private static List<LocalDate> daten(Serie s) {
        return TerminRegeln.daten(s.wochentag.tag, s.gueltigVon, s.gueltigBis, s.ausnahmeZeitraeume());
    }

    private static void pruefeEingabe(SerieSchreiben e, Set<Team> teams, Saison saison) {
        TerminRegeln.pruefeSerie(TerminRegeln.zeit(e.beginnZeit()), TerminRegeln.zeit(e.endeZeit()),
                e.gueltigVon(), e.gueltigBis());
        TerminService.pruefeTeamsInSaison(teams, saison);
    }

    private static void uebernehmen(Serie s, SerieSchreiben e, Set<Team> teams) {
        s.typ = e.typ();
        s.titel = e.titel().strip();
        s.wochentag = e.wochentag();
        s.beginnZeit = TerminRegeln.zeit(e.beginnZeit());
        s.endeZeit = TerminRegeln.zeit(e.endeZeit());
        s.gueltigVon = e.gueltigVon();
        s.gueltigBis = e.gueltigBis();
        s.ort = Ort.von(e.ort());
        s.antwortfristStunden = TerminRegeln.antwortfrist(e.antwortfristStunden());
        s.teams.clear();
        s.teams.addAll(teams);
    }

    /** Neuer Termin der Serie an diesem Tag. */
    private static Termin erzeugen(Serie s, LocalDate tag) {
        Termin t = Termin.neu();
        t.serie = s;
        t.serieDatum = tag;
        t.saison = TerminService.saisonAm(tag, s.saison);
        angleichen(t, s, tag);
        return t;
    }

    /** Angaben der Serie auf den Termin übertragen. */
    private static void angleichen(Termin t, Serie s, LocalDate tag) {
        t.typ = s.typ.terminTyp();
        t.titel = s.titel;
        t.beginn = TerminRegeln.zeitpunkt(tag, s.beginnZeit);
        t.ende = TerminRegeln.zeitpunkt(tag, s.endeZeit);
        t.ort = s.ort == null ? null : Ort.von(s.ort.dto());
        t.antwortfristStunden = s.antwortfristStunden;
        t.teams.clear();
        t.teams.addAll(s.teams);
    }

    /** Termin entfernen oder, wenn schon jemand geantwortet hat, absagen (TER-07, zustaende.md). */
    private void wegfallen(Termin t, String grund) {
        Number antworten = (Number) em.createNativeQuery("select count(*) from antwort where termin_id = ?1")
                .setParameter(1, t.id).getSingleResult();
        switch (TerminRegeln.beiAusnahme(antworten.longValue() > 0)) {
            case ENTFERNEN -> t.delete();
            case ABSAGEN -> {
                t.status = TerminStatus.ABGESAGT;
                t.absagegrund = grund;
            }
        }
    }
}
