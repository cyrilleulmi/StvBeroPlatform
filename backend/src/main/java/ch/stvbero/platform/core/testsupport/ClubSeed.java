package ch.stvbero.platform.core.testsupport;

import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.event.EventRules;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The club data set (docs/test-data.md, club data set): the unihockey section of STV Beromünster with its ten
 * real teams as swiss unihockey lists them for «Eintracht Beromünster» (club 417058), and invented people. Three
 * teams are linked with swiss unihockey, the others are left open to try out linking; {@link ClubLoader} then
 * loads their matches from the real interface. Only in the profiles dev and test.
 * <p>
 * Unlike the test data set ({@link Seed}), the clock runs: the data is laid out around the real «now» (practices
 * every week, hand-entered match days of «Herren KF 4. Liga II» before and after today).
 * <p>
 * People are never taken from swiss unihockey: all names are invented.
 */
@ApplicationScoped
@IfBuildProperty(name = "stv.test-support", stringValue = "true")
public class ClubSeed {

    /** Where the club is at home (home hall at swiss unihockey): usual meeting point of every team (AUF-32). */
    public static final String HOME = "Oberstufenschulhaus Beromünster";
    static final String HOME_HALL = "Oberstufenschulhaus";
    static final String HOME_TOWN = "Beromünster";
    static final double HOME_LATITUDE = 47.20666833;
    static final double HOME_LONGITUDE = 8.188356;

    static final LocalDate ARCHIVE_START = LocalDate.of(2025, 6, 1);
    static final LocalDate SEASON_START = LocalDate.of(2026, 6, 1);
    static final LocalDate SEASON_END = LocalDate.of(2027, 5, 31);
    static final LocalDate PRACTICE_FROM = LocalDate.of(2026, 8, 17);
    static final LocalDate PRACTICE_UNTIL = LocalDate.of(2027, 4, 30);

    /**
     * A team of the club.
     *
     * @param suTeamId team ID at swiss unihockey (season 2026/27)
     * @param linked   linked in the data set; the others are left open
     */
    public record ClubTeam(int nr, String name, int suTeamId, boolean linked, boolean adults, boolean women) {
    }

    public static final List<ClubTeam> TEAMS = List.of(
            new ClubTeam(1, "Herren KF 3. Liga", 428216, true, true, false),
            new ClubTeam(2, "Herren KF 4. Liga II", 428649, false, true, false),
            new ClubTeam(3, "Damen KF 1. Liga", 428361, true, true, true),
            new ClubTeam(4, "Damen KF 3. Liga II", 432100, false, true, true),
            new ClubTeam(5, "Junioren A", 428055, true, false, false),
            new ClubTeam(6, "Junioren B", 428960, false, false, false),
            new ClubTeam(7, "Junioren C", 428603, false, false, false),
            new ClubTeam(8, "Junioren D", 422353, false, false, false),
            new ClubTeam(9, "Junioren E", 432685, false, false, false),
            new ClubTeam(10, "Juniorinnen B", 430452, false, false, true));

    /** Team with the hand-entered match days: works without swiss unihockey (SUH-10). */
    public static final int MANUAL_TEAM = 2;

    private record Staff(int nr, String firstName, String lastName, boolean admin, List<Integer> coachOf) {
    }

    /** Admin and coaches. Reto, Thomas and Daniela coach two teams; Petra also plays in «Damen KF 3. Liga II». */
    private static final List<Staff> STAFF = List.of(
            new Staff(1, "Alex", "Admin", true, List.of()),
            new Staff(2, "Reto", "Amrein", false, List.of(1, 2)),
            new Staff(3, "Bruno", "Felder", false, List.of(2)),
            new Staff(4, "Petra", "Keller", false, List.of(3)),
            new Staff(5, "Monika", "Stalder", false, List.of(4)),
            new Staff(6, "Marc", "Huber", false, List.of(5)),
            new Staff(7, "Ruth", "Meier", false, List.of(6)),
            new Staff(8, "Thomas", "Egli", false, List.of(7, 8)),
            new Staff(9, "Daniela", "Koch", false, List.of(8, 9)),
            new Staff(10, "Nadia", "Lang", false, List.of(10)));

    private static final int PETRA = 4;

    private static final List<String> MEN = List.of(
            "Luca", "Noah", "Jan", "Tim", "Nico", "Fabian", "Simon", "David", "Kevin", "Lukas", "Jonas", "Pascal",
            "Michael", "Stefan", "Andreas", "Patrick", "Dominik", "Samuel", "Elias", "Raphael", "Silvan", "Florian",
            "Manuel", "Cedric", "Joel", "Robin", "Yannick", "Severin", "Livio", "Matteo", "Levin", "Nils", "Julian",
            "Leon", "Finn", "Gian", "Mauro", "Remo", "Sven", "Timo", "Kilian", "Lars", "Maurice", "Nando", "Oliver",
            "Philipp", "Roman", "Sandro", "Tobias", "Urs", "Valentin", "Yves", "Adrian", "Benjamin", "Christian",
            "Damian", "Emil", "Felix", "Gabriel", "Hannes", "Ivan", "Jannis", "Kai", "Lorenz");

    private static final List<String> WOMEN = List.of(
            "Anna", "Lea", "Mia", "Sara", "Nina", "Jana", "Kim", "Eva", "Lara", "Zoe", "Ella", "Tina", "Vera",
            "Laura", "Julia", "Selina", "Michelle", "Jasmin", "Livia", "Alina", "Chiara", "Elena", "Fiona", "Giulia",
            "Hanna", "Ines", "Joana", "Katja", "Lena", "Melanie", "Noemi", "Olivia");

    private static final List<String> LAST_NAMES = List.of(
            "Muster", "Graf", "Frei", "Brun", "Roth", "Wyss", "Baumann", "Steiner", "Suter", "Kaufmann", "Fischer",
            "Bucher", "Studer", "Arnold", "Bachmann", "Bieri", "Bühler", "Christen", "Estermann", "Fellmann", "Gisler",
            "Häfliger", "Imfeld", "Jost", "Kurmann", "Lustenberger", "Marti", "Niederberger", "Odermatt", "Portmann",
            "Rölli", "Schmid", "Troxler", "Ulrich", "Vogel", "Wicki", "Zemp", "Achermann", "Bossert", "Dubach",
            "Emmenegger", "Fuchs", "Galliker");

    /** Player: person number {@code team * 100 + index}. */
    private record Player(int nr, int team, int index, String firstName, String lastName) {
    }

    /**
     * Practice series: teams train once or twice a week; series 1 and 4 are shared by two teams. {@code optional}:
     * teams that are invited without obligation («freiwillig», TER-15).
     */
    private record ClubSeries(int nr, DayOfWeek day, LocalTime start, LocalTime end, List<Integer> teams,
                              List<Integer> optional) {

        ClubSeries(int nr, DayOfWeek day, LocalTime start, LocalTime end, List<Integer> teams) {
            this(nr, day, start, end, teams, List.of());
        }
    }

    /** Series whose next practice invites its second team without obligation, as a single changed event (TER-15). */
    private static final int ONE_OPTIONAL_PRACTICE = 1;

    private static final List<ClubSeries> SERIES = List.of(
            new ClubSeries(1, DayOfWeek.TUESDAY, LocalTime.of(20, 0), LocalTime.of(21, 30), List.of(1, 2)),
            new ClubSeries(2, DayOfWeek.THURSDAY, LocalTime.of(20, 0), LocalTime.of(21, 30), List.of(1)),
            new ClubSeries(3, DayOfWeek.MONDAY, LocalTime.of(19, 30), LocalTime.of(21, 0), List.of(3)),
            // Thursday: the first women's team is expected, the second one is welcome to join
            new ClubSeries(4, DayOfWeek.THURSDAY, LocalTime.of(19, 0), LocalTime.of(20, 30), List.of(3, 4), List.of(4)),
            new ClubSeries(5, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(19, 30), List.of(5)),
            new ClubSeries(6, DayOfWeek.TUESDAY, LocalTime.of(18, 0), LocalTime.of(19, 30), List.of(6)),
            new ClubSeries(7, DayOfWeek.WEDNESDAY, LocalTime.of(17, 30), LocalTime.of(19, 0), List.of(7)),
            new ClubSeries(8, DayOfWeek.THURSDAY, LocalTime.of(17, 30), LocalTime.of(19, 0), List.of(8)),
            new ClubSeries(9, DayOfWeek.FRIDAY, LocalTime.of(17, 30), LocalTime.of(18, 45), List.of(9)),
            new ClubSeries(10, DayOfWeek.FRIDAY, LocalTime.of(18, 45), LocalTime.of(20, 15), List.of(10)));

    /** Series whose practices record attendance (PRS-01), so that the statistics have data (F-15). */
    private static final Set<Integer> ATTENDANCE_CHECK = Set.of(2, 3);

    /**
     * Hand-entered match day of «Herren KF 4. Liga II»: Sunday {@code weeks} from now, two matches. Halls and
     * coordinates are real venues of the league; opponents and results are invented.
     */
    private record ManualMatchDay(int nr, int weeks, String hall, String town, double latitude, double longitude,
                                  LocalTime first, LocalTime second, String opponent1, String opponent2,
                                  String result1, String result2) {
    }

    private static final List<ManualMatchDay> MANUAL_MATCH_DAYS = List.of(
            new ManualMatchDay(1, -9, "Sporthalle Hofstatt", "Kaisten", 47.54083252, 8.03194987,
                    LocalTime.of(15, 25), LocalTime.of(17, 15), "UHC Musterdorf", "Unihockey Beispielstadt", "4:6", "7:3"),
            new ManualMatchDay(2, -6, "Kalofenhalle", "Grosswangen", 47.12966333, 8.04942467,
                    LocalTime.of(14, 30), LocalTime.of(16, 20), "Floorball Seetal", "UHC Waldberg", "5:5", "2:8"),
            new ManualMatchDay(3, -3, HOME_HALL, HOME_TOWN, HOME_LATITUDE, HOME_LONGITUDE,
                    LocalTime.of(9, 0), LocalTime.of(10, 50), "Unihockey Bergdorf", "STV Hügelwil", "9:4", "6:5"),
            new ManualMatchDay(4, -1, "Suhrenmatte", "Buchs AG", 47.39366667, 8.076,
                    LocalTime.of(12, 40), LocalTime.of(15, 25), "UH Talmatt", "UHC Musterdorf", "3:7", "8:8"),
            new ManualMatchDay(5, 1, "MZH Walterswil", "Walterswil", 47.328765, 7.969089,
                    LocalTime.of(9, 0), LocalTime.of(11, 45), "Unihockey Beispielstadt", "Floorball Seetal", null, null),
            new ManualMatchDay(6, 4, "3fach Turnhalle Morgenweg", "Schötz", 47.171348, 7.99079,
                    LocalTime.of(14, 30), LocalTime.of(17, 15), "UHC Waldberg", "Unihockey Bergdorf", null, null),
            new ManualMatchDay(7, 7, "SICKINGA-Sporthallen", "Untersiggenthal", 47.4992115, 8.25802317,
                    LocalTime.of(10, 50), LocalTime.of(13, 35), "STV Hügelwil", "UH Talmatt", null, null),
            new ManualMatchDay(8, 10, "Färberacker", "Unterkulm", 47.31217, 8.11708367,
                    LocalTime.of(14, 30), LocalTime.of(16, 20), "UHC Musterdorf", "Unihockey Beispielstadt", null, null));

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    /** Empties all tables (except the Flyway history), fills in the club and lets the clock run. */
    @Transactional
    public void reset() {
        @SuppressWarnings("unchecked")
        List<String> tables = em.createNativeQuery("""
                select tablename from pg_tables
                where schemaname = current_schema() and tablename <> 'flyway_schema_history'
                """).getResultList();
        if (!tables.isEmpty()) {
            em.createNativeQuery("truncate table " + String.join(", ", tables) + " cascade").executeUpdate();
        }
        clock.release();
        Instant now = clock.now();
        LocalDate today = EventRules.date(now);

        sql("insert into season (id, name, start_date, end_date, is_current) values (?, ?, ?, ?, false)",
                Seed.seasonId(1), "2025/26", ARCHIVE_START, SEASON_START.minusDays(1));
        sql("insert into season (id, name, start_date, end_date, is_current) values (?, ?, ?, ?, true)",
                Seed.seasonId(2), "2026/27", SEASON_START, SEASON_END);

        List<Player> players = players();
        Set<String> keys = new HashSet<>();
        for (Staff s : STAFF) {
            person(s.nr(), s.firstName(), s.lastName(), s.admin(), keys);
        }
        for (Player p : players) {
            person(p.nr(), p.firstName(), p.lastName(), false, keys);
        }

        for (ClubTeam team : TEAMS) {
            sql("insert into team (id, meeting_point_location) values (?, ?)", Seed.teamId(team.nr()), HOME);
            sql("insert into team_season (team_id, season_id, name) values (?, ?, ?)", Seed.teamId(team.nr()),
                    Seed.seasonId(2), team.name());
            group(team.nr() * 10 + 1, team.nr(), 2, "LINE", "Linie 1", 1);
            group(team.nr() * 10 + 2, team.nr(), 2, "LINE", "Linie 2", 2);
            group(team.nr() * 10 + 3, team.nr(), 2, "GOALIES", "Goalies", null);
            if (team.linked()) {
                sql("insert into su_link (id, team_id, season_id, su_team_id, su_team_name, created_at) values (?, ?, ?, ?, ?, ?)",
                        Seed.suLinkId(team.nr()), Seed.teamId(team.nr()), Seed.seasonId(2), team.suTeamId(),
                        team.name(), now);
            }
        }
        // Standard event variants (VAR-01). Only the first men's team makes a call-up, all others work with the
        // responses alone (VAR-11).
        em.createNativeQuery(ch.stvbero.platform.core.variant.VariantRules.ENSURE_STANDARD_SQL).executeUpdate();
        sql("update event_variant set call_up = true where event_type = 'MATCH_DAY' and team_id = ?", Seed.teamId(1));
        // Archive 2025/26: the two first teams existed already; the women played one league lower and were linked
        // with another team at swiss unihockey (the link changes from season to season, SUH-11).
        sql("insert into team_season (team_id, season_id, name) values (?, ?, ?)", Seed.teamId(1), Seed.seasonId(1),
                "Herren KF 3. Liga");
        sql("insert into team_season (team_id, season_id, name) values (?, ?, ?)", Seed.teamId(3), Seed.seasonId(1),
                "Damen KF 2. Liga");
        group(19, 1, 1, "GOALIES", "Goalies", null);
        group(39, 3, 1, "GOALIES", "Goalies", null);
        sql("insert into su_link (id, team_id, season_id, su_team_id, su_team_name, created_at) values (?, ?, ?, ?, ?, ?)",
                Seed.suLinkId(103), Seed.teamId(3), Seed.seasonId(1), 419730, "Damen KF 2. Liga",
                zurich(ARCHIVE_START.atStartOfDay()));

        // Coaches
        for (Staff s : STAFF) {
            for (int team : s.coachOf()) {
                membership(s.nr(), team, 2, "COACH", null);
            }
        }
        membership(2, 1, 1, "COACH", null);
        membership(PETRA, 3, 1, "COACH", null);
        // Petra also plays in the second women's team (dual role)
        membership(PETRA, 4, 2, "PLAYER", 41);
        // Players: adults Linie 1 (4), Linie 2 (4), Goalies (2), without line (2); juniors 3, 3, 1, 1
        for (Player p : players) {
            ClubTeam team = TEAMS.get(p.team() - 1);
            membership(p.nr(), p.team(), 2, "PLAYER", groupOf(team, p.index()));
            if ((p.team() == 1 || p.team() == 3) && p.index() <= 6) {
                membership(p.nr(), p.team(), 1, "PLAYER", null);
            }
        }
        // Two players of the first men's team help out in the second one
        membership(101, 2, 2, "PLAYER", null);
        membership(102, 2, 2, "PLAYER", null);

        // Driving (DRV-18): juniors do not drive; two adults have a bigger and a smaller car than the default of 5
        for (ClubTeam team : TEAMS) {
            if (!team.adults()) {
                sql("""
                        update person set can_drive = false where id in (
                            select person_id from team_membership where team_id = ? and team_role = 'PLAYER')""",
                        Seed.teamId(team.nr()));
            }
        }
        sql("update person set car_seats = 7 where id = ?", Seed.personId(103));
        sql("update person set car_seats = 4 where id = ?", Seed.personId(304));
        sql("update person set can_drive = false where id = ?", Seed.personId(305));

        // Special line-ups (LIN-13): power play and penalty kill of the two first teams (small field: 3 against 2)
        for (int team : new int[] {1, 3}) {
            int base = team * 100;
            specialLineup(team * 10 + 1, team, 1, "Überzahl 1", new String[] {"Center", "Flügel links", "Flügel rechts"},
                    new Integer[] {base + 1, base + 2, base + 3});
            specialLineup(team * 10 + 2, team, 2, "Überzahl 2", new String[] {"Center", "Flügel links", "Flügel rechts"},
                    new Integer[] {base + 5, base + 6, null});
            specialLineup(team * 10 + 3, team, 3, "Unterzahl", new String[] {null, null},
                    new Integer[] {base + 4, base + 7});
        }

        Map<Integer, List<Integer>> teamPlayers = new LinkedHashMap<>();
        players.forEach(p -> teamPlayers.computeIfAbsent(p.team(), k -> new ArrayList<>()).add(p.nr()));
        teamPlayers.get(2).addAll(List.of(101, 102));
        teamPlayers.get(4).add(PETRA);

        practices(now, today, teamPlayers);
        manualMatchDays(now, today, teamPlayers.get(MANUAL_TEAM));
        clubEvent(today);

        em.flush();
        em.clear();
    }

    // ------------------------------------------------------------------ People

    private static List<Player> players() {
        List<Player> players = new ArrayList<>();
        int men = 0;
        int women = 0;
        int surname = 0;
        for (ClubTeam team : TEAMS) {
            int size = team.adults() ? 12 : 8;
            for (int i = 1; i <= size; i++) {
                String first = team.women() ? WOMEN.get(women++ % WOMEN.size()) : MEN.get(men++ % MEN.size());
                String last = LAST_NAMES.get(surname % LAST_NAMES.size());
                surname += 7;
                players.add(new Player(team.nr() * 100 + i, team.nr(), i, first, last));
            }
        }
        return players;
    }

    private void person(int nr, String firstName, String lastName, boolean admin, Set<String> keys) {
        String base = key(firstName);
        String key = base;
        for (int i = 2; !keys.add(key); i++) {
            key = base + i;
        }
        sql("insert into person (id, first_name, last_name, email, admin) values (?, ?, ?, ?, ?)",
                Seed.personId(nr), firstName, lastName, key + "@example.ch", admin);
        sql("insert into account (id, person_id, login_key) values (?, ?, ?)", Seed.accountId(nr),
                Seed.personId(nr), key);
    }

    /** Login key of a person: the first name in lower case without accents, e.g. «zoe», «joel». */
    static String key(String firstName) {
        return Normalizer.normalize(firstName, Normalizer.Form.NFD).replaceAll("[^A-Za-z]", "")
                .toLowerCase(Locale.ROOT);
    }

    private static Integer groupOf(ClubTeam team, int index) {
        int base = team.nr() * 10;
        if (team.adults()) {
            return index <= 4 ? Integer.valueOf(base + 1) : index <= 8 ? Integer.valueOf(base + 2)
                    : index <= 10 ? Integer.valueOf(base + 3) : null;
        }
        return index <= 3 ? Integer.valueOf(base + 1) : index <= 6 ? Integer.valueOf(base + 2)
                : index == 7 ? Integer.valueOf(base + 3) : null;
    }

    private void group(int nr, int team, int season, String type, String name, Integer sortOrder) {
        sql("insert into line_group (id, team_id, season_id, type, name, sort_order) values (?, ?, ?, ?, ?, "
                        + (sortOrder == null ? "null" : sortOrder) + ")",
                Seed.groupId(nr), Seed.teamId(team), Seed.seasonId(season), type, name);
    }

    /** A special line-up in the current season; {@code null} leaves a place empty or without a label. */
    private void specialLineup(int nr, int team, int order, String name, String[] labels, Integer[] persons) {
        UUID id = UUID.nameUUIDFromBytes(("lineup-" + nr).getBytes());
        sql("insert into special_lineup (id, team_id, season_id, name, sort_order, created_at) values (?, ?, ?, ?, ?, ?)",
                id, Seed.teamId(team), Seed.seasonId(2), name, order, clock.now());
        for (int i = 0; i < labels.length; i++) {
            em.createNativeQuery("""
                            insert into special_lineup_place (lineup_id, position, label, person_id)
                            values (?, ?, cast(? as varchar), cast(? as uuid))""")
                    .setParameter(1, id).setParameter(2, i + 1).setParameter(3, labels[i])
                    .setParameter(4, persons[i] == null ? null : Seed.personId(persons[i]).toString())
                    .executeUpdate();
        }
    }

    private void membership(int person, int team, int season, String teamRole, Integer group) {
        UUID id = UUID.nameUUIDFromBytes(("tz-" + person + "-" + team + "-" + season).getBytes());
        sql("""
                insert into team_membership (id, person_id, team_id, season_id, team_role, joined_on)
                values (?, ?, ?, ?, ?, ?)""", id, Seed.personId(person), Seed.teamId(team), Seed.seasonId(season),
                teamRole, season == 1 ? ARCHIVE_START : SEASON_START);
        if (group != null) {
            sql("insert into group_assignment (team_membership_id, group_id) values (?, ?)", id, Seed.groupId(group));
        }
    }

    // ------------------------------------------------------------------ Practices

    /**
     * Weekly practices from 17.08.2026 to 30.04.2027 without the Christmas break, with responses for the practices
     * of the last four and the next two weeks, and recorded attendance for the past practices of two series.
     */
    private void practices(Instant now, LocalDate today, Map<Integer, List<Integer>> teamPlayers) {
        LocalDate breakStart = LocalDate.of(2026, 12, 21);
        LocalDate breakEnd = LocalDate.of(2027, 1, 3);
        for (ClubSeries s : SERIES) {
            UUID series = Seed.seriesId(s.nr());
            sql("""
                    insert into series (id, season_id, type, title, weekday, start_time, end_time, valid_from,
                                       valid_until, location_name, response_deadline_hours)
                    values (?, ?, 'PRACTICE', 'Training', ?, ?, ?, ?, ?, ?, ?)""", series, Seed.seasonId(2),
                    s.day().name(), s.start(), s.end(), PRACTICE_FROM, PRACTICE_UNTIL, HOME,
                    EventRules.DEFAULT_RESPONSE_DEADLINE);
            for (int team : s.teams()) {
                sql("insert into series_team (series_id, team_id) values (?, ?)", series, Seed.teamId(team));
            }
            for (int team : s.optional()) {
                sql("insert into series_optional_team (series_id, team_id) values (?, ?)", series, Seed.teamId(team));
            }
            boolean oneOptionalDone = false;
            sql("insert into series_exception (id, series_id, start_date, end_date, reason) values (?, ?, ?, ?, ?)",
                    UUID.nameUUIDFromBytes(("ausnahme-" + s.nr()).getBytes()), series, breakStart, breakEnd,
                    "Weihnachtsferien");
            List<Integer> coaches = STAFF.stream().filter(staff -> staff.coachOf().stream().anyMatch(s.teams()::contains))
                    .map(Staff::nr).toList();
            for (LocalDate day : EventRules.dates(s.day(), PRACTICE_FROM, PRACTICE_UNTIL,
                    List.of(new EventRules.Period(breakStart, breakEnd)))) {
                UUID event = UUID.nameUUIDFromBytes(("termin-" + s.nr() + "-" + day).getBytes());
                Instant start = EventRules.instant(day, s.start());
                Instant end = EventRules.instant(day, s.end());
                boolean past = !end.isAfter(now);
                boolean recorded = past && ATTENDANCE_CHECK.contains(s.nr()) && !day.isBefore(today.minusDays(56));
                sql("""
                        insert into event (id, season_id, type, title, starts_at, ends_at, status, location_name,
                                            response_deadline_hours, series_id, series_date, attendance_check)
                        values (?, ?, 'PRACTICE', 'Training', ?, ?, 'PLANNED', ?, ?, ?, ?, ?)""", event,
                        Seed.seasonId(2), start, end, HOME, EventRules.DEFAULT_RESPONSE_DEADLINE, series, day,
                        ATTENDANCE_CHECK.contains(s.nr()));
                for (int team : s.teams()) {
                    sql("insert into event_team (event_id, team_id) values (?, ?)", event, Seed.teamId(team));
                }
                List<Integer> optional = new ArrayList<>(s.optional());
                if (s.nr() == ONE_OPTIONAL_PRACTICE && !past && !oneOptionalDone) {
                    // The next practice of the men: the second team is welcome, changed for this one event only
                    oneOptionalDone = true;
                    optional.add(s.teams().get(1));
                    sql("update event set individually_changed = true where id = ?", event);
                }
                for (int team : optional) {
                    sql("insert into event_optional_team (event_id, team_id) values (?, ?)", event, Seed.teamId(team));
                }
                if (day.isBefore(today.minusDays(28)) && !recorded || day.isAfter(today.plusDays(14))) {
                    continue;
                }
                Set<Integer> people = new java.util.LinkedHashSet<>(coaches);
                for (int team : s.teams()) {
                    List<Integer> ofTeam = teamPlayers.get(team);
                    if (optional.contains(team)) {
                        // Of a team that is only invited, a few respond
                        for (int i = 0; i < ofTeam.size(); i += 4) {
                            people.add(ofTeam.get(i));
                        }
                    } else {
                        people.addAll(ofTeam);
                    }
                }
                Instant respondedAt = start.minusSeconds(3L * 24 * 3600).isBefore(now)
                        ? start.minusSeconds(3L * 24 * 3600) : now.minusSeconds(3600);
                for (int person : people) {
                    respond(event, person, respondedAt, past);
                }
                if (recorded) {
                    sql("insert into attendance_record (event_id, recorded_at, recorded_by) values (?, ?, ?)", event,
                            end.plusSeconds(600), Seed.personId(coaches.get(0)));
                    for (int team : s.teams()) {
                        for (int person : teamPlayers.get(team)) {
                            int roll = roll(event, person, 7);
                            sql("insert into attendance_entry (event_id, person_id, status) values (?, ?, ?)", event,
                                    Seed.personId(person), roll < 72 ? "PRESENT" : roll < 88 ? "EXCUSED" : "ABSENT");
                        }
                    }
                }
            }
        }
    }

    /**
     * A response that looks like real life: most attend, some decline with a reason, a few are unsure, and for
     * upcoming events some have not answered yet.
     */
    private void respond(UUID event, int person, Instant at, boolean past) {
        int roll = roll(event, person, 3);
        String status;
        String comment = null;
        if (roll < 62) {
            status = "ATTENDING";
        } else if (roll < 78) {
            status = "DECLINED";
            comment = roll % 2 == 0 ? "Arbeit" : null;
        } else if (roll < 86) {
            status = "MAYBE";
            comment = "Weiss ich erst kurz vorher";
        } else if (past) {
            status = "DECLINED";
        } else {
            return;
        }
        sql("""
                insert into response (event_id, person_id, status, comment, changed_at, late_cancellation)
                values (?, ?, ?, ?, ?, false)""", event, Seed.personId(person), status, comment, at);
    }

    /** A number from 0 to 99 that is always the same for the same event and person. */
    private static int roll(UUID event, int person, int salt) {
        return Math.floorMod((event.hashCode() * 31 + person) * 131 + salt * 17, 100);
    }

    // ------------------------------------------------------------------ Hand-entered match days

    /**
     * Match days of «Herren KF 4. Liga II», entered by hand (SUH-10): four are over, four are still to come. They
     * make the driver plan, the timeline and the trips usable without swiss unihockey. The next one has a meeting
     * time, a draft call-up, a full car, a car with free seats and players without a seat; the later ones have
     * only the usual meeting point, so that the timeline suggests a departure (TML-04).
     */
    private void manualMatchDays(Instant now, LocalDate today, List<Integer> players) {
        LocalDate sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        int matchNr = 1;
        int carNr = 1;
        for (ManualMatchDay m : MANUAL_MATCH_DAYS) {
            LocalDate day = sunday.plusWeeks(m.weeks());
            if (m.weeks() > 0 && day.equals(today)) {
                day = day.plusWeeks(1);
            }
            UUID event = Seed.eventId(m.nr());
            Instant start = EventRules.instant(day, m.first());
            Instant end = EventRules.instant(day, m.second()).plusSeconds(50 * 60);
            boolean past = m.weeks() < 0;
            boolean next = m.weeks() == 1;
            Instant meeting = past || next ? start.minusSeconds((long) (m.nr() == 3 ? 75 : 125) * 60) : null;
            sql("""
                    insert into event (id, season_id, type, title, starts_at, ends_at, status, location_name,
                                        location_address, location_maps_url, location_latitude, location_longitude,
                                        notes, response_deadline_hours, meeting_point_time, meeting_point_location)
                    values (?, ?, 'MATCH_DAY', 'Spieltag', ?, ?, 'PLANNED', ?, ?, ?, ?, ?, ?, ?, ?, ?)""", event,
                    Seed.seasonId(2), start, end, m.hall(), m.town(),
                    "https://www.google.com/maps/search/?api=1&query=" + m.latitude() + "," + m.longitude(),
                    m.latitude(), m.longitude(), next ? "Weisse Trikots mitnehmen" : null,
                    EventRules.DEFAULT_RESPONSE_DEADLINE, meeting, HOME);
            sql("insert into event_team (event_id, team_id) values (?, ?)", event, Seed.teamId(MANUAL_TEAM));
            match(matchNr++, event, EventRules.instant(day, m.first()), m.opponent1(), true, m.result1());
            match(matchNr++, event, EventRules.instant(day, m.second()), m.opponent2(), false, m.result2());

            if (past || m.weeks() <= 4) {
                Instant respondedAt = past ? start.minusSeconds(4L * 24 * 3600) : now.minusSeconds(7200);
                for (int person : List.of(2, 3)) {
                    respond(event, person, respondedAt, past);
                }
                for (int person : players) {
                    respond(event, person, respondedAt, past);
                }
            }
            if (past) {
                // Two cars per past match day, the drivers take turns: Bruno drives most often (DRV-17)
                int first = m.nr() % 2 == 0 ? players.get(0) : 3;
                int second = players.get(m.nr() + 1);
                carNr = car(carNr, event, first, 4, players, start);
                carNr = car(carNr, event, second, 3, players, start);
            }
            if (next) {
                // A full car, a car with free seats, the rest still without a seat (DRV-04)
                int firstCar = carNr;
                carNr = car(carNr, event, 3, 3, List.of(), now);
                for (int i = 0; i < 3; i++) {
                    sql("insert into car_passenger (car_id, event_id, person_id, added_at) values (?, ?, ?, ?)",
                            Seed.carId(firstCar), event, Seed.personId(players.get(i)), now);
                }
                carNr = car(carNr, event, players.get(4), 4, List.of(), now);
                sql("insert into call_up (event_id, published_at) values (?, null)", event);
                for (int i = 0; i < 8; i++) {
                    sql("insert into call_up_entry (event_id, person_id) values (?, ?)", event,
                            Seed.personId(players.get(i)));
                }
            }
        }
    }

    private void match(int nr, UUID event, Instant start, String opponent, boolean home, String result) {
        sql("""
                insert into match (id, event_id, opponent, start_time, home, result, source)
                values (?, ?, ?, ?, ?, ?, 'MANUAL')""", Seed.matchId(nr), event, opponent, start, home, result);
    }

    /** A car with its driver; on past match days two players ride along. Returns the next car number. */
    private int car(int nr, UUID event, int driver, int seats, List<Integer> candidates, Instant matchDayStart) {
        Instant at = matchDayStart.minusSeconds(2L * 24 * 3600);
        sql("insert into car (id, event_id, driver_id, seats, created_at) values (?, ?, ?, ?, ?)", Seed.carId(nr),
                event, Seed.personId(driver), seats, at);
        int added = 0;
        for (int person : candidates) {
            if (added == 2) {
                break;
            }
            if (person == driver || seated(event, person)) {
                continue;
            }
            sql("insert into car_passenger (car_id, event_id, person_id, added_at) values (?, ?, ?, ?)",
                    Seed.carId(nr), event, Seed.personId(person), at);
            added++;
        }
        return nr + 1;
    }

    private boolean seated(UUID event, int person) {
        Query query = em.createNativeQuery("""
                select 1 from car where event_id = ?1 and driver_id = ?2
                union all select 1 from car_passenger where event_id = ?1 and person_id = ?2""");
        return !query.setParameter(1, event).setParameter(2, Seed.personId(person)).getResultList().isEmpty();
    }

    /** One event for the whole club, so that an event of the type «Anderes» exists. */
    private void clubEvent(LocalDate today) {
        LocalDate day = today.plusDays(20).with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
        UUID event = UUID.nameUUIDFromBytes("vereinsanlass".getBytes());
        sql("""
                insert into event (id, season_id, type, title, starts_at, ends_at, status, location_name,
                                    response_deadline_hours)
                values (?, ?, 'OTHER', 'Vereinsessen', ?, ?, 'PLANNED', ?, ?)""", event, Seed.seasonId(2),
                EventRules.instant(day, LocalTime.of(19, 0)), EventRules.instant(day, LocalTime.of(22, 0)),
                "Restaurant Hirschen, Beromünster", EventRules.DEFAULT_RESPONSE_DEADLINE);
        for (ClubTeam team : TEAMS) {
            if (team.adults()) {
                sql("insert into event_team (event_id, team_id) values (?, ?)", event, Seed.teamId(team.nr()));
            }
        }
    }

    // ------------------------------------------------------------------ Helpers

    private void sql(String statement, Object... parameters) {
        Query query = em.createNativeQuery(statement);
        for (int i = 0; i < parameters.length; i++) {
            query.setParameter(i + 1, parameters[i]);
        }
        query.executeUpdate();
    }

    private static Instant zurich(LocalDateTime time) {
        return time.atZone(BackendClock.ZONE).toInstant();
    }
}
