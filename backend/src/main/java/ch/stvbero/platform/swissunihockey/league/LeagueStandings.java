package ch.stvbero.platform.swissunihockey.league;

import ch.stvbero.platform.attendance.event.Event;
import ch.stvbero.platform.attendance.event.StandingDto;
import ch.stvbero.platform.attendance.event.Standings;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.swissunihockey.SuMatch;
import ch.stvbero.platform.swissunihockey.SuStanding;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranks for the matches of a match day (AUF-34), from the league data last loaded (F-17). Without league data
 * everything is unknown.
 */
@ApplicationScoped
public class LeagueStandings implements Standings.Source {

    private static final Logger LOG = Logger.getLogger(LeagueStandings.class);

    @Inject
    ObjectMapper mapper;

    @Override
    public Standings of(Event event) {
        for (Team team : event.teams) {
            LeagueSnapshot snapshot = LeagueSnapshot.findById(team.id);
            if (snapshot == null || !event.season.id.equals(snapshot.seasonId) || snapshot.loadedAt == null) {
                continue;
            }
            try {
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
                return new Ranks(own, ranks);
            } catch (Exception unreadable) {
                // League data that cannot be read never breaks the match day, but it must not go unnoticed
                LOG.warnf(unreadable, "League data of team %s cannot be read, match day %s shows no ranks", team.id, event.id);
            }
        }
        return Standings.NONE;
    }

    private record Ranks(StandingDto own, Map<String, Integer> ranks) implements Standings {

        @Override
        public Integer rankOf(String team) {
            return team == null ? null : ranks.get(LeagueRules.norm(team));
        }
    }
}
