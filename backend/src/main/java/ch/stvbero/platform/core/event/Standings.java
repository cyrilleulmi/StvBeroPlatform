package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.league.LeagueRules;
import ch.stvbero.platform.core.league.LeagueSnapshot;
import ch.stvbero.platform.core.swissunihockey.SuMatch;
import ch.stvbero.platform.core.swissunihockey.SuStanding;
import ch.stvbero.platform.core.team.Team;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Arc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranks of the league table for the matches of a match day (AUF-34): the own team and its opponents, from the
 * league data last loaded (F-17). Without league data everything is unknown.
 */
final class Standings {

    private static final Standings NONE = new Standings(null, Map.of());

    private final StandingDto own;
    private final Map<String, Integer> ranks;

    private Standings(StandingDto own, Map<String, Integer> ranks) {
        this.own = own;
        this.ranks = ranks;
    }

    static Standings of(Event event) {
        for (Team team : event.teams) {
            LeagueSnapshot snapshot = LeagueSnapshot.findById(team.id);
            if (snapshot == null || !event.season.id.equals(snapshot.seasonId) || snapshot.loadedAt == null) {
                continue;
            }
            try {
                ObjectMapper mapper = Arc.container().instance(ObjectMapper.class).get();
                List<SuStanding> table = mapper.readValue(snapshot.standings, new TypeReference<>() {
                });
                List<SuMatch> matches = mapper.readValue(snapshot.matches, new TypeReference<>() {
                });
                if (table.isEmpty()) {
                    continue;
                }
                Map<String, Integer> ranks = new HashMap<>();
                table.forEach(row -> ranks.putIfAbsent(LeagueRules.norm(row.team()), row.rank()));
                Integer suTeamId = team.suTeamIdIn(event.season);
                int ownRow = LeagueRules.ownRow(table, suTeamId == null ? -1 : suTeamId, matches,
                        team.nameIn(event.season));
                StandingDto own = ownRow < 0 ? null
                        : new StandingDto(team.id, table.get(ownRow).rank(), table.size(), snapshot.league, snapshot.group);
                return new Standings(own, ranks);
            } catch (Exception unreadable) {
                // League data that cannot be read never breaks the match day
            }
        }
        return NONE;
    }

    /** Rank of the own team, or {@code null}. */
    StandingDto own() {
        return own;
    }

    /** Rank of a team by its name as in the matches, or {@code null} if it is not in the table. */
    Integer rankOf(String team) {
        return team == null ? null : ranks.get(LeagueRules.norm(team));
    }
}
