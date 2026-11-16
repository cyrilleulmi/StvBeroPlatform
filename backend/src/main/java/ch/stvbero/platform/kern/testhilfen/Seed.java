package ch.stvbero.platform.kern.testhilfen;

import ch.stvbero.platform.kern.termin.TerminRegeln;
import ch.stvbero.platform.kern.uhr.Uhr;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Der feste Ausgangszustand aus docs/testdaten.md. Nur in den Profilen dev und test.
 * Beim Start und bei {@code POST /api/test/reset} wird die Datenbank geleert und neu befüllt.
 */
@ApplicationScoped
@IfBuildProperty(name = "stv.testhilfen", stringValue = "true")
public class Seed {

    /** Standard-«Jetzt» nach Reset: Montag, 16.11.2026, 06:00 Europe/Zurich. */
    public static final LocalDateTime STANDARD_JETZT = LocalDateTime.of(2026, 11, 16, 6, 0);

    record SeedPerson(int nr, String schluessel, String vorname, String nachname, boolean admin) {
    }

    record SeedSaison(int nr, String name, LocalDate von, LocalDate bis, boolean aktuell) {
    }

    static final List<SeedSaison> SAISONS = List.of(
            new SeedSaison(1, "2025/26", LocalDate.of(2025, 7, 1), LocalDate.of(2026, 6, 30), false),
            new SeedSaison(2, "2026/27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30), true));

    record SeedTeam(int nr, String name, Integer suTeamId) {
    }

    /** Teamzugehörigkeit: Person, Team, Saison (Nummern), Funktion, Gruppe (Nummer, {@code null} = ohne Linie). */
    record SeedZugehoerigkeit(int person, int team, int saison, String funktion, Integer gruppe) {

        SeedZugehoerigkeit(int person, int team, int saison, String funktion) {
            this(person, team, saison, funktion, null);
        }
    }

    /** Linie oder Goalies (docs/testdaten.md, «Gruppen»). Reihenfolge nur bei Linien. */
    record SeedGruppe(int nr, int team, int saison, String art, String name, Integer reihenfolge) {
    }

    private static final String L = "LINIE";
    private static final String G = "GOALIES";

    static final List<SeedGruppe> GRUPPEN = List.of(
            new SeedGruppe(1, 1, 2, L, "Linie 1", 1),
            new SeedGruppe(2, 1, 2, L, "Linie 2", 2),
            new SeedGruppe(3, 1, 2, G, "Goalies", null),
            new SeedGruppe(4, 2, 2, L, "Linie 1", 1),
            new SeedGruppe(5, 2, 2, L, "Linie 2", 2),
            new SeedGruppe(6, 2, 2, G, "Goalies", null),
            new SeedGruppe(7, 3, 2, L, "Linie 1", 1),
            new SeedGruppe(8, 3, 2, G, "Goalies", null),
            // Goalies von Team 1 im Archiv 2025/26 (jedes Team hat pro Saison Goalies, LIN-05)
            new SeedGruppe(9, 1, 1, G, "Goalies", null));

    static final List<SeedTeam> TEAMS = List.of(
            new SeedTeam(1, "Team 1", 900001),
            new SeedTeam(2, "Team 2", null),
            new SeedTeam(3, "Team 3", null));

    private static String teamName(int nr) {
        return TEAMS.stream().filter(t -> t.nr() == nr).findFirst().orElseThrow().name();
    }

    /** Welche Teams in welcher Saison bestehen: Team, Saison. Der Name gilt pro Saison (SAI-02). */
    static final List<int[]> TEAM_SAISONS = List.of(
            new int[]{1, 1},
            new int[]{1, 2}, new int[]{2, 2}, new int[]{3, 2});

    private static final String S = "SPIELERIN";
    private static final String C = "COACH";

    static final List<SeedZugehoerigkeit> ZUGEHOERIGKEITEN = List.of(
            // Saison 2026/27 (aktuell)
            new SeedZugehoerigkeit(2, 1, 2, C), new SeedZugehoerigkeit(2, 2, 2, S, 4),
            new SeedZugehoerigkeit(3, 2, 2, C),
            new SeedZugehoerigkeit(4, 3, 2, C),
            new SeedZugehoerigkeit(5, 1, 2, S, 1),
            new SeedZugehoerigkeit(6, 1, 2, S, 1), new SeedZugehoerigkeit(6, 2, 2, S, 5),
            new SeedZugehoerigkeit(7, 1, 2, S, 1),
            new SeedZugehoerigkeit(8, 1, 2, S, 2),
            new SeedZugehoerigkeit(9, 1, 2, S, 2),
            new SeedZugehoerigkeit(10, 1, 2, S, 3),
            new SeedZugehoerigkeit(11, 1, 2, S),
            new SeedZugehoerigkeit(12, 2, 2, S, 4),
            new SeedZugehoerigkeit(13, 2, 2, S, 4),
            new SeedZugehoerigkeit(14, 2, 2, S, 5),
            new SeedZugehoerigkeit(15, 2, 2, S, 6),
            new SeedZugehoerigkeit(16, 3, 2, S, 7),
            // Saison 2025/26 (Archiv)
            new SeedZugehoerigkeit(17, 1, 1, S));

    static final List<SeedPerson> PERSONEN = List.of(
            new SeedPerson(1, "alex", "Alex", "Admin", true),
            new SeedPerson(2, "petra", "Petra", "Keller", false),
            new SeedPerson(3, "marc", "Marc", "Huber", false),
            new SeedPerson(4, "ruth", "Ruth", "Meier", false),
            new SeedPerson(5, "anna", "Anna", "Muster", false),
            new SeedPerson(6, "lea", "Lea", "Graf", false),
            new SeedPerson(7, "mia", "Mia", "Frei", false),
            new SeedPerson(8, "sara", "Sara", "Brun", false),
            new SeedPerson(9, "nina", "Nina", "Roth", false),
            new SeedPerson(10, "jana", "Jana", "Wyss", false),
            new SeedPerson(11, "kim", "Kim", "Baumann", false),
            new SeedPerson(12, "eva", "Eva", "Steiner", false),
            new SeedPerson(13, "lara", "Lara", "Suter", false),
            new SeedPerson(14, "zoe", "Zoe", "Kaufmann", false),
            new SeedPerson(15, "ella", "Ella", "Fischer", false),
            new SeedPerson(16, "tina", "Tina", "Bucher", false),
            new SeedPerson(17, "vera", "Vera", "Studer", false));

    /** Serie (docs/testdaten.md, «Serien»). Teams und Saison als Nummern. */
    record SeedSerie(int nr, String wochentag, DayOfWeek tag, LocalTime beginn, LocalTime ende, String ort,
                     LocalDate von, LocalDate bis, List<Integer> teams, SeedAusnahme ausnahme) {
    }

    record SeedAusnahme(LocalDate von, LocalDate bis, String grund) {
    }

    private static final String NORD = "Sporthalle Nord";

    static final List<SeedSerie> SERIEN = List.of(
            new SeedSerie(1, "DIENSTAG", DayOfWeek.TUESDAY, LocalTime.of(18, 30), LocalTime.of(20, 0), NORD,
                    LocalDate.of(2026, 8, 18), LocalDate.of(2027, 4, 27), List.of(1, 2),
                    new SeedAusnahme(LocalDate.of(2026, 12, 21), LocalDate.of(2027, 1, 3), "Weihnachtsferien")),
            new SeedSerie(2, "DONNERSTAG", DayOfWeek.THURSDAY, LocalTime.of(19, 0), LocalTime.of(20, 30), NORD,
                    LocalDate.of(2026, 8, 20), LocalDate.of(2027, 4, 29), List.of(1), null),
            new SeedSerie(3, "MITTWOCH", DayOfWeek.WEDNESDAY, LocalTime.of(18, 0), LocalTime.of(19, 30), "Turnhalle Dorf",
                    LocalDate.of(2026, 8, 19), LocalDate.of(2027, 4, 28), List.of(3), null));

    /** Serientermine mit fester ID (docs/testdaten.md, «Termine mit festen IDs»): Serie, Datum → Termin-Nummer. */
    static final Map<String, Integer> FESTE_SERIENTERMINE = Map.of(
            "1:2026-11-17", 1,
            "2:2026-11-19", 2,
            "1:2026-11-24", 3,
            "1:2026-11-10", 7);

    /** Abgesagte Seed-Termine: Nummer → Grund. */
    static final Map<Integer, String> ABGESAGT = Map.of(3, "Halle besetzt");

    /** Antwort im Seed (F-04): Termin-Nummer, Person-Nummer, Status, Kommentar. */
    record SeedAntwort(int termin, int person, String status, String kommentar) {
    }

    /** Zeitpunkt aller Seed-Antworten: vor dem Standard-«Jetzt» und vor jeder Antwortfrist. */
    static final LocalDateTime ANTWORTEN_AM = LocalDateTime.of(2026, 11, 15, 20, 0);

    private static final String D = "DABEI";
    private static final String N = "NICHT_DABEI";
    private static final String V = "VIELLEICHT";

    /** Antworten gemäss docs/testdaten.md. Wer fehlt, ist «offen». */
    static final List<SeedAntwort> ANTWORTEN = List.of(
            // Termin 01: Hauptbeispiel, alle Antwortstatus (Nina und Ella offen)
            new SeedAntwort(1, 5, D, null), new SeedAntwort(1, 6, D, null), new SeedAntwort(1, 7, N, "Krank"),
            new SeedAntwort(1, 8, D, null), new SeedAntwort(1, 10, D, null), new SeedAntwort(1, 11, D, null),
            new SeedAntwort(1, 2, D, null), new SeedAntwort(1, 12, V, "Komme evtl. später"),
            new SeedAntwort(1, 13, D, null), new SeedAntwort(1, 14, N, null), new SeedAntwort(1, 3, N, "Ferien"),
            // Termin 02: nur Anna
            new SeedAntwort(2, 5, D, null),
            // Termin 04: Spieltag Team 1
            new SeedAntwort(4, 5, D, null), new SeedAntwort(4, 6, D, null), new SeedAntwort(4, 7, V, "Muss evtl. arbeiten"),
            new SeedAntwort(4, 8, D, null), new SeedAntwort(4, 10, D, null), new SeedAntwort(4, 11, N, null),
            new SeedAntwort(4, 2, D, null),
            // Termin 05: Spieltag Team 2
            new SeedAntwort(5, 2, D, null), new SeedAntwort(5, 6, D, null), new SeedAntwort(5, 12, D, null),
            new SeedAntwort(5, 13, D, null), new SeedAntwort(5, 14, N, null), new SeedAntwort(5, 15, D, null),
            new SeedAntwort(5, 3, D, null));

    /** Einzeltermin. Ort mit Adresse und Google-Maps-Link optional, Treffpunkt nur bei Spieltagen. */
    record SeedTermin(int nr, int saison, String typ, String titel, LocalDateTime beginn, LocalDateTime ende,
                      String ort, String adresse, String mapsUrl, String hinweise, LocalDateTime treffpunktZeit,
                      String treffpunktOrt, List<Integer> teams) {
    }

    static final List<SeedTermin> EINZELTERMINE = List.of(
            new SeedTermin(4, 2, "SPIELTAG", "Spieltag", LocalDateTime.of(2026, 11, 22, 9, 30),
                    LocalDateTime.of(2026, 11, 22, 13, 0), "Sporthalle Seefeld", "Musterdorf",
                    "https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf",
                    "Weisse Trikots mitnehmen", LocalDateTime.of(2026, 11, 22, 8, 0), "Parkplatz Sporthalle Nord",
                    List.of(1)),
            new SeedTermin(5, 2, "SPIELTAG", "Spieltag", LocalDateTime.of(2026, 11, 28, 14, 0),
                    LocalDateTime.of(2026, 11, 28, 16, 0), "Turnhalle Weid", "Beispielstadt",
                    "https://www.google.com/maps/search/?api=1&query=Turnhalle+Weid+Beispielstadt",
                    null, LocalDateTime.of(2026, 11, 28, 12, 30), NORD, List.of(2)),
            new SeedTermin(6, 2, "ANLASS", "Teamessen", LocalDateTime.of(2026, 12, 11, 19, 0),
                    LocalDateTime.of(2026, 12, 11, 22, 0), null, null, null, null, null, null, List.of(1, 2, 3)),
            new SeedTermin(8, 1, "TRAINING", "Training", LocalDateTime.of(2026, 5, 12, 18, 30),
                    LocalDateTime.of(2026, 5, 12, 20, 0), NORD, null, null, null, null, null, List.of(1)));

    /** Spiel (docs/testdaten.md, Termin 04 und 05): Nummer, Termin, Anspielzeit, Gegner, Heim, SU-Spiel-ID. */
    record SeedSpiel(int nr, int termin, LocalDateTime anspielzeit, String gegner, boolean heim, Integer suSpielId) {
    }

    static final List<SeedSpiel> SPIELE = List.of(
            new SeedSpiel(1, 4, LocalDateTime.of(2026, 11, 22, 9, 30), "UHC Musterdorf", true, 5000001),
            new SeedSpiel(2, 4, LocalDateTime.of(2026, 11, 22, 11, 45), "Unihockey Beispielstadt", false, 5000002),
            new SeedSpiel(3, 5, LocalDateTime.of(2026, 11, 28, 14, 0), "UH Testingen", true, null));

    /** Aufgebot (F-05): Termin, aufgebotene Personen, veröffentlicht am ({@code null} = Entwurf). */
    record SeedAufgebot(int termin, List<Integer> personen, LocalDateTime veroeffentlichtAm) {
    }

    static final List<SeedAufgebot> AUFGEBOTE = List.of(
            // Termin 04: Entwurf mit Anna, Lea, Mia, Sara, Jana
            new SeedAufgebot(4, List.of(5, 6, 7, 8, 10), null),
            // Termin 05: veröffentlicht mit Petra, Eva, Lara, Ella (Lea nicht aufgeboten)
            new SeedAufgebot(5, List.of(2, 12, 13, 15), LocalDateTime.of(2026, 11, 15, 21, 0)));

    @Inject
    EntityManager em;

    @Inject
    Uhr uhr;

    void beimStart(@Observes StartupEvent ereignis) {
        zuruecksetzen();
    }

    /** Leert alle Tabellen (ausser der Flyway-Historie), befüllt den Seed und setzt die Uhr auf Standard. */
    @Transactional
    public void zuruecksetzen() {
        @SuppressWarnings("unchecked")
        List<String> tabellen = em.createNativeQuery("""
                select tablename from pg_tables
                where schemaname = current_schema() and tablename <> 'flyway_schema_history'
                """).getResultList();
        if (!tabellen.isEmpty()) {
            em.createNativeQuery("truncate table " + String.join(", ", tabellen) + " cascade").executeUpdate();
        }

        for (SeedSaison s : SAISONS) {
            em.createNativeQuery("insert into saison (id, name, von, bis, aktuell) values (?, ?, ?, ?, ?)")
                    .setParameter(1, saisonId(s.nr())).setParameter(2, s.name()).setParameter(3, s.von())
                    .setParameter(4, s.bis()).setParameter(5, s.aktuell())
                    .executeUpdate();
        }
        for (SeedPerson p : PERSONEN) {
            em.createNativeQuery("insert into person (id, vorname, nachname, email, admin) values (?, ?, ?, ?, ?)")
                    .setParameter(1, personId(p.nr())).setParameter(2, p.vorname()).setParameter(3, p.nachname())
                    .setParameter(4, p.schluessel() + "@example.ch").setParameter(5, p.admin())
                    .executeUpdate();
            em.createNativeQuery("insert into konto (id, person_id, schluessel) values (?, ?, ?)")
                    .setParameter(1, kontoId(p.nr())).setParameter(2, personId(p.nr())).setParameter(3, p.schluessel())
                    .executeUpdate();
        }
        for (SeedTeam t : TEAMS) {
            em.createNativeQuery("insert into team (id) values (?)")
                    .setParameter(1, teamId(t.nr()))
                    .executeUpdate();
            if (t.suTeamId() != null) {
                em.createNativeQuery("update team set su_team_id = ? where id = ?")
                        .setParameter(1, t.suTeamId()).setParameter(2, teamId(t.nr()))
                        .executeUpdate();
            }
        }
        for (int[] ts : TEAM_SAISONS) {
            em.createNativeQuery("insert into team_saison (team_id, saison_id, name) values (?, ?, ?)")
                    .setParameter(1, teamId(ts[0])).setParameter(2, saisonId(ts[1])).setParameter(3, teamName(ts[0]))
                    .executeUpdate();
        }
        for (SeedGruppe g : GRUPPEN) {
            em.createNativeQuery("insert into gruppe (id, team_id, saison_id, art, name, reihenfolge) values (?, ?, ?, ?, ?, "
                            + (g.reihenfolge() == null ? "null" : g.reihenfolge()) + ")")
                    .setParameter(1, gruppeId(g.nr())).setParameter(2, teamId(g.team()))
                    .setParameter(3, saisonId(g.saison())).setParameter(4, g.art()).setParameter(5, g.name())
                    .executeUpdate();
        }
        for (SeedZugehoerigkeit z : ZUGEHOERIGKEITEN) {
            UUID id = UUID.nameUUIDFromBytes(("tz-" + z.person() + "-" + z.team() + "-" + z.saison()).getBytes());
            em.createNativeQuery("insert into teamzugehoerigkeit (id, person_id, team_id, saison_id, funktion) values (?, ?, ?, ?, ?)")
                    .setParameter(1, id)
                    .setParameter(2, personId(z.person())).setParameter(3, teamId(z.team()))
                    .setParameter(4, saisonId(z.saison())).setParameter(5, z.funktion())
                    .executeUpdate();
            if (z.gruppe() != null) {
                em.createNativeQuery("insert into gruppenzuweisung (teamzugehoerigkeit_id, gruppe_id) values (?, ?)")
                        .setParameter(1, id).setParameter(2, gruppeId(z.gruppe()))
                        .executeUpdate();
            }
        }
        serienUndTermine();
        for (SeedAntwort a : ANTWORTEN) {
            em.createNativeQuery("""
                            insert into antwort (termin_id, person_id, status, kommentar, geaendert_am, kurzfristig)
                            values (?, ?, ?, ?, ?, false)""")
                    .setParameter(1, terminId(a.termin())).setParameter(2, personId(a.person()))
                    .setParameter(3, a.status()).setParameter(4, a.kommentar()).setParameter(5, zurich(ANTWORTEN_AM))
                    .executeUpdate();
        }
        spieleUndAufgebote();
        em.clear();

        uhr.festsetzen(STANDARD_JETZT.atZone(Uhr.ZONE).toInstant());
    }

    /** Serien mit allen ihren Terminen (auch vergangenen) und die Einzeltermine (F-03). */
    private void serienUndTermine() {
        for (SeedSerie s : SERIEN) {
            em.createNativeQuery("""
                            insert into serie (id, saison_id, typ, titel, wochentag, beginn_zeit, ende_zeit, gueltig_von,
                                               gueltig_bis, ort_name, antwortfrist_stunden)
                            values (?, ?, 'TRAINING', 'Training', ?, ?, ?, ?, ?, ?, ?)""")
                    .setParameter(1, serieId(s.nr())).setParameter(2, saisonId(2)).setParameter(3, s.wochentag())
                    .setParameter(4, s.beginn()).setParameter(5, s.ende()).setParameter(6, s.von())
                    .setParameter(7, s.bis()).setParameter(8, s.ort())
                    .setParameter(9, TerminRegeln.ANTWORTFRIST_STANDARD)
                    .executeUpdate();
            for (int team : s.teams()) {
                em.createNativeQuery("insert into serie_team (serie_id, team_id) values (?, ?)")
                        .setParameter(1, serieId(s.nr())).setParameter(2, teamId(team)).executeUpdate();
            }
            List<TerminRegeln.Zeitraum> ausnahmen = List.of();
            if (s.ausnahme() != null) {
                SeedAusnahme a = s.ausnahme();
                em.createNativeQuery("insert into ausnahme (id, serie_id, von, bis, grund) values (?, ?, ?, ?, ?)")
                        .setParameter(1, UUID.nameUUIDFromBytes(("ausnahme-" + s.nr()).getBytes()))
                        .setParameter(2, serieId(s.nr())).setParameter(3, a.von()).setParameter(4, a.bis())
                        .setParameter(5, a.grund()).executeUpdate();
                ausnahmen = List.of(new TerminRegeln.Zeitraum(a.von(), a.bis()));
            }
            for (LocalDate tag : TerminRegeln.daten(s.tag(), s.von(), s.bis(), ausnahmen)) {
                Integer nr = FESTE_SERIENTERMINE.get(s.nr() + ":" + tag);
                UUID id = nr != null ? terminId(nr) : UUID.nameUUIDFromBytes(("termin-" + s.nr() + "-" + tag).getBytes());
                em.createNativeQuery("""
                                insert into termin (id, saison_id, typ, titel, beginn, ende, status, absagegrund, ort_name,
                                                    antwortfrist_stunden, serie_id, serie_datum)
                                values (?, ?, 'TRAINING', 'Training', ?, ?, ?, ?, ?, ?, ?, ?)""")
                        .setParameter(1, id).setParameter(2, saisonId(2))
                        .setParameter(3, TerminRegeln.zeitpunkt(tag, s.beginn()))
                        .setParameter(4, TerminRegeln.zeitpunkt(tag, s.ende()))
                        .setParameter(5, nr != null && ABGESAGT.containsKey(nr) ? "ABGESAGT" : "GEPLANT")
                        .setParameter(6, nr != null ? ABGESAGT.get(nr) : null)
                        .setParameter(7, s.ort()).setParameter(8, TerminRegeln.ANTWORTFRIST_STANDARD)
                        .setParameter(9, serieId(s.nr())).setParameter(10, tag)
                        .executeUpdate();
                teamsZuordnen(id, s.teams());
            }
        }
        for (SeedTermin t : EINZELTERMINE) {
            em.createNativeQuery("""
                            insert into termin (id, saison_id, typ, titel, beginn, ende, status, ort_name, ort_adresse,
                                                ort_maps_url, hinweise, antwortfrist_stunden, treffpunkt_zeit, treffpunkt_ort)
                            values (?, ?, ?, ?, ?, ?, 'GEPLANT', ?, ?, ?, ?, ?, ?, ?)""")
                    .setParameter(1, terminId(t.nr())).setParameter(2, saisonId(t.saison()))
                    .setParameter(3, t.typ()).setParameter(4, t.titel())
                    .setParameter(5, zurich(t.beginn())).setParameter(6, zurich(t.ende()))
                    .setParameter(7, t.ort()).setParameter(8, t.adresse()).setParameter(9, t.mapsUrl())
                    .setParameter(10, t.hinweise()).setParameter(11, TerminRegeln.ANTWORTFRIST_STANDARD)
                    .setParameter(12, t.treffpunktZeit() == null ? null : zurich(t.treffpunktZeit()))
                    .setParameter(13, t.treffpunktOrt())
                    .executeUpdate();
            teamsZuordnen(terminId(t.nr()), t.teams());
        }
    }

    /** Spiele und Aufgebote der Spieltage 04 und 05 (F-05). */
    private void spieleUndAufgebote() {
        for (SeedSpiel sp : SPIELE) {
            em.createNativeQuery("""
                            insert into spiel (id, termin_id, gegner, anspielzeit, heim, quelle, su_spiel_id)
                            values (?, ?, ?, ?, ?, ?, ?)""")
                    .setParameter(1, spielId(sp.nr())).setParameter(2, terminId(sp.termin()))
                    .setParameter(3, sp.gegner()).setParameter(4, zurich(sp.anspielzeit())).setParameter(5, sp.heim())
                    .setParameter(6, sp.suSpielId() == null ? "MANUELL" : "SWISS_UNIHOCKEY")
                    .setParameter(7, sp.suSpielId())
                    .executeUpdate();
        }
        for (SeedAufgebot a : AUFGEBOTE) {
            em.createNativeQuery("insert into aufgebot (termin_id, veroeffentlicht_am) values (?, ?)")
                    .setParameter(1, terminId(a.termin()))
                    .setParameter(2, a.veroeffentlichtAm() == null ? null : zurich(a.veroeffentlichtAm()))
                    .executeUpdate();
            for (int person : a.personen()) {
                em.createNativeQuery("insert into aufgebot_eintrag (termin_id, person_id) values (?, ?)")
                        .setParameter(1, terminId(a.termin())).setParameter(2, personId(person)).executeUpdate();
            }
        }
    }

    private void teamsZuordnen(UUID terminId, List<Integer> teams) {
        for (int team : teams) {
            em.createNativeQuery("insert into termin_team (termin_id, team_id) values (?, ?)")
                    .setParameter(1, terminId).setParameter(2, teamId(team)).executeUpdate();
        }
    }

    private static Instant zurich(LocalDateTime zeit) {
        return zeit.atZone(Uhr.ZONE).toInstant();
    }

    public static UUID teamId(int nr) {
        return id("1", nr);
    }

    public static UUID personId(int nr) {
        return id("2", nr);
    }

    public static UUID gruppeId(int nr) {
        return id("3", nr);
    }

    public static UUID terminId(int nr) {
        return id("4", nr);
    }

    public static UUID spielId(int nr) {
        return id("5", nr);
    }

    public static UUID serieId(int nr) {
        return id("7", nr);
    }

    public static UUID saisonId(int nr) {
        return id("6", nr);
    }

    public static UUID kontoId(int nr) {
        return id("8", nr);
    }

    private static UUID id(String typ, int nr) {
        return UUID.fromString("%s0000000-0000-4000-8000-%012d".formatted(typ, nr));
    }
}
