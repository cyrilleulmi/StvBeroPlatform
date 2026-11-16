package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.anmeldung.Angemeldet;
import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.linie.Gruppe;
import ch.stvbero.platform.kern.person.Person;
import ch.stvbero.platform.kern.person.PersonKurz;
import ch.stvbero.platform.kern.team.Funktion;
import ch.stvbero.platform.kern.team.GruppenArt;
import ch.stvbero.platform.kern.team.Team;
import ch.stvbero.platform.kern.team.TeamSaison;
import ch.stvbero.platform.kern.team.Teamzugehoerigkeit;
import ch.stvbero.platform.kern.uhr.Uhr;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
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
 * Antworten auf Termine (ANM): eigene Antwort, stellvertretende Antwort und die Übersicht nach Team und
 * Linie. Rechte nach RR-02, RR-03 und RR-06, durchgesetzt hier im Backend (RR-07).
 */
@ApplicationScoped
public class AntwortService {

    static final String OHNE_LINIE = "Ohne Linie";

    @Inject
    EntityManager em;

    @Inject
    Uhr uhr;

    /** Gespeicherte Antwort, wie sie in der Tabelle steht. */
    record Gespeichert(AntwortStatus status, String kommentar, Instant geaendertAm, UUID eingetragenVon,
                       boolean kurzfristig) {
    }

    // ------------------------------------------------------------------ Schreiben

    /**
     * Antwort setzen oder ändern (ANM-01 bis ANM-12). {@code personId == null} oder die eigene ID: für sich
     * selbst, sonst stellvertretend (ANM-11, RR-06) mit «eingetragen von» (ANM-12).
     */
    @Transactional
    public AntwortDto setzen(Angemeldet wer, UUID terminId, UUID personId, AntwortSetzen eingabe) {
        Termin t = TerminService.finden(terminId);
        List<Teamzugehoerigkeit> mitglieder = mitglieder(t);
        Map<UUID, Funktion> meine = funktionen(mitglieder, wer.personId());
        UUID fuer;
        UUID eingetragenVon;
        if (personId == null || personId.equals(wer.personId())) {
            AntwortRegeln.pruefeDarfSelbstAntworten(!meine.isEmpty());
            fuer = wer.personId();
            eingetragenVon = null;
        } else {
            TerminRegeln.pruefeDarfLesen(wer.admin(), meine.values());
            Person.<Person>findByIdOptional(personId)
                    .orElseThrow(() -> FachFehler.nichtGefunden("Diese Person gibt es nicht."));
            Map<UUID, Funktion> ihre = funktionen(mitglieder, personId);
            if (ihre.isEmpty() && (wer.admin() || meine.containsValue(Funktion.COACH))) {
                throw FachFehler.nichtGefunden("Diese Person gehört zu keinem Team dieses Termins.");
            }
            AntwortRegeln.pruefeDarfStellvertreten(wer.admin(), meine, ihre);
            fuer = personId;
            eingetragenVon = wer.personId();
        }
        Instant jetzt = uhr.jetzt();
        AntwortRegeln.pruefeOffen(t.status, t.ende, jetzt);
        t.saison.pruefeBeschreibbar();
        AntwortStatus neu = eingabe.status().status();
        AntwortRegeln.pruefeKommentar(neu, eingabe.kommentar());

        Gespeichert bisher = gesperrt(t.id, fuer);
        boolean kurzfristig = AntwortRegeln.kurzfristig(
                bisher == null ? AntwortStatus.OFFEN : bisher.status(), bisher != null && bisher.kurzfristig(), neu,
                TerminRegeln.antwortfrist(t.beginn, t.antwortfristStunden), jetzt);
        String kommentar = TerminRegeln.leerZuNull(eingabe.kommentar());
        em.createNativeQuery("""
                        insert into antwort (termin_id, person_id, status, kommentar, geaendert_am, eingetragen_von, kurzfristig)
                        values (?, ?, ?, ?, ?, ?, ?)
                        on conflict (termin_id, person_id) do update
                        set status = excluded.status, kommentar = excluded.kommentar, geaendert_am = excluded.geaendert_am,
                            eingetragen_von = excluded.eingetragen_von, kurzfristig = excluded.kurzfristig
                        """)
                .setParameter(1, t.id).setParameter(2, fuer).setParameter(3, neu.name()).setParameter(4, kommentar)
                .setParameter(5, jetzt).setParameter(6, eingetragenVon).setParameter(7, kurzfristig)
                .executeUpdate();
        // Benachrichtigung der Coaches bei kurzfristiger Absage kommt mit F-06 (BEN-02).
        return new AntwortDto(neu, kommentar, TerminLeser.zeit(jetzt),
                eingetragenVon == null ? null : PersonKurz.von(Person.findById(eingetragenVon)), kurzfristig);
    }

    // ------------------------------------------------------------------ Lesen

    /** Übersicht nach Team und Gruppe mit Coach-Block und Zählern (ANM-20 bis ANM-32). */
    public UebersichtDto uebersicht(Angemeldet wer, UUID terminId) {
        Termin t = TerminService.finden(terminId);
        List<Teamzugehoerigkeit> mitglieder = mitglieder(t);
        Map<UUID, Funktion> meine = funktionen(mitglieder, wer.personId());
        TerminRegeln.pruefeDarfLesen(wer.admin(), meine.values());

        Map<UUID, Gespeichert> antworten = antworten(t.id);
        Map<UUID, PersonKurz> personen = new HashMap<>();
        mitglieder.forEach(z -> personen.put(z.person.id, PersonKurz.von(z.person)));

        List<UebersichtDto.TeamName> teams = teamNamen(t);
        List<UebersichtDto.TeamBlock> bloecke = new ArrayList<>();
        List<UebersichtDto.CoachEintrag> coaches = new ArrayList<>();
        Set<UUID> alleSpielerinnen = new LinkedHashSet<>();
        for (UebersichtDto.TeamName team : teams) {
            boolean kommentareSichtbar = AntwortRegeln.kommentarSichtbar(wer.admin(), false, meine.get(team.id()));
            List<Teamzugehoerigkeit> imTeam = mitglieder.stream().filter(z -> z.team.id.equals(team.id())).toList();
            List<Teamzugehoerigkeit> spielerinnen = imTeam.stream().filter(z -> z.funktion == Funktion.SPIELERIN).toList();
            spielerinnen.forEach(z -> alleSpielerinnen.add(z.person.id));

            Map<UUID, List<UebersichtDto.Eintrag>> proGruppe = new LinkedHashMap<>();
            List<UebersichtDto.Eintrag> ohneLinie = new ArrayList<>();
            for (Teamzugehoerigkeit z : spielerinnen) {
                UebersichtDto.Eintrag eintrag = new UebersichtDto.Eintrag(personen.get(z.person.id),
                        antwort(antworten.get(z.person.id), kommentareSichtbar || z.person.id.equals(wer.personId()), personen));
                if (z.gruppen.isEmpty()) {
                    ohneLinie.add(eintrag);
                } else {
                    z.gruppen.forEach(g -> proGruppe.computeIfAbsent(g.id, k -> new ArrayList<>()).add(eintrag));
                }
            }
            List<UebersichtDto.Gruppe> gruppen = new ArrayList<>();
            for (Gruppe g : Gruppe.von(Team.findById(team.id()), t.saison)) {
                List<UebersichtDto.Eintrag> eintraege = proGruppe.get(g.id);
                if (eintraege != null && !eintraege.isEmpty()) {
                    gruppen.add(new UebersichtDto.Gruppe(g.id, g.art == GruppenArt.GOALIES
                            ? UebersichtDto.Art.GOALIES : UebersichtDto.Art.LINIE, g.name, sortiert(eintraege)));
                }
            }
            if (!ohneLinie.isEmpty()) {
                gruppen.add(new UebersichtDto.Gruppe(null, UebersichtDto.Art.OHNE_LINIE, OHNE_LINIE, sortiert(ohneLinie)));
            }
            bloecke.add(new UebersichtDto.TeamBlock(team, zaehler(spielerinnen.stream().map(z -> z.person.id).toList(), antworten), gruppen));

            imTeam.stream().filter(z -> z.funktion == Funktion.COACH)
                    .map(z -> new UebersichtDto.CoachEintrag(personen.get(z.person.id), team,
                            antwort(antworten.get(z.person.id), kommentareSichtbar || z.person.id.equals(wer.personId()), personen)))
                    .sorted(Comparator.comparing(UebersichtDto.CoachEintrag::person, PersonKurz.REIHENFOLGE))
                    .forEach(coaches::add);
        }
        return new UebersichtDto(t.id, zaehler(alleSpielerinnen, antworten), bloecke, coaches);
    }

    // ------------------------------------------------------------------ Hilfen

    /** Mitglieder aller beteiligten Teams in der Saison des Termins, mit Person und Gruppen. */
    List<Teamzugehoerigkeit> mitglieder(Termin t) {
        List<UUID> teams = t.teams.stream().map(team -> team.id).toList();
        if (teams.isEmpty()) {
            return List.of();
        }
        return em.createQuery("""
                        select distinct z from Teamzugehoerigkeit z join fetch z.person left join fetch z.gruppen
                        where z.saison.id = :saison and z.team.id in :teams""", Teamzugehoerigkeit.class)
                .setParameter("saison", t.saison.id).setParameter("teams", teams).getResultList();
    }

    /** Funktion einer Person pro beteiligtem Team (leer: in keinem Team des Termins). */
    private static Map<UUID, Funktion> funktionen(List<Teamzugehoerigkeit> mitglieder, UUID person) {
        Map<UUID, Funktion> funktionen = new HashMap<>();
        mitglieder.stream().filter(z -> z.person.id.equals(person)).forEach(z -> funktionen.put(z.team.id, z.funktion));
        return funktionen;
    }

    /** Teams des Termins nach Name, mit dem Namen in der Saison des Termins (SAI-02). */
    private static List<UebersichtDto.TeamName> teamNamen(Termin t) {
        Map<UUID, String> namen = new HashMap<>();
        List<UUID> ids = t.teams.stream().map(team -> team.id).toList();
        if (!ids.isEmpty()) {
            TeamSaison.<TeamSaison>list("saison.id = ?1 and team.id in ?2", t.saison.id, ids)
                    .forEach(ts -> namen.put(ts.team.id, ts.name));
        }
        return ids.stream().map(id -> new UebersichtDto.TeamName(id, namen.getOrDefault(id, "")))
                .sorted(Comparator.comparing(UebersichtDto.TeamName::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(UebersichtDto.TeamName::id))
                .toList();
    }

    Map<UUID, Gespeichert> antworten(UUID terminId) {
        @SuppressWarnings("unchecked")
        List<Object[]> zeilen = em.createNativeQuery("""
                        select person_id, status, kommentar, geaendert_am, eingetragen_von, kurzfristig
                        from antwort where termin_id = ?""")
                .setParameter(1, terminId).getResultList();
        Map<UUID, Gespeichert> antworten = new HashMap<>();
        for (Object[] z : zeilen) {
            antworten.put((UUID) z[0], gespeichert(z, 1));
        }
        return antworten;
    }

    /** Bisherige Antwort, für die Dauer der Transaktion gesperrt (gleichzeitige Wechsel, ANM-10). */
    private Gespeichert gesperrt(UUID terminId, UUID personId) {
        @SuppressWarnings("unchecked")
        List<Object[]> zeilen = em.createNativeQuery("""
                        select person_id, status, kommentar, geaendert_am, eingetragen_von, kurzfristig
                        from antwort where termin_id = ? and person_id = ? for update""")
                .setParameter(1, terminId).setParameter(2, personId).getResultList();
        return zeilen.isEmpty() ? null : gespeichert(zeilen.get(0), 1);
    }

    private static Gespeichert gespeichert(Object[] z, int ab) {
        return new Gespeichert(AntwortStatus.valueOf((String) z[ab]), (String) z[ab + 1], instant(z[ab + 2]),
                (UUID) z[ab + 3], Boolean.TRUE.equals(z[ab + 4]));
    }

    /** Antwort für die Anzeige. Ohne Berechtigung fehlt der Kommentar (ANM-07). */
    static AntwortDto antwort(Gespeichert a, boolean mitKommentar, Map<UUID, PersonKurz> personen) {
        if (a == null) {
            return AntwortDto.OFFEN;
        }
        PersonKurz von = null;
        if (a.eingetragenVon() != null) {
            von = personen.computeIfAbsent(a.eingetragenVon(),
                    id -> Person.<Person>findByIdOptional(id).map(PersonKurz::von).orElse(null));
        }
        return new AntwortDto(a.status(), mitKommentar ? a.kommentar() : null, TerminLeser.zeit(a.geaendertAm()),
                von, a.kurzfristig());
    }

    private static List<UebersichtDto.Eintrag> sortiert(List<UebersichtDto.Eintrag> eintraege) {
        return eintraege.stream().sorted(Comparator.comparing(UebersichtDto.Eintrag::person, PersonKurz.REIHENFOLGE)).toList();
    }

    private static ZaehlerDto zaehler(java.util.Collection<UUID> spielerinnen, Map<UUID, Gespeichert> antworten) {
        return TerminRegeln.zaehlen(spielerinnen.stream()
                .map(p -> antworten.containsKey(p) ? antworten.get(p).status() : AntwortStatus.OFFEN).toList());
    }

    private static Instant instant(Object wert) {
        if (wert instanceof Instant i) {
            return i;
        }
        if (wert instanceof OffsetDateTime o) {
            return o.toInstant();
        }
        if (wert instanceof Timestamp ts) {
            return ts.toInstant();
        }
        return null;
    }
}
