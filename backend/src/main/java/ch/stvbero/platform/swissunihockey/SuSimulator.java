package ch.stvbero.platform.swissunihockey;

import ch.stvbero.platform.core.clock.BackendClock;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates swiss unihockey while the app simulates ({@code Simulation}: the test data set in the profiles
 * {@code dev} and {@code test}, docs/test-data.md, simulated
 * swiss unihockey data). After the reset it delivers for team ID {@code 900001} exactly match 01 and 02 of event 04
 * to the sync, and the league «Herren Kleinfeld 3. Liga», «Gruppe 12» with a table of eight teams and ten matches
 * to the league page (F-17). The test support endpoint {@code PUT /api/test/swiss-unihockey/matches} sets other
 * data or an outage.
 * <p>
 * The league view has its own match list here, so that a sync after the reset still changes nothing; the real
 * client uses the same match list for both.
 */
@ApplicationScoped
@Typed(SuSimulator.class)
@IfBuildProperty(name = "stv.test-support", stringValue = "true")
public class SuSimulator implements SwissUnihockeySource {

    public static final int TEAM_1 = 900001;

    private static final String MAPS_SEEFELD =
            "https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf";
    private static final String NORTH = "Sporthalle Nord";
    private static final String BEROMUENSTER = "Beromünster";

    /** Match 01 and 02 of event 04 (docs/test-data.md). */
    public static final List<SuMatch> SEED = List.of(
            new SuMatch(5000001, at(2026, 11, 22, 9, 30), "Team 1", "UHC Musterdorf", "Sporthalle Seefeld",
                    "Musterdorf", MAPS_SEEFELD, null, null, null),
            new SuMatch(5000002, at(2026, 11, 22, 11, 45), "Unihockey Beispielstadt", "Team 1", "Sporthalle Seefeld",
                    "Musterdorf", MAPS_SEEFELD, null, null, null));

    /** League view of Team 1 after the reset (docs/test-data.md, league of Team 1). */
    public static final SuLeague SEED_LEAGUE = new SuLeague("Herren Kleinfeld 3. Liga", "Gruppe 12", List.of(
            new SuStanding(1, 900011, "UHC Musterdorf", 6, 5, 1, 0, 38, 17, 11),
            new SuStanding(2, TEAM_1, "Team 1", 6, 4, 1, 1, 31, 22, 9),
            new SuStanding(3, 900012, "Unihockey Beispielstadt", 6, 4, 0, 2, 29, 24, 8),
            new SuStanding(4, 900013, "Floorball Seetal", 6, 3, 1, 2, 27, 25, 7),
            new SuStanding(5, 900014, "UHC Waldberg", 6, 2, 1, 3, 22, 25, 5),
            new SuStanding(6, 900015, "Unihockey Bergdorf", 6, 2, 0, 4, 21, 30, 4),
            new SuStanding(7, 900016, "STV Hügelwil", 6, 0, 2, 4, 15, 27, 2),
            new SuStanding(8, 900017, "UH Talmatt", 6, 0, 2, 4, 14, 27, 2)),
            List.of(
                    played(5000011, at(2026, 9, 19, 14, 0), "Team 1", "STV Hügelwil", NORTH, BEROMUENSTER, "7:2"),
                    played(5000012, at(2026, 10, 4, 10, 0), "UH Talmatt", "Team 1", "Turnhalle Talmatt", "Talmatt",
                            "3:6"),
                    played(5000013, at(2026, 10, 17, 15, 30), "Team 1", "Floorball Seetal", NORTH, BEROMUENSTER,
                            "4:4"),
                    played(5000014, at(2026, 10, 25, 11, 0), "UHC Waldberg", "Team 1", "Mehrzweckhalle Waldberg",
                            "Waldberg", "2:5"),
                    played(5000015, at(2026, 11, 7, 13, 0), "UHC Musterdorf", "Team 1", "Sporthalle Seefeld",
                            "Musterdorf", "6:3"),
                    played(5000016, at(2026, 11, 15, 10, 30), "Team 1", "Unihockey Bergdorf", NORTH, BEROMUENSTER,
                            "6:5"),
                    SEED.get(0),
                    SEED.get(1),
                    played(5000017, at(2026, 12, 12, 14, 0), "Team 1", "UH Talmatt", NORTH, BEROMUENSTER, null),
                    played(5000018, at(2027, 1, 10, 10, 0), "Floorball Seetal", "Team 1", "Seehalle", "Seetal",
                            null)));

    /**
     * The teams of the club after the reset (docs/test-data.md, simulated swiss unihockey data): the linked
     * team of Team 1 and two more to choose from in the link dialog (SUH-02).
     */
    public static final List<SuClubTeam> SEED_CLUB_TEAMS = List.of(
            new SuClubTeam(TEAM_1, "Herren KF 3. Liga"),
            new SuClubTeam(900002, "Damen KF 2. Liga"),
            new SuClubTeam(900003, "Junioren B"));

    private record Data(List<SuMatch> matches, SuLeague league, boolean unavailable) {
    }

    private final Map<Integer, Data> teams = new ConcurrentHashMap<>();
    private volatile List<SuClubTeam> clubTeams = SEED_CLUB_TEAMS;
    private volatile boolean clubUnavailable;

    public SuSimulator() {
        reset();
    }

    /** State after the reset: team ID 900001 with match 01 and 02 and its league, nothing otherwise. */
    public final void reset() {
        teams.clear();
        teams.put(TEAM_1, new Data(SEED, SEED_LEAGUE, false));
        clubTeams = SEED_CLUB_TEAMS;
        clubUnavailable = false;
    }

    /** Sets the teams of the club; {@code unavailable} simulates an outage of the team list (SUH-09). */
    public void setClub(List<SuClubTeam> teams, boolean unavailable) {
        if (teams != null) {
            clubTeams = List.copyOf(teams);
        }
        clubUnavailable = unavailable;
    }

    @Override
    public List<SuClubTeam> clubTeams(int season) {
        if (clubUnavailable) {
            throw new SuUnavailable("swiss unihockey not reachable (simulated)");
        }
        return clubTeams;
    }

    /**
     * Sets the data for a team ID; {@code unavailable} simulates an outage (SUH-09) of the match list and the
     * league. {@code league == null} keeps the league view the team ID had before.
     */
    public void set(int suTeamId, List<SuMatch> matches, SuLeague league, boolean unavailable) {
        SuLeague previous = teams.containsKey(suTeamId) ? teams.get(suTeamId).league() : null;
        teams.put(suTeamId, new Data(List.copyOf(matches), league == null ? previous : league, unavailable));
    }

    /** The season does not matter in the simulator: it delivers whatever was set. */
    @Override
    public List<SuMatch> matches(int suTeamId, int season) {
        return data(suTeamId).matches();
    }

    /** Without a league view set, an empty table without league name and the matches of the sync. */
    @Override
    public SuLeague league(int suTeamId, int season) {
        Data data = data(suTeamId);
        return data.league() == null ? new SuLeague(null, null, List.of(), data.matches()) : data.league();
    }

    private Data data(int suTeamId) {
        Data data = teams.getOrDefault(suTeamId, new Data(List.of(), null, false));
        if (data.unavailable()) {
            throw new SuUnavailable("swiss unihockey not reachable (simulated)");
        }
        return data;
    }

    private static Instant at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(BackendClock.ZONE).toInstant();
    }

    private static SuMatch played(int id, Instant start, String home, String away, String hall, String location,
                                  String result) {
        return new SuMatch(id, start, home, away, hall, location, SyncRules.mapsUrl(
                new SuMatch(id, start, home, away, hall, location, null, null, null, null)), null, null, result);
    }
}
