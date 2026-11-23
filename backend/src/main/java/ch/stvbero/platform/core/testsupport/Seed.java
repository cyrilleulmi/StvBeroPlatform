package ch.stvbero.platform.core.testsupport;

import ch.stvbero.platform.core.event.NotificationRules;
import ch.stvbero.platform.core.event.EventRules;
import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.league.LeagueService;
import ch.stvbero.platform.core.swissunihockey.SuSimulator;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The fixed starting point from docs/test-data.md: the test data set. Only in the profiles dev and test.
 * {@code POST /api/test/reset} empties the database and fills it again; on start {@link DataSets} decides between
 * this data set and the club data set ({@link ClubSeed}).
 */
@ApplicationScoped
@IfBuildProperty(name = "stv.test-support", stringValue = "true")
public class Seed {

    /** Default "now" after a reset: Monday, 16.11.2026, 06:00 Europe/Zurich. */
    public static final LocalDateTime DEFAULT_NOW = LocalDateTime.of(2026, 11, 16, 6, 0);

    /** League data of Team 1 was last loaded by the daily sync on the morning of the default "now" (F-17). */
    public static final LocalDateTime LEAGUE_LOADED_AT = LocalDateTime.of(2026, 11, 16, 4, 30);

    /**
     * Calendar address of Anna (F-18): {@code /api/calendar/<token>.ics}. All other people get theirs on the first
     * visit of «Profil». Fixed only in the seed; real tokens are random (CAL-06).
     */
    public static final String ANNA_CALENDAR_TOKEN = "seed-anna-calendar-000000000000000000000005";

    /** Push device of Lea (F-19): one phone with push on. Keys are the browser keys of RFC 8291, appendix A. */
    public static final String LEA_PUSH_ID = "a0000000-0000-4000-8000-000000000001";
    public static final String LEA_PUSH_ENDPOINT = "https://push.example.test/seed-lea-phone";
    static final String LEA_PUSH_P256DH =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    static final String LEA_PUSH_AUTH = "BTBZMqHH6r4Tts7J_aSIgg";

    record SeedPerson(int nr, String loginKey, String firstName, String lastName, boolean admin) {
    }

    record SeedSeason(int nr, String name, LocalDate startDate, LocalDate endDate, boolean current) {
    }

    static final List<SeedSeason> SEASONS = List.of(
            new SeedSeason(1, "2025/26", LocalDate.of(2025, 7, 1), LocalDate.of(2026, 6, 30), false),
            new SeedSeason(2, "2026/27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30), true));

    record SeedTeam(int nr, String name, Integer suTeamId) {
    }

    /** Team membership: person, team, season (numbers), team role, group (number, {@code null} = no line). */
    record SeedMembership(int person, int team, int season, String teamRole, Integer group) {

        SeedMembership(int person, int team, int season, String teamRole) {
            this(person, team, season, teamRole, null);
        }
    }

    /**
     * Memberships that start after the season start (F-15, STA-07): "person:team:season" → day of joining. All
     * others joined on the first day of their season.
     */
    static final Map<String, LocalDate> JOINED_LATER = Map.of("11:1:2", LocalDate.of(2026, 10, 1));

    /** Line or goalies (docs/test-data.md, groups). Sort order only for lines. */
    record SeedGroup(int nr, int team, int season, String type, String name, Integer sortOrder) {
    }

    private static final String L = "LINE";
    private static final String G = "GOALIES";

    static final List<SeedGroup> GROUPS = List.of(
            new SeedGroup(1, 1, 2, L, "Linie 1", 1),
            new SeedGroup(2, 1, 2, L, "Linie 2", 2),
            new SeedGroup(3, 1, 2, G, "Goalies", null),
            new SeedGroup(4, 2, 2, L, "Linie 1", 1),
            new SeedGroup(5, 2, 2, L, "Linie 2", 2),
            new SeedGroup(6, 2, 2, G, "Goalies", null),
            new SeedGroup(7, 3, 2, L, "Linie 1", 1),
            new SeedGroup(8, 3, 2, G, "Goalies", null),
            // Goalies of team 1 in the 2025/26 archive (every team has goalies in every season, LIN-05)
            new SeedGroup(9, 1, 1, G, "Goalies", null));

    static final List<SeedTeam> TEAMS = List.of(
            new SeedTeam(1, "Team 1", 900001),
            new SeedTeam(2, "Team 2", null),
            new SeedTeam(3, "Team 3", null));

    private static String teamName(int nr) {
        return TEAMS.stream().filter(t -> t.nr() == nr).findFirst().orElseThrow().name();
    }

    /** Which teams exist in which season: team, season. The name applies per season (SAI-02). */
    static final List<int[]> TEAM_SEASONS = List.of(
            new int[]{1, 1},
            new int[]{1, 2}, new int[]{2, 2}, new int[]{3, 2});

    private static final String P = "PLAYER";
    private static final String C = "COACH";

    static final List<SeedMembership> MEMBERSHIPS = List.of(
            // Season 2026/27 (current)
            new SeedMembership(2, 1, 2, C), new SeedMembership(2, 2, 2, P, 4),
            new SeedMembership(3, 2, 2, C),
            new SeedMembership(4, 3, 2, C),
            new SeedMembership(5, 1, 2, P, 1),
            new SeedMembership(6, 1, 2, P, 1), new SeedMembership(6, 2, 2, P, 5),
            new SeedMembership(7, 1, 2, P, 1),
            new SeedMembership(8, 1, 2, P, 2),
            new SeedMembership(9, 1, 2, P, 2),
            new SeedMembership(10, 1, 2, P, 3),
            new SeedMembership(11, 1, 2, P),
            new SeedMembership(12, 2, 2, P, 4),
            new SeedMembership(13, 2, 2, P, 4),
            new SeedMembership(14, 2, 2, P, 5),
            new SeedMembership(15, 2, 2, P, 6),
            new SeedMembership(16, 3, 2, P, 7),
            // Season 2025/26 (archive)
            new SeedMembership(17, 1, 1, P));

    static final List<SeedPerson> PERSONS = List.of(
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

    /** Series (docs/test-data.md, series). Teams and season as numbers. */
    record SeedSeries(int nr, String weekday, DayOfWeek day, LocalTime start, LocalTime end, String location,
                     LocalDate startDate, LocalDate endDate, List<Integer> teams, SeedException exception) {
    }

    record SeedException(LocalDate startDate, LocalDate endDate, String reason) {
    }

    private static final String NORTH_HALL = "Sporthalle Nord";

    static final List<SeedSeries> SERIES = List.of(
            new SeedSeries(1, "TUESDAY", DayOfWeek.TUESDAY, LocalTime.of(18, 30), LocalTime.of(20, 0), NORTH_HALL,
                    LocalDate.of(2026, 8, 18), LocalDate.of(2027, 4, 27), List.of(1, 2),
                    new SeedException(LocalDate.of(2026, 12, 21), LocalDate.of(2027, 1, 3), "Weihnachtsferien")),
            new SeedSeries(2, "THURSDAY", DayOfWeek.THURSDAY, LocalTime.of(19, 0), LocalTime.of(20, 30), NORTH_HALL,
                    LocalDate.of(2026, 8, 20), LocalDate.of(2027, 4, 29), List.of(1), null),
            new SeedSeries(3, "WEDNESDAY", DayOfWeek.WEDNESDAY, LocalTime.of(18, 0), LocalTime.of(19, 30), "Turnhalle Dorf",
                    LocalDate.of(2026, 8, 19), LocalDate.of(2027, 4, 28), List.of(3), null));

    /** Series events with a fixed ID (docs/test-data.md, events with fixed IDs): series, date → event number. */
    static final Map<String, Integer> FIXED_SERIES_EVENTS = Map.of(
            "1:2026-11-17", 1,
            "2:2026-11-19", 2,
            "1:2026-11-24", 3,
            "1:2026-11-10", 7,
            "2:2026-11-12", 9,
            "2:2026-11-05", 10);

    /** Seed events with the switch «Präsenz erfassen» on (F-14, PRS-01). */
    static final Set<Integer> ATTENDANCE_CHECK = Set.of(9, 10);

    /** Cancelled seed events: number → reason. */
    static final Map<Integer, String> CANCELLED = Map.of(3, "Halle besetzt");

    /** Response in the seed (F-04): event number, person number, status, comment. */
    record SeedResponse(int event, int person, String status, String comment, boolean late) {

        SeedResponse(int event, int person, String status, String comment) {
            this(event, person, status, comment, false);
        }
    }

    /** Time of the late cancellation on event 07 (F-15): after its response deadline (Mon 09.11. 18:30). */
    static final LocalDateTime LATE_CANCELLED_AT = LocalDateTime.of(2026, 11, 10, 17, 0);

    /** Time of all seed responses: before the default "now" and before every response deadline. */
    static final LocalDateTime RESPONDED_AT = LocalDateTime.of(2026, 11, 15, 20, 0);

    private static final String A = "ATTENDING";
    private static final String D = "DECLINED";
    private static final String M = "MAYBE";

    /** Responses as in docs/test-data.md. Whoever is missing is pending. */
    static final List<SeedResponse> RESPONSES = List.of(
            // Event 01: main example, every response status (Nina and Ella pending)
            new SeedResponse(1, 5, A, null), new SeedResponse(1, 6, A, null), new SeedResponse(1, 7, D, "Krank"),
            new SeedResponse(1, 8, A, null), new SeedResponse(1, 10, A, null), new SeedResponse(1, 11, A, null),
            new SeedResponse(1, 2, A, null), new SeedResponse(1, 12, M, "Komme evtl. später"),
            new SeedResponse(1, 13, A, null), new SeedResponse(1, 14, D, null), new SeedResponse(1, 3, D, "Ferien"),
            // Event 02: only Anna
            new SeedResponse(2, 5, A, null),
            // Event 04: match day team 1
            new SeedResponse(4, 5, A, null), new SeedResponse(4, 6, A, null), new SeedResponse(4, 7, M, "Muss evtl. arbeiten"),
            new SeedResponse(4, 8, A, null), new SeedResponse(4, 10, A, null), new SeedResponse(4, 11, D, null),
            new SeedResponse(4, 2, A, null),
            // Event 05: match day team 2
            new SeedResponse(5, 2, A, null), new SeedResponse(5, 6, A, null), new SeedResponse(5, 12, A, null),
            new SeedResponse(5, 13, A, null), new SeedResponse(5, 14, D, null), new SeedResponse(5, 15, A, null),
            new SeedResponse(5, 3, A, null),
            // Event 09: past practice team 1 with attendance check, nothing recorded (Kim pending)
            new SeedResponse(9, 5, A, null), new SeedResponse(9, 6, A, null), new SeedResponse(9, 7, D, "Krank"),
            new SeedResponse(9, 8, A, null), new SeedResponse(9, 9, M, "Knie schmerzt"), new SeedResponse(9, 10, A, null),
            // Event 10: past practice team 1 with recorded attendance (Kim pending)
            new SeedResponse(10, 5, A, null), new SeedResponse(10, 6, A, null), new SeedResponse(10, 7, D, "Ferien"),
            new SeedResponse(10, 8, A, null), new SeedResponse(10, 9, A, null), new SeedResponse(10, 10, A, null),
            // Event 07: past practice team 1 + 2 for the statistics (F-15); Anna cancelled late
            new SeedResponse(7, 5, D, "Krank", true), new SeedResponse(7, 6, A, null), new SeedResponse(7, 7, A, null),
            new SeedResponse(7, 12, M, "Komme später"));

    /** Responses to past events are given two days before the event instead of {@link #RESPONDED_AT}. */
    static final Map<Integer, LocalDateTime> RESPONDED_AT_PER_EVENT = Map.of(
            7, LocalDateTime.of(2026, 11, 8, 20, 0),
            9, LocalDateTime.of(2026, 11, 10, 20, 0),
            10, LocalDateTime.of(2026, 11, 3, 20, 0));

    /** Recorded attendance (F-14): event, recorded by, recorded at, person → status. */
    record SeedAttendance(int event, int recordedBy, LocalDateTime recordedAt, Map<Integer, String> entries) {
    }

    static final List<SeedAttendance> ATTENDANCE = List.of(
            // Event 10 recorded by Petra: Sara responded «dabei» but was absent, Mia excused, Kim absent
            new SeedAttendance(10, 2, LocalDateTime.of(2026, 11, 5, 21, 0), Map.of(
                    5, "PRESENT", 6, "PRESENT", 7, "EXCUSED", 8, "ABSENT", 9, "PRESENT", 10, "PRESENT", 11, "ABSENT")));

    /** Single event. Venue with address and Google Maps link optional, meeting point only for match days. */
    record SeedEvent(int nr, int season, String type, String title, LocalDateTime start, LocalDateTime end,
                      String location, String address, String mapsUrl, String notes, LocalDateTime meetingPointTime,
                      String meetingPointLocation, List<Integer> teams) {
    }

    static final List<SeedEvent> SINGLE_EVENTS = List.of(
            new SeedEvent(4, 2, "MATCH_DAY", "Spieltag", LocalDateTime.of(2026, 11, 22, 9, 30),
                    LocalDateTime.of(2026, 11, 22, 13, 0), "Sporthalle Seefeld", "Musterdorf",
                    "https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf",
                    "Weisse Trikots mitnehmen", LocalDateTime.of(2026, 11, 22, 8, 0), "Parkplatz Sporthalle Nord",
                    List.of(1)),
            new SeedEvent(5, 2, "MATCH_DAY", "Spieltag", LocalDateTime.of(2026, 11, 28, 14, 0),
                    LocalDateTime.of(2026, 11, 28, 16, 0), "Turnhalle Weid", "Beispielstadt",
                    "https://www.google.com/maps/search/?api=1&query=Turnhalle+Weid+Beispielstadt",
                    null, LocalDateTime.of(2026, 11, 28, 12, 30), NORTH_HALL, List.of(2)),
            new SeedEvent(6, 2, "OTHER", "Teamessen", LocalDateTime.of(2026, 12, 11, 19, 0),
                    LocalDateTime.of(2026, 12, 11, 22, 0), null, null, null, null, null, null, List.of(1, 2, 3)),
            new SeedEvent(8, 1, "PRACTICE", "Training", LocalDateTime.of(2026, 5, 12, 18, 30),
                    LocalDateTime.of(2026, 5, 12, 20, 0), NORTH_HALL, null, null, null, null, null, List.of(1)));

    /** Match (docs/test-data.md, events 04 and 05): number, event, start time, opponent, home, SU match ID. */
    record SeedMatch(int nr, int event, LocalDateTime startTime, String opponent, boolean home, Integer suMatchId) {
    }

    static final List<SeedMatch> MATCHES = List.of(
            new SeedMatch(1, 4, LocalDateTime.of(2026, 11, 22, 9, 30), "UHC Musterdorf", true, 5000001),
            new SeedMatch(2, 4, LocalDateTime.of(2026, 11, 22, 11, 45), "Unihockey Beispielstadt", false, 5000002),
            new SeedMatch(3, 5, LocalDateTime.of(2026, 11, 28, 14, 0), "UH Testingen", true, null));

    /** Call-up (F-05): event, called-up persons, published at ({@code null} = draft). */
    record SeedCallUp(int event, List<Integer> persons, LocalDateTime publishedAt) {
    }

    static final List<SeedCallUp> CALL_UPS = List.of(
            // Event 04: draft with Anna, Lea, Mia, Sara, Jana
            new SeedCallUp(4, List.of(5, 6, 7, 8, 10), null),
            // Event 05: published with Petra, Eva, Lara, Ella (Lea not called up)
            new SeedCallUp(5, List.of(2, 12, 13, 15), LocalDateTime.of(2026, 11, 15, 21, 0)));

    /**
     * Notification (F-06): number, type, recipient, event, created at, read at ({@code null} = unread),
     * for call-ups: called up yes/no.
     */
    record SeedNotification(int nr, String type, int recipient, int event, LocalDateTime createdAt,
                                LocalDateTime readAt, boolean calledUp) {
    }

    private static final LocalDateTime CANCELLED_AT = LocalDateTime.of(2026, 11, 14, 10, 0);
    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 11, 15, 21, 0);

    static final List<SeedNotification> NOTIFICATIONS = List.of(
            // Event 03 cancelled by Petra: all members of team 1 and team 2 except Petra. Mia has read it.
            new SeedNotification(1, "EVENT_CANCELLED", 3, 3, CANCELLED_AT, null, false),
            new SeedNotification(2, "EVENT_CANCELLED", 5, 3, CANCELLED_AT, null, false),
            new SeedNotification(3, "EVENT_CANCELLED", 6, 3, CANCELLED_AT, null, false),
            new SeedNotification(4, "EVENT_CANCELLED", 7, 3, CANCELLED_AT, LocalDateTime.of(2026, 11, 14, 12, 0), false),
            new SeedNotification(5, "EVENT_CANCELLED", 8, 3, CANCELLED_AT, null, false),
            new SeedNotification(6, "EVENT_CANCELLED", 9, 3, CANCELLED_AT, null, false),
            new SeedNotification(7, "EVENT_CANCELLED", 10, 3, CANCELLED_AT, null, false),
            new SeedNotification(8, "EVENT_CANCELLED", 11, 3, CANCELLED_AT, null, false),
            new SeedNotification(9, "EVENT_CANCELLED", 12, 3, CANCELLED_AT, null, false),
            new SeedNotification(10, "EVENT_CANCELLED", 13, 3, CANCELLED_AT, null, false),
            new SeedNotification(11, "EVENT_CANCELLED", 14, 3, CANCELLED_AT, null, false),
            new SeedNotification(12, "EVENT_CANCELLED", 15, 3, CANCELLED_AT, null, false),
            // Call-up for event 05 published by Marc: all players of team 2. Petra has read it.
            new SeedNotification(13, "CALL_UP_PUBLISHED", 2, 5, PUBLISHED_AT,
                    LocalDateTime.of(2026, 11, 15, 21, 30), true),
            new SeedNotification(14, "CALL_UP_PUBLISHED", 6, 5, PUBLISHED_AT, null, false),
            new SeedNotification(15, "CALL_UP_PUBLISHED", 12, 5, PUBLISHED_AT, null, true),
            new SeedNotification(16, "CALL_UP_PUBLISHED", 13, 5, PUBLISHED_AT, null, true),
            new SeedNotification(17, "CALL_UP_PUBLISHED", 14, 5, PUBLISHED_AT, null, false),
            new SeedNotification(18, "CALL_UP_PUBLISHED", 15, 5, PUBLISHED_AT, null, true));

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    @Inject
    LeagueService league;

    @Inject
    ch.stvbero.platform.core.driver.DriverPlanHistory driverPlanHistory;

    @Inject
    ch.stvbero.platform.core.absence.AbsenceService absences;

    /** Sara's holidays (ABS): Mon 14.12.–Sun 20.12.2026, declines the practices of Tue 15.12. and Thu 17.12. */
    public static final String SARA_ABSENCE_ID = "d0000000-0000-4000-8000-000000000001";

    /** Empties all tables (except the Flyway history), fills in the seed and sets the clock to the default. */
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

        for (SeedSeason s : SEASONS) {
            em.createNativeQuery("insert into season (id, name, start_date, end_date, is_current) values (?, ?, ?, ?, ?)")
                    .setParameter(1, seasonId(s.nr())).setParameter(2, s.name()).setParameter(3, s.startDate())
                    .setParameter(4, s.endDate()).setParameter(5, s.current())
                    .executeUpdate();
        }
        for (SeedPerson p : PERSONS) {
            em.createNativeQuery("insert into person (id, first_name, last_name, email, admin) values (?, ?, ?, ?, ?)")
                    .setParameter(1, personId(p.nr())).setParameter(2, p.firstName()).setParameter(3, p.lastName())
                    .setParameter(4, p.loginKey() + "@example.ch").setParameter(5, p.admin())
                    .executeUpdate();
            em.createNativeQuery("insert into account (id, person_id, login_key) values (?, ?, ?)")
                    .setParameter(1, accountId(p.nr())).setParameter(2, personId(p.nr())).setParameter(3, p.loginKey())
                    .executeUpdate();
        }
        for (SeedTeam t : TEAMS) {
            em.createNativeQuery("insert into team (id) values (?)")
                    .setParameter(1, teamId(t.nr()))
                    .executeUpdate();
        }
        for (int[] ts : TEAM_SEASONS) {
            em.createNativeQuery("insert into team_season (team_id, season_id, name) values (?, ?, ?)")
                    .setParameter(1, teamId(ts[0])).setParameter(2, seasonId(ts[1])).setParameter(3, teamName(ts[0]))
                    .executeUpdate();
        }
        // Standard event variants (VAR-01). The teams of the test data set make a call-up (VAR-11), so that the
        // call-up can be tested; a new team does not.
        em.createNativeQuery(ch.stvbero.platform.core.variant.VariantRules.ENSURE_STANDARD_SQL).executeUpdate();
        em.createNativeQuery("update event_variant set call_up = true where event_type = 'MATCH_DAY'").executeUpdate();
        // Link to swiss unihockey in the current season (SUH-11), last synced by the daily run at 04:30
        for (SeedTeam t : TEAMS) {
            if (t.suTeamId() != null) {
                em.createNativeQuery("""
                                insert into su_link (id, team_id, season_id, su_team_id, su_team_name, created_at,
                                                     last_sync_at, last_sync_ok)
                                values (?, ?, ?, ?, ?, ?, ?, true)""")
                        .setParameter(1, suLinkId(t.nr())).setParameter(2, teamId(t.nr()))
                        .setParameter(3, seasonId(2)).setParameter(4, t.suTeamId())
                        .setParameter(5, SuSimulator.SEED_CLUB_TEAMS.get(0).name())
                        .setParameter(6, zurich(SEASONS.get(1).startDate().atStartOfDay()))
                        .setParameter(7, zurich(LEAGUE_LOADED_AT))
                        .executeUpdate();
            }
        }
        for (SeedGroup g : GROUPS) {
            em.createNativeQuery("insert into line_group (id, team_id, season_id, type, name, sort_order) values (?, ?, ?, ?, ?, "
                            + (g.sortOrder() == null ? "null" : g.sortOrder()) + ")")
                    .setParameter(1, groupId(g.nr())).setParameter(2, teamId(g.team()))
                    .setParameter(3, seasonId(g.season())).setParameter(4, g.type()).setParameter(5, g.name())
                    .executeUpdate();
        }
        for (SeedMembership m : MEMBERSHIPS) {
            UUID id = UUID.nameUUIDFromBytes(("tz-" + m.person() + "-" + m.team() + "-" + m.season()).getBytes());
            LocalDate joinedOn = JOINED_LATER.getOrDefault(m.person() + ":" + m.team() + ":" + m.season(),
                    SEASONS.get(m.season() - 1).startDate());
            em.createNativeQuery("""
                            insert into team_membership (id, person_id, team_id, season_id, team_role, joined_on)
                            values (?, ?, ?, ?, ?, ?)""")
                    .setParameter(1, id)
                    .setParameter(2, personId(m.person())).setParameter(3, teamId(m.team()))
                    .setParameter(4, seasonId(m.season())).setParameter(5, m.teamRole()).setParameter(6, joinedOn)
                    .executeUpdate();
            if (m.group() != null) {
                em.createNativeQuery("insert into group_assignment (team_membership_id, group_id) values (?, ?)")
                        .setParameter(1, id).setParameter(2, groupId(m.group()))
                        .executeUpdate();
            }
        }
        seriesAndEvents();
        for (SeedResponse r : RESPONSES) {
            em.createNativeQuery("""
                            insert into response (event_id, person_id, status, comment, changed_at, late_cancellation)
                            values (?, ?, ?, ?, ?, ?)""")
                    .setParameter(1, eventId(r.event())).setParameter(2, personId(r.person()))
                    .setParameter(3, r.status()).setParameter(4, r.comment())
                    .setParameter(5, zurich(r.late() ? LATE_CANCELLED_AT
                            : RESPONDED_AT_PER_EVENT.getOrDefault(r.event(), RESPONDED_AT)))
                    .setParameter(6, r.late())
                    .executeUpdate();
        }
        matchesAndCallUps();
        driverList();
        attendance();
        notifications();
        // League data of Team 1, loaded by the daily sync at 04:30 (F-17)
        league.store(teamId(1), seasonId(2), SuSimulator.SEED_LEAGUE,
                LEAGUE_LOADED_AT.atZone(BackendClock.ZONE).toInstant());
        em.createNativeQuery("insert into calendar_subscription (person_id, token, created_at) values (?, ?, ?)")
                .setParameter(1, personId(5)).setParameter(2, ANNA_CALENDAR_TOKEN).setParameter(3, zurich(RESPONDED_AT))
                .executeUpdate();
        // F-19: Kim switched e-mails off; Lea has push on one phone. All others: e-mail on (default), no push.
        em.createNativeQuery("insert into notification_preference (person_id, email) values (?, false)")
                .setParameter(1, personId(11)).executeUpdate();
        em.createNativeQuery("""
                        insert into push_subscription (id, person_id, endpoint, p256dh, auth, label, created_at)
                        values (?, ?, ?, ?, ?, 'Chrome auf Android', ?)""")
                .setParameter(1, UUID.fromString(LEA_PUSH_ID)).setParameter(2, personId(6))
                .setParameter(3, LEA_PUSH_ENDPOINT).setParameter(4, LEA_PUSH_P256DH).setParameter(5, LEA_PUSH_AUTH)
                .setParameter(6, zurich(RESPONDED_AT))
                .executeUpdate();
        em.flush();
        em.clear();

        clock.fixAt(DEFAULT_NOW.atZone(BackendClock.ZONE).toInstant());
        // ABS: Sara is on holiday; her practices in this time are declined like after entering it in the app
        em.createNativeQuery("""
                        insert into absence (id, person_id, first_day, last_day, reason, note, created_at)
                        values (?, ?, ?, ?, 'VACATION', 'Skiferien', ?)""")
                .setParameter(1, UUID.fromString(SARA_ABSENCE_ID)).setParameter(2, personId(8))
                .setParameter(3, LocalDate.of(2026, 12, 14)).setParameter(4, LocalDate.of(2026, 12, 20))
                .setParameter(5, zurich(RESPONDED_AT))
                .executeUpdate();
        absences.applyForPerson(personId(8), practice -> false);
    }

    /** Series with all their events (past ones too) and the single events (F-03). */
    private void seriesAndEvents() {
        for (SeedSeries s : SERIES) {
            em.createNativeQuery("""
                            insert into series (id, season_id, type, title, weekday, start_time, end_time, valid_from,
                                               valid_until, location_name, response_deadline_hours)
                            values (?, ?, 'PRACTICE', 'Training', ?, ?, ?, ?, ?, ?, ?)""")
                    .setParameter(1, seriesId(s.nr())).setParameter(2, seasonId(2)).setParameter(3, s.weekday())
                    .setParameter(4, s.start()).setParameter(5, s.end()).setParameter(6, s.startDate())
                    .setParameter(7, s.endDate()).setParameter(8, s.location())
                    .setParameter(9, EventRules.DEFAULT_RESPONSE_DEADLINE)
                    .executeUpdate();
            for (int team : s.teams()) {
                em.createNativeQuery("insert into series_team (series_id, team_id) values (?, ?)")
                        .setParameter(1, seriesId(s.nr())).setParameter(2, teamId(team)).executeUpdate();
            }
            List<EventRules.Period> exceptions = List.of();
            if (s.exception() != null) {
                SeedException exception = s.exception();
                em.createNativeQuery("insert into series_exception (id, series_id, start_date, end_date, reason) values (?, ?, ?, ?, ?)")
                        .setParameter(1, UUID.nameUUIDFromBytes(("ausnahme-" + s.nr()).getBytes()))
                        .setParameter(2, seriesId(s.nr())).setParameter(3, exception.startDate())
                        .setParameter(4, exception.endDate()).setParameter(5, exception.reason()).executeUpdate();
                exceptions = List.of(new EventRules.Period(exception.startDate(), exception.endDate()));
            }
            for (LocalDate day : EventRules.dates(s.day(), s.startDate(), s.endDate(), exceptions)) {
                Integer nr = FIXED_SERIES_EVENTS.get(s.nr() + ":" + day);
                UUID id = nr != null ? eventId(nr) : UUID.nameUUIDFromBytes(("termin-" + s.nr() + "-" + day).getBytes());
                em.createNativeQuery("""
                                insert into event (id, season_id, type, title, starts_at, ends_at, status, cancellation_reason, location_name,
                                                    response_deadline_hours, series_id, series_date, attendance_check)
                                values (?, ?, 'PRACTICE', 'Training', ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                        .setParameter(1, id).setParameter(2, seasonId(2))
                        .setParameter(3, EventRules.instant(day, s.start()))
                        .setParameter(4, EventRules.instant(day, s.end()))
                        .setParameter(5, nr != null && CANCELLED.containsKey(nr) ? "CANCELLED" : "PLANNED")
                        .setParameter(6, nr != null ? CANCELLED.get(nr) : null)
                        .setParameter(7, s.location()).setParameter(8, EventRules.DEFAULT_RESPONSE_DEADLINE)
                        .setParameter(9, seriesId(s.nr())).setParameter(10, day)
                        .setParameter(11, nr != null && ATTENDANCE_CHECK.contains(nr))
                        .executeUpdate();
                assignTeams(id, s.teams());
            }
        }
        for (SeedEvent e : SINGLE_EVENTS) {
            em.createNativeQuery("""
                            insert into event (id, season_id, type, title, starts_at, ends_at, status, location_name, location_address,
                                                location_maps_url, notes, response_deadline_hours, meeting_point_time, meeting_point_location)
                            values (?, ?, ?, ?, ?, ?, 'PLANNED', ?, ?, ?, ?, ?, ?, ?)""")
                    .setParameter(1, eventId(e.nr())).setParameter(2, seasonId(e.season()))
                    .setParameter(3, e.type()).setParameter(4, e.title())
                    .setParameter(5, zurich(e.start())).setParameter(6, zurich(e.end()))
                    .setParameter(7, e.location()).setParameter(8, e.address()).setParameter(9, e.mapsUrl())
                    .setParameter(10, e.notes()).setParameter(11, EventRules.DEFAULT_RESPONSE_DEADLINE)
                    .setParameter(12, e.meetingPointTime() == null ? null : zurich(e.meetingPointTime()))
                    .setParameter(13, e.meetingPointLocation())
                    .executeUpdate();
            assignTeams(eventId(e.nr()), e.teams());
        }
    }

    /** Recorded attendance of event 10 (F-14). */
    private void attendance() {
        for (SeedAttendance a : ATTENDANCE) {
            em.createNativeQuery("insert into attendance_record (event_id, recorded_at, recorded_by) values (?, ?, ?)")
                    .setParameter(1, eventId(a.event())).setParameter(2, zurich(a.recordedAt()))
                    .setParameter(3, personId(a.recordedBy())).executeUpdate();
            a.entries().forEach((person, status) -> em.createNativeQuery(
                            "insert into attendance_entry (event_id, person_id, status) values (?, ?, ?)")
                    .setParameter(1, eventId(a.event())).setParameter(2, personId(person)).setParameter(3, status)
                    .executeUpdate());
        }
    }

    /** Matches and call-ups of the match days 04 and 05 (F-05). */
    private void matchesAndCallUps() {
        for (SeedMatch m : MATCHES) {
            em.createNativeQuery("""
                            insert into match (id, event_id, opponent, start_time, home, source, su_match_id)
                            values (?, ?, ?, ?, ?, ?, ?)""")
                    .setParameter(1, matchId(m.nr())).setParameter(2, eventId(m.event()))
                    .setParameter(3, m.opponent()).setParameter(4, zurich(m.startTime())).setParameter(5, m.home())
                    .setParameter(6, m.suMatchId() == null ? "MANUAL" : "SWISS_UNIHOCKEY")
                    .setParameter(7, m.suMatchId())
                    .executeUpdate();
        }
        for (SeedCallUp c : CALL_UPS) {
            em.createNativeQuery("insert into call_up (event_id, published_at) values (?, ?)")
                    .setParameter(1, eventId(c.event()))
                    .setParameter(2, c.publishedAt() == null ? null : zurich(c.publishedAt()))
                    .executeUpdate();
            for (int person : c.persons()) {
                em.createNativeQuery("insert into call_up_entry (event_id, person_id) values (?, ?)")
                        .setParameter(1, eventId(c.event())).setParameter(2, personId(person)).executeUpdate();
            }
        }
    }

    /** Car of a match day (F-20): number, event, driver, seats, passengers (person numbers). */
    record SeedCar(int nr, int event, int driver, int seats, List<Integer> passengers) {
    }

    /**
     * Driver list of match day 04 (F-20, DRV-13): Petra's car is full (Anna, Lea, Mia), Sara drives with three free
     * seats, Jana responded «dabei» and has no seat yet. The route is calculated on the first view.
     */
    static final List<SeedCar> CARS = List.of(
            new SeedCar(1, 4, 2, 3, List.of(5, 6, 7)),
            new SeedCar(2, 4, 8, 3, List.of()));

    private void driverList() {
        for (SeedCar car : CARS) {
            em.createNativeQuery("insert into car (id, event_id, driver_id, seats, created_at) values (?, ?, ?, ?, ?)")
                    .setParameter(1, carId(car.nr())).setParameter(2, eventId(car.event()))
                    .setParameter(3, personId(car.driver())).setParameter(4, car.seats())
                    .setParameter(5, zurich(RESPONDED_AT)).executeUpdate();
            em.flush();
            driverPlanHistory.seedCarAdded(eventId(car.event()), personId(car.driver()), zurich(RESPONDED_AT));
            for (int person : car.passengers()) {
                em.createNativeQuery("""
                                insert into car_passenger (car_id, event_id, person_id, added_at) values (?, ?, ?, ?)""")
                        .setParameter(1, carId(car.nr())).setParameter(2, eventId(car.event()))
                        .setParameter(3, personId(person)).setParameter(4, zurich(RESPONDED_AT)).executeUpdate();
            }
        }
    }

    /** Notifications for event 03 (cancellation) and event 05 (call-up), with the same texts as in production (F-06). */
    private void notifications() {
        for (SeedNotification n : NOTIFICATIONS) {
            Object[] event = (Object[]) em.createNativeQuery("select title, starts_at, cancellation_reason from event where id = ?")
                    .setParameter(1, eventId(n.event())).getSingleResult();
            String label = NotificationRules.label((String) event[0], instant(event[1]));
            String text = "EVENT_CANCELLED".equals(n.type())
                    ? NotificationRules.cancelledText(label, (String) event[2])
                    : NotificationRules.publishedText(label, n.calledUp());
            em.createNativeQuery("""
                            insert into notification (id, type, recipient_id, text, event_id, created_at, read_at)
                            values (?, ?, ?, ?, ?, ?, ?)""")
                    .setParameter(1, notificationId(n.nr())).setParameter(2, n.type())
                    .setParameter(3, personId(n.recipient())).setParameter(4, text)
                    .setParameter(5, eventId(n.event())).setParameter(6, zurich(n.createdAt()))
                    .setParameter(7, n.readAt() == null ? null : zurich(n.readAt()))
                    .executeUpdate();
        }
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return ((Timestamp) value).toInstant();
    }

    private void assignTeams(UUID eventId, List<Integer> teams) {
        for (int team : teams) {
            em.createNativeQuery("insert into event_team (event_id, team_id) values (?, ?)")
                    .setParameter(1, eventId).setParameter(2, teamId(team)).executeUpdate();
        }
    }

    private static Instant zurich(LocalDateTime time) {
        return time.atZone(BackendClock.ZONE).toInstant();
    }

    public static UUID teamId(int nr) {
        return id("1", nr);
    }

    public static UUID personId(int nr) {
        return id("2", nr);
    }

    public static UUID groupId(int nr) {
        return id("3", nr);
    }

    public static UUID eventId(int nr) {
        return id("4", nr);
    }

    public static UUID matchId(int nr) {
        return id("5", nr);
    }

    public static UUID seriesId(int nr) {
        return id("7", nr);
    }

    public static UUID seasonId(int nr) {
        return id("6", nr);
    }

    public static UUID notificationId(int nr) {
        return id("9", nr);
    }

    public static UUID carId(int nr) {
        return id("b", nr);
    }

    /** Link of a team to swiss unihockey in the current season (SUH-11): the number of the team. */
    public static UUID suLinkId(int nr) {
        return id("c", nr);
    }

    public static UUID accountId(int nr) {
        return id("8", nr);
    }

    private static UUID id(String type, int nr) {
        return UUID.fromString("%s0000000-0000-4000-8000-%012d".formatted(type, nr));
    }
}
