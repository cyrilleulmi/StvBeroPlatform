package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.anmeldung.Angemeldet;
import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.saison.Saison;
import ch.stvbero.platform.kern.team.Funktion;
import ch.stvbero.platform.kern.team.Team;
import ch.stvbero.platform.kern.team.Teamzugehoerigkeit;
import ch.stvbero.platform.kern.uhr.Uhr;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Termine erfassen, ändern, verschieben und absagen (TER). Erfassen und bearbeiten dürfen Coaches eines
 * beteiligten Teams und Admins (TER-11, RR-06), ansehen alle Mitglieder der beteiligten Teams.
 */
@ApplicationScoped
public class TerminService {

    public static final String KOMMEND = "kommend";
    public static final String VERGANGEN = "vergangen";

    @Inject
    EntityManager em;

    @Inject
    Uhr uhr;

    @Inject
    TerminBenachrichtigungen benachrichtigungen;

    /**
     * Termine der eigenen Teams (TER-13). Kommende aufsteigend ab «jetzt», vergangene absteigend.
     * Eigene Teams heisst: Mitglied eines beteiligten Teams in der Saison des Termins.
     */
    public List<TerminKurzDto> liste(Angemeldet wer, LocalDate von, LocalDate bis, UUID teamId, TerminTyp typ,
                                     String ansicht) {
        boolean vergangen = VERGANGEN.equals(ansicht);
        if (ansicht != null && !vergangen && !KOMMEND.equals(ansicht)) {
            throw FachFehler.ungueltigesFeld("ansicht", "Die Ansicht ist «kommend» oder «vergangen».");
        }
        Instant jetzt = uhr.jetzt();
        StringBuilder jpql = new StringBuilder("""
                select distinct t from Termin t left join fetch t.teams join fetch t.saison
                where exists (select z.id from Teamzugehoerigkeit z
                              where z.person.id = :person and z.saison = t.saison and z.team member of t.teams)
                """);
        Map<String, Object> parameter = new HashMap<>();
        parameter.put("person", wer.personId());
        parameter.put("jetzt", jetzt);
        jpql.append(vergangen ? " and t.ende <= :jetzt" : " and t.ende > :jetzt");
        if (von != null) {
            jpql.append(" and t.beginn >= :von");
            parameter.put("von", von.atStartOfDay(Uhr.ZONE).toInstant());
        }
        if (bis != null) {
            jpql.append(" and t.beginn < :bis");
            parameter.put("bis", bis.plusDays(1).atStartOfDay(Uhr.ZONE).toInstant());
        }
        if (teamId != null) {
            jpql.append(" and exists (select x.id from Termin t2 join t2.teams x where t2 = t and x.id = :team)");
            parameter.put("team", teamId);
        }
        if (typ != null) {
            jpql.append(" and t.typ = :typ");
            parameter.put("typ", typ);
        }
        jpql.append(vergangen ? " order by t.beginn desc, t.id" : " order by t.beginn, t.id");
        TypedQuery<Termin> query = em.createQuery(jpql.toString(), Termin.class);
        parameter.forEach(query::setParameter);
        List<Termin> termine = query.getResultList();
        TerminLeser leser = new TerminLeser(em, wer, termine);
        return termine.stream().map(leser::kurz).toList();
    }

    public TerminDto lesen(Angemeldet wer, UUID terminId) {
        Termin termin = finden(terminId);
        TerminLeser leser = new TerminLeser(em, wer, List.of(termin));
        Set<Funktion> meine = leser.meineFunktionen(termin);
        TerminRegeln.pruefeDarfLesen(wer.admin(), meine);
        return leser.voll(termin, darfBearbeiten(wer, termin, meine));
    }

    /** Einzeltermin erfassen, auch für mehrere Teams (TER-01, TER-04, TER-11). */
    @Transactional
    public TerminDto anlegen(Angemeldet wer, TerminSchreiben eingabe) {
        Instant beginn = eingabe.beginn().toInstant();
        Instant ende = eingabe.ende().toInstant();
        Set<Team> teams = teams(eingabe.teamIds());
        Saison saison = saisonAm(TerminRegeln.datum(beginn), null);
        TerminRegeln.pruefeDarfErfassen(wer.admin(), funktionen(wer, teams, saison));
        pruefeEingabe(eingabe.typ(), beginn, ende, teams, saison);
        Termin t = Termin.neu();
        t.saison = saison;
        uebernehmen(t, eingabe, teams);
        t.persist();
        return lesen(wer, t.id);
    }

    /**
     * Termin ändern. Eine andere Beginnzeit ist eine Verschiebung (TER-06). Bei einem Serientermin gilt die
     * Änderung nur für diesen Termin; spätere Änderungen der Serie übernimmt er nicht mehr. Antworten bleiben.
     */
    @Transactional
    public TerminDto aendern(Angemeldet wer, UUID terminId, TerminSchreiben eingabe) {
        Termin t = finden(terminId);
        TerminRegeln.pruefeDarfErfassen(wer.admin(), funktionen(wer, t.teams, t.saison));
        TerminRegeln.pruefeAenderbar(t.status, t.ende, uhr.jetzt());
        Instant beginn = eingabe.beginn().toInstant();
        Instant ende = eingabe.ende().toInstant();
        Set<Team> teams = teams(eingabe.teamIds());
        Saison saison = saisonAm(TerminRegeln.datum(beginn), t.saison);
        // Auch nach der Änderung muss die Person Coach eines beteiligten Teams sein.
        TerminRegeln.pruefeDarfErfassen(wer.admin(), funktionen(wer, teams, saison));
        pruefeEingabe(eingabe.typ(), beginn, ende, teams, saison);
        List<Spiel> spiele = t.typ == TerminTyp.SPIELTAG ? Spiel.von(t.id) : List.of();
        TerminRegeln.pruefeTypWechsel(t.typ, eingabe.typ(), !spiele.isEmpty() || Aufgebot.findById(t.id) != null);
        SpielRegeln.pruefeVerschiebbar(spiele.stream().anyMatch(s -> s.quelle == Quelle.SWISS_UNIHOCKEY),
                t.beginn, beginn);
        Instant alterBeginn = t.beginn;
        String alteBezeichnung = TerminBenachrichtigungen.bezeichnung(t);
        List<UUID> bisherigeMitglieder = TerminBenachrichtigungen.mitglieder(t);
        long tage = ChronoUnit.DAYS.between(TerminRegeln.datum(t.beginn), TerminRegeln.datum(beginn));
        if (tage != 0) {
            // Von Hand erfasste Spiele ziehen mit, wenn der Spieltag auf einen anderen Tag verschoben wird (AUF-01).
            spiele.stream().filter(s -> s.quelle == Quelle.MANUELL)
                    .forEach(s -> s.anspielzeit = s.anspielzeit.atZone(Uhr.ZONE).plusDays(tage).toInstant());
        }
        t.verschoben = TerminRegeln.verschoben(t.verschoben, t.beginn, beginn);
        t.saison = saison;
        uebernehmen(t, eingabe, teams);
        if (t.serie != null) {
            t.einzelnGeaendert = true;
        }
        if (BenachrichtigungsRegeln.verschoben(alterBeginn, t.beginn)) {
            benachrichtigungen.verschoben(wer, t, alteBezeichnung, bisherigeMitglieder);
        }
        return lesen(wer, t.id);
    }

    /**
     * Termin absagen (TER-06). Danach sind keine Änderungen und keine Antworten mehr möglich. Alle Mitglieder
     * der beteiligten Teams werden benachrichtigt (BEN-07).
     */
    @Transactional
    public TerminDto absagen(Angemeldet wer, UUID terminId, String grund) {
        Termin t = finden(terminId);
        TerminRegeln.pruefeDarfErfassen(wer.admin(), funktionen(wer, t.teams, t.saison));
        TerminRegeln.pruefeAenderbar(t.status, t.ende, uhr.jetzt());
        t.saison.pruefeBeschreibbar();
        t.status = TerminStatus.ABGESAGT;
        t.absagegrund = grund.strip();
        benachrichtigungen.abgesagt(wer, t);
        return lesen(wer, t.id);
    }

    // ------------------------------------------------------------------ gemeinsam mit SerieService

    static Termin finden(UUID id) {
        return Termin.<Termin>findByIdOptional(id)
                .orElseThrow(() -> FachFehler.nichtGefunden("Diesen Termin gibt es nicht."));
    }

    /** Teams zu den IDs. Unbekannte IDs: 400. */
    static Set<Team> teams(Collection<UUID> ids) {
        Set<Team> teams = new LinkedHashSet<>();
        for (UUID id : new LinkedHashSet<>(ids)) {
            teams.add(Team.<Team>findByIdOptional(id)
                    .orElseThrow(() -> FachFehler.ungueltigesFeld("teamIds", "Dieses Team gibt es nicht.")));
        }
        return teams;
    }

    /** Funktionen der Person in diesen Teams in dieser Saison (leer, wenn sie in keinem Mitglied ist). */
    static Set<Funktion> funktionen(Angemeldet wer, Collection<Team> teams, Saison saison) {
        if (teams.isEmpty()) {
            return Set.of();
        }
        Set<Funktion> funktionen = new LinkedHashSet<>();
        Teamzugehoerigkeit.<Teamzugehoerigkeit>list("person.id = ?1 and saison.id = ?2 and team.id in ?3",
                wer.personId(), saison.id, teams.stream().map(t -> t.id).toList()).forEach(z -> funktionen.add(z.funktion));
        return funktionen;
    }

    /** Die Saison, in die der Tag fällt; sonst die angegebene bzw. die aktuelle. */
    static Saison saisonAm(LocalDate tag, Saison sonst) {
        return Saison.<Saison>find("von <= ?1 and bis >= ?1", tag).firstResultOptional()
                .orElseGet(() -> sonst != null ? sonst : Saison.aktuelleOderFehler());
    }

    /** Alle Teams gehören in der Saison zum Verein, die Saison ist nicht im Archiv (SAI-02). */
    static void pruefeTeamsInSaison(Collection<Team> teams, Saison saison) {
        for (Team team : teams) {
            if (!team.gehoertZu(saison)) {
                throw FachFehler.ungueltigesFeld("teamIds", "Ein Team gibt es in der Saison " + saison.name + " nicht.");
            }
        }
        saison.pruefeBeschreibbar();
    }

    private boolean darfBearbeiten(Angemeldet wer, Termin t, Set<Funktion> meine) {
        return TerminRegeln.darfBearbeiten(wer.admin(), meine) && t.status == TerminStatus.GEPLANT
                && !t.istVorbei(uhr.jetzt()) && !t.saison.istArchiv();
    }

    private void pruefeEingabe(TerminTyp typ, Instant beginn, Instant ende, Set<Team> teams, Saison saison) {
        TerminRegeln.pruefeZeit(beginn, ende);
        TerminRegeln.pruefeNichtVorbei(ende, uhr.jetzt());
        TerminRegeln.pruefeTeams(typ, teams.size());
        pruefeTeamsInSaison(teams, saison);
    }

    private static void uebernehmen(Termin t, TerminSchreiben e, Set<Team> teams) {
        t.typ = e.typ();
        t.titel = e.titel().strip();
        t.beginn = e.beginn().toInstant();
        t.ende = e.ende().toInstant();
        t.ort = Ort.von(e.ort());
        t.hinweise = TerminRegeln.leerZuNull(e.hinweise());
        t.antwortfristStunden = TerminRegeln.antwortfrist(e.antwortfristStunden());
        t.treffpunktZeit = e.treffpunkt() == null ? null : e.treffpunkt().zeit().toInstant();
        t.treffpunktOrt = e.treffpunkt() == null ? null : e.treffpunkt().ort().strip();
        t.teams.clear();
        t.teams.addAll(teams);
    }
}
