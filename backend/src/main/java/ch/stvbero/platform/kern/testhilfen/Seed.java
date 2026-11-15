package ch.stvbero.platform.kern.testhilfen;

import ch.stvbero.platform.kern.uhr.Uhr;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
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
        em.clear();

        uhr.festsetzen(STANDARD_JETZT.atZone(Uhr.ZONE).toInstant());
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
