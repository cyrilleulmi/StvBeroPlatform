package ch.stvbero.platform.core.league;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.event.MatchSource;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.swissunihockey.SuLeague;
import ch.stvbero.platform.core.swissunihockey.SuMatch;
import ch.stvbero.platform.core.swissunihockey.SuStanding;
import ch.stvbero.platform.core.swissunihockey.SuUnavailable;
import ch.stvbero.platform.core.swissunihockey.SwissUnihockeySource;
import ch.stvbero.platform.core.swissunihockey.SyncRules;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.team.TeamMembership;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * League data of the teams linked with swiss unihockey (F-17). {@link #refresh(UUID)} loads it (daily sync or
 * manual reload by an admin) and caches it in {@link LeagueSnapshot}; {@link #get} only reads the cache and never
 * calls swiss unihockey (criterion 6). If swiss unihockey is not reachable, the last loaded data stays (SUH-09).
 */
@ApplicationScoped
public class LeagueService {

    private static final Logger LOG = Logger.getLogger(LeagueService.class);

    /** Failed reload of a team with data loaded earlier: that data stays visible (SUH-09). */
    public static final String UNAVAILABLE = "swiss unihockey ist im Moment nicht erreichbar. Es werden weiterhin "
            + "die zuletzt geladenen Daten angezeigt.";

    /** Failed reload of a team that has no loaded data yet: there is nothing to keep showing (SUH-09). */
    public static final String UNAVAILABLE_NEVER_LOADED = "swiss unihockey ist im Moment nicht erreichbar. "
            + "Bitte später nochmals versuchen.";

    private static final TypeReference<List<SuStanding>> STANDINGS = new TypeReference<>() {
    };
    private static final TypeReference<List<SuMatch>> MATCHES = new TypeReference<>() {
    };

    @Inject
    SwissUnihockeySource source;

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    @Inject
    ObjectMapper json;

    /**
     * League page of a team in the current season. Everyone who may see the team (members of the team, coaches and
     * admins; criterion 10), otherwise 403.
     */
    @Transactional
    public LeagueDto get(CurrentUser user, UUID teamId) {
        Team team = find(teamId);
        Season season = Season.currentOrFail();
        boolean member = TeamMembership.count("person.id = ?1 and team.id = ?2 and season.id = ?3",
                user.personId(), team.id, season.id) > 0;
        if (!user.allTeams() && !member) {
            throw DomainError.forbidden();
        }
        String name = team.nameIn(season);
        Integer suTeamId = team.suTeamIdIn(season);
        if (suTeamId == null) {
            return new LeagueDto(team.id, name, false, null, null, null, null, List.of(), List.of(), List.of());
        }
        LeagueSnapshot snapshot = LeagueSnapshot.findById(team.id);
        if (snapshot == null || !season.id.equals(snapshot.seasonId)) {
            return new LeagueDto(team.id, name, true, null, null, null, null, List.of(), List.of(), List.of());
        }
        List<SuStanding> standings = read(snapshot.standings, STANDINGS);
        List<SuMatch> matches = read(snapshot.matches, MATCHES);
        int own = LeagueRules.ownRow(standings, suTeamId, matches, name);
        List<LeagueDto.Row> table = new ArrayList<>();
        for (int i = 0; i < standings.size(); i++) {
            SuStanding s = standings.get(i);
            table.add(new LeagueDto.Row(s.rank(), s.team(), s.matches(), s.wins(), s.draws(), s.losses(),
                    s.goalsFor(), s.goalsAgainst(), s.points(), i == own));
        }
        String ownName = own >= 0 ? LeagueRules.norm(standings.get(own).team()) : SyncRules.ownTeam(matches, name);
        Map<Integer, UUID> events = matchDays(team, matches);
        return new LeagueDto(team.id, name, true, snapshot.league, snapshot.group, time(snapshot.loadedAt),
                time(snapshot.failedAt), table,
                LeagueRules.upcoming(matches, clock.now()).stream().map(m -> match(m, ownName, events)).toList(),
                LeagueRules.results(matches).stream().map(m -> match(m, ownName, events)).toList());
    }

    /** Outcome of a reload by hand. */
    public enum RefreshOutcome {
        /** Loaded. */
        LOADED,
        /** swiss unihockey not reachable; the data loaded earlier stays. */
        FAILED_KEPT_DATA,
        /** swiss unihockey not reachable and no data was ever loaded for this season. */
        FAILED_NO_DATA
    }

    /** Reload by hand (criterion 6): admins only. */
    @Transactional
    public RefreshOutcome refresh(CurrentUser user, UUID teamId) {
        user.checkAdmin();
        if (refresh(teamId)) {
            return RefreshOutcome.LOADED;
        }
        LeagueSnapshot snapshot = LeagueSnapshot.findById(teamId);
        return snapshot != null && snapshot.loadedAt != null
                ? RefreshOutcome.FAILED_KEPT_DATA : RefreshOutcome.FAILED_NO_DATA;
    }

    /**
     * Loads the league data of a team in the current season and caches it. If swiss unihockey is not reachable,
     * the last loaded data stays and only the failed attempt is noted (SUH-09).
     *
     * @return {@code false} if swiss unihockey was not reachable or delivered something unexpected
     */
    @Transactional
    public boolean refresh(UUID teamId) {
        Team team = find(teamId);
        Season season = Season.currentOrFail();
        if (!team.belongsTo(season)) {
            throw DomainError.conflict("Nicht in der aktuellen Saison",
                    "Das Team gehört nicht zur Saison " + season.name + ".", "SUH-03");
        }
        // With several links the league page shows the first one (SUH-11)
        Integer suTeamId = team.suTeamIdIn(season);
        if (suTeamId == null) {
            throw DomainError.conflict("Nicht verknüpft",
                    "«" + team.nameIn(season) + "» ist nicht mit swiss unihockey verknüpft.", "SUH-02");
        }
        // Two loads of the same team run one after the other, never at the same time.
        em.lock(team, LockModeType.PESSIMISTIC_WRITE);
        LeagueSnapshot snapshot = LeagueSnapshot.findById(team.id);
        if (snapshot == null) {
            snapshot = new LeagueSnapshot();
            snapshot.teamId = team.id;
            snapshot.seasonId = season.id;
            snapshot.persist();
        } else if (!season.id.equals(snapshot.seasonId)) {
            // Data of an earlier season does not count for the new one.
            clear(snapshot, season.id);
        }
        Instant now = clock.now();
        try {
            SuLeague league = source.league(suTeamId, season.startDate.getYear());
            store(snapshot, season, league, now);
            LOG.infof("League of team %s loaded: %d rows, %d matches", team.id, league.table().size(),
                    league.matches().size());
            return true;
        } catch (SuUnavailable e) {
            LOG.warnf("League of team %s (SU %d) not loaded: %s", team.id, suTeamId, e.getMessage());
            snapshot.failedAt = now;
            return false;
        }
    }

    /** Stores league data as loaded at {@code loadedAt}. Also used by the seed. */
    @Transactional
    public void store(UUID teamId, UUID seasonId, SuLeague league, Instant loadedAt) {
        LeagueSnapshot snapshot = LeagueSnapshot.findById(teamId);
        if (snapshot == null) {
            snapshot = new LeagueSnapshot();
            snapshot.teamId = teamId;
            snapshot.seasonId = seasonId;
            snapshot.persist();
        }
        clear(snapshot, seasonId);
        store(snapshot, Season.findById(seasonId), league, loadedAt);
    }

    private void store(LeagueSnapshot snapshot, Season season, SuLeague league, Instant loadedAt) {
        snapshot.league = league.league() == null ? null : SyncRules.truncateText(league.league(), 200);
        snapshot.group = league.group() == null ? null : SyncRules.truncateText(league.group(), 200);
        snapshot.standings = write(league.table());
        snapshot.matches = write(league.matches().stream()
                .filter(m -> SyncRules.inSeason(m.startTime(), season.startDate, season.endDate)).toList());
        snapshot.loadedAt = loadedAt;
        snapshot.failedAt = null;
    }

    private static void clear(LeagueSnapshot snapshot, UUID seasonId) {
        snapshot.seasonId = seasonId;
        snapshot.league = null;
        snapshot.group = null;
        snapshot.standings = "[]";
        snapshot.matches = "[]";
        snapshot.loadedAt = null;
        snapshot.failedAt = null;
    }

    /** Match days of this team the matches belong to (criterion 3). */
    private Map<Integer, UUID> matchDays(Team team, List<SuMatch> matches) {
        Map<Integer, UUID> events = new HashMap<>();
        if (matches.isEmpty()) {
            return events;
        }
        em.createQuery("""
                        select m.suMatchId, e.id from Match m join m.event e join e.teams t
                        where m.source = :source and m.suMatchId in :ids and t.id = :team""", Object[].class)
                .setParameter("source", MatchSource.SWISS_UNIHOCKEY)
                .setParameter("ids", matches.stream().map(SuMatch::suMatchId).toList())
                .setParameter("team", team.id)
                .getResultList()
                .forEach(row -> events.put((Integer) row[0], (UUID) row[1]));
        return events;
    }

    private static LeagueDto.LeagueMatch match(SuMatch m, String ownName, Map<Integer, UUID> events) {
        SyncRules.Side side = SyncRules.side(m, ownName);
        return new LeagueDto.LeagueMatch(m.suMatchId(), time(m.startTime()), m.homeTeam(), m.awayTeam(),
                side.opponent(), side.home(), m.hall(), m.location(), m.result(), events.get(m.suMatchId()));
    }

    private static OffsetDateTime time(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, BackendClock.ZONE);
    }

    private <T> List<T> read(String text, TypeReference<List<T>> type) {
        try {
            return json.readValue(text, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored league data not readable", e);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("League data not writable", e);
        }
    }

    private static Team find(UUID id) {
        return Team.<Team>findByIdOptional(id)
                .orElseThrow(() -> DomainError.notFound("Dieses Team gibt es nicht."));
    }
}
