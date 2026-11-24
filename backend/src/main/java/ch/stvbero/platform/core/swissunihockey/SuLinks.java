package ch.stvbero.platform.core.swissunihockey;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.error.Unique;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.team.SuLink;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.team.TeamMembership;
import ch.stvbero.platform.core.team.TeamSeason;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Links between teams and swiss unihockey (SUH-02, SUH-11): a team has per season none, one or several teams at
 * swiss unihockey. Coaches (of any team, E-69) and admins link and unlink (E-84); enforced here (RR-07).
 */
@ApplicationScoped
public class SuLinks {

    private static final Logger LOG = Logger.getLogger(SuLinks.class);

    public static final String CLUB_UNAVAILABLE = "swiss unihockey ist im Moment nicht erreichbar. Die Teams des "
            + "Vereins lassen sich nicht laden. Die Team-ID lässt sich auch von Hand eingeben.";

    @Inject
    SwissUnihockeySource source;

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    /** The links of a team in the current season. Members of the team, coaches and admins. */
    @Transactional
    public SuConnectionDto connection(CurrentUser user, UUID teamId) {
        Team team = find(teamId);
        Season season = Season.currentOrFail();
        boolean member = TeamMembership.count("person.id = ?1 and team.id = ?2 and season.id = ?3",
                user.personId(), team.id, season.id) > 0;
        if (!user.allTeams() && !member) {
            throw DomainError.forbidden();
        }
        return new SuConnectionDto(team.id, team.nameIn(season), user.allTeams(),
                SuLink.of(team.id, season.id).stream().map(SuConnectionDto.Link::of).toList());
    }

    /**
     * Links a team with a team at swiss unihockey in the current season (SUH-02). A team at swiss unihockey
     * belongs to at most one team per season (409).
     */
    @Transactional
    public UUID add(CurrentUser user, UUID teamId, SuDtos.LinkInput input) {
        user.checkAllTeams();
        Team team = find(teamId);
        Season season = Season.currentOrFail();
        if (!team.belongsTo(season)) {
            throw DomainError.conflict("Nicht in der aktuellen Saison",
                    "Das Team gehört nicht zur Saison " + season.name + ".", "SUH-03");
        }
        int suTeamId = input.suTeamId();
        SuLink.<SuLink>find("season.id = ?1 and suTeamId = ?2", season.id, suTeamId).firstResultOptional()
                .ifPresent(existing -> {
                    throw alreadyLinked(suTeamId, existing.team, season);
                });
        String name = input.name() == null || input.name().isBlank() ? lookUpName(suTeamId, season)
                : input.name().strip();
        SuLink link = SuLink.create(team, season, suTeamId, name, clock.now());
        link.persist();
        // The same team linked twice at the same time (double click): 409 instead of 500
        Unique.save(em, "su_link_once_per_season", () -> DomainError.conflict("Schon verknüpft",
                "Dieses Team von swiss unihockey ist schon verknüpft.", "SUH-02"));
        return link.id;
    }

    /**
     * Removes a link (SUH-11). The imported matches stay. If the team has no link left, the imported matches of
     * its upcoming match days are marked «nicht mehr vorhanden», so that a coach can remove them (SUH-12).
     */
    @Transactional
    public void remove(CurrentUser user, UUID teamId, UUID linkId) {
        user.checkAllTeams();
        Team team = find(teamId);
        SuLink link = SuLink.<SuLink>findByIdOptional(linkId).filter(l -> l.team.id.equals(team.id))
                .orElseThrow(() -> DomainError.notFound("Diese Verknüpfung gibt es nicht."));
        UUID season = link.season.id;
        link.delete();
        em.flush();
        if (SuLink.count("team.id = ?1 and season.id = ?2", team.id, season) == 0) {
            em.createNativeQuery("""
                            update match set missing_at_su = true
                            where source = 'SWISS_UNIHOCKEY' and event_id in (
                                select e.id from event e join event_team et on et.event_id = e.id
                                where et.team_id = ?1 and e.season_id = ?2 and e.status = 'PLANNED' and e.ends_at > ?3)""")
                    .setParameter(1, team.id).setParameter(2, season).setParameter(3, clock.now())
                    .executeUpdate();
        }
    }

    /**
     * The teams of the club at swiss unihockey in the current season, each with the team of the app it is linked
     * with (SUH-02): the choice in the link dialog. 502 if swiss unihockey cannot be reached (SUH-09).
     */
    @Transactional
    public List<SuDtos.ClubTeam> clubTeams(CurrentUser user) {
        user.checkAllTeams();
        Season season = Season.currentOrFail();
        List<SuClubTeam> teams;
        try {
            teams = source.clubTeams(season.startDate.getYear());
        } catch (SuUnavailable e) {
            LOG.warnf("Teams of the club not loaded from swiss unihockey: %s", e.getMessage());
            throw new DomainError(502, "swiss unihockey nicht erreichbar", CLUB_UNAVAILABLE, "SUH-09");
        }
        Map<Integer, SuLink> linked = new HashMap<>();
        SuLink.<SuLink>list("season.id", season.id).forEach(link -> linked.put(link.suTeamId, link));
        Map<UUID, String> names = names(season);
        return teams.stream().map(team -> {
            SuLink link = linked.get(team.suTeamId());
            return new SuDtos.ClubTeam(team.suTeamId(), team.name(), link == null ? null : link.team.id,
                    link == null ? null : names.getOrDefault(link.team.id, ""));
        }).toList();
    }

    /** How the connection is doing (SUH-13): coaches and admins see a hint if the last sync of a link failed. */
    @Transactional
    public SuDtos.Status status(CurrentUser user) {
        user.checkAllTeams();
        Season season = Season.currentOrFail();
        Map<UUID, String> names = names(season);
        List<SuLink> links = SuLink.list("season.id", season.id);
        Instant lastOk = links.stream().filter(link -> Boolean.TRUE.equals(link.lastSyncOk))
                .map(link -> link.lastSyncAt).max(Comparator.naturalOrder()).orElse(null);
        List<SuDtos.Failure> failures = new ArrayList<>();
        for (SuLink link : links) {
            if (Boolean.FALSE.equals(link.lastSyncOk)) {
                failures.add(new SuDtos.Failure(link.team.id, names.getOrDefault(link.team.id, ""), link.suTeamId,
                        link.suTeamName, time(link.lastSyncAt), link.lastSyncError));
            }
        }
        failures.sort(Comparator.comparing(SuDtos.Failure::teamName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(SuDtos.Failure::suTeamId));
        int linkedTeams = (int) links.stream().map(link -> link.team.id).distinct().count();
        return new SuDtos.Status(linkedTeams, time(lastOk), failures);
    }

    /** The name at swiss unihockey, if it can be looked up; linking also works without it (SUH-09). */
    private String lookUpName(int suTeamId, Season season) {
        try {
            return source.clubTeams(season.startDate.getYear()).stream()
                    .filter(team -> team.suTeamId() == suTeamId).map(SuClubTeam::name)
                    .map(name -> SyncRules.truncateText(name, 120)).findFirst().orElse(null);
        } catch (SuUnavailable e) {
            return null;
        }
    }

    private static DomainError alreadyLinked(int suTeamId, Team other, Season season) {
        String name = other.in(season).map(ts -> ts.name).orElse("einem anderen Team");
        return DomainError.conflict("Schon verknüpft",
                "Die Team-ID " + suTeamId + " ist schon mit «" + name + "» verknüpft.", "SUH-02");
    }

    private static Map<UUID, String> names(Season season) {
        Map<UUID, String> names = new HashMap<>();
        TeamSeason.<TeamSeason>list("season.id", season.id).forEach(ts -> names.put(ts.team.id, ts.name));
        return names;
    }

    private static OffsetDateTime time(Instant instant) {
        return instant == null ? null : instant.atZone(BackendClock.ZONE).toOffsetDateTime();
    }

    private static Team find(UUID id) {
        return Team.<Team>findByIdOptional(id)
                .orElseThrow(() -> DomainError.notFound("Dieses Team gibt es nicht."));
    }
}
