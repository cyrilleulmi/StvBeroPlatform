package ch.stvbero.platform.swissunihockey;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.notification.NotificationType;
import ch.stvbero.platform.notification.Notifications;
import ch.stvbero.platform.notification.Message;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.team.SuLink;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.team.TeamSeason;
import ch.stvbero.platform.attendance.event.Location;
import ch.stvbero.platform.attendance.event.MatchSource;
import ch.stvbero.platform.attendance.event.Match;
import ch.stvbero.platform.attendance.event.Event;
import ch.stvbero.platform.attendance.event.EventNotifications;
import ch.stvbero.platform.attendance.event.EventRules;
import ch.stvbero.platform.attendance.event.EventStatus;
import ch.stvbero.platform.attendance.event.EventType;
import ch.stvbero.platform.core.clock.BackendClock;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sync of the matches of a team with swiss unihockey (SUH-03 to SUH-10).
 * <ul>
 *   <li>New matches become match days: the same day and the same hall give one match day (SUH-04).</li>
 *   <li>Changed matches are applied. Meeting point, notes, responses and call-up stay (SUH-05, SUH-06);
 *       the team is notified (BEN-06).</li>
 *   <li>Matches that swiss unihockey no longer delivers are marked, not deleted. Changed and missing matches
 *       show a warning to coaches and admins (SUH-12).</li>
 *   <li>A team can have several links in a season; the sync takes the matches of all of them (SUH-11).</li>
 *   <li>Match days and matches entered by hand stay untouched (SUH-10).</li>
 *   <li>If swiss unihockey is not reachable, nothing changes (502, SUH-09).</li>
 * </ul>
 */
@ApplicationScoped
public class SuSync {

    private static final Logger LOG = Logger.getLogger(SuSync.class);

    public static final String UNAVAILABLE = "swiss unihockey ist im Moment nicht erreichbar. Es wurde nichts "
            + "geändert. Der nächste Abgleich versucht es wieder.";

    /** What the app says about a failed sync on the links (SUH-13). */
    public static final String FAILED = "swiss unihockey war nicht erreichbar oder hat unerwartete Daten geliefert.";

    @Inject
    SwissUnihockeySource source;

    @Inject
    Notifications notifications;

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    // ------------------------------------------------------------------ Sync

    /** Triggers the sync by hand (coach, admin; E-84). */
    @Transactional
    public SyncResultDto sync(CurrentUser user, UUID teamId) {
        user.checkAllTeams();
        return sync(teamId);
    }

    /**
     * Syncs one team in the current season with all its links (SUH-11). Also used by the daily run. If swiss
     * unihockey fails for one link, nothing changes for the team (502, SUH-09); the failure is noted on the links
     * (SUH-13).
     */
    @Transactional
    public SyncResultDto sync(UUID teamId) {
        Team team = find(teamId);
        Season season = Season.currentOrFail();
        List<SuLink> links = SuLink.of(team.id, season.id);
        if (links.isEmpty()) {
            throw DomainError.conflict("Nicht verknüpft",
                    "«" + name(team) + "» ist nicht mit swiss unihockey verknüpft.", "SUH-02");
        }
        if (!team.belongsTo(season)) {
            throw DomainError.conflict("Nicht in der aktuellen Saison",
                    "«" + name(team) + "» gehört nicht zur Saison " + season.name + ".", "SUH-03");
        }
        // Two syncs of the same team run one after the other, never at the same time.
        em.lock(team, LockModeType.PESSIMISTIC_WRITE);
        Instant now = clock.now();
        List<SuMatch> delivered = new ArrayList<>();
        // Which name the own team has in the matches of each link (it can differ per link)
        Map<Integer, String> ownTeams = new HashMap<>();
        try {
            for (SuLink link : links) {
                List<SuMatch> matches = source.matches(link.suTeamId, season.startDate.getYear());
                String own = SyncRules.ownTeam(matches, team.nameIn(season));
                for (SuMatch match : matches) {
                    if (!ownTeams.containsKey(match.suMatchId())) {
                        ownTeams.put(match.suMatchId(), own);
                        delivered.add(match);
                    }
                }
            }
        } catch (SuUnavailable e) {
            LOG.warnf("swiss unihockey sync for team %s failed: %s", team.id, e.getMessage());
            List<UUID> ids = links.stream().map(link -> link.id).toList();
            QuarkusTransaction.requiringNew().run(() -> SuLink.update(
                    "lastSyncAt = ?1, lastSyncOk = false, lastSyncError = ?2 where id in ?3", now, FAILED, ids));
            throw new DomainError(502, "swiss unihockey nicht erreichbar", UNAVAILABLE, "SUH-09");
        }
        SyncResultDto result = apply(team, season, delivered, ownTeams);
        for (SuLink link : links) {
            link.lastSyncAt = now;
            link.lastSyncOk = true;
            link.lastSyncError = null;
        }
        return result;
    }

    /** IDs of the linked teams of the current season, for the daily run. */
    @Transactional
    public List<UUID> linkedTeams() {
        return em.createQuery("""
                        select ts.team.id from TeamSeason ts
                        where ts.season.current = true and exists (
                            select l.id from SuLink l where l.team.id = ts.team.id and l.season.id = ts.season.id)
                        order by ts.name, ts.team.id""", UUID.class)
                .getResultList();
    }

    private SyncResultDto apply(Team team, Season season, List<SuMatch> delivered, Map<Integer, String> ownTeams) {
        Instant now = clock.now();
        Duration matchDuration = Duration.ofMinutes(team.matchDurationMinutes);
        Map<Integer, SuMatch> byId = new LinkedHashMap<>();
        delivered.stream().filter(m -> SyncRules.inSeason(m.startTime(), season.startDate, season.endDate))
                .forEach(m -> byId.put(m.suMatchId(), m));

        List<Match> previous = em.createQuery("""
                        select s from Match s join s.event t join t.teams tm
                        where s.source = :source and t.season.id = :season and tm.id = :team
                        order by s.startTime, s.id""", Match.class)
                .setParameter("source", MatchSource.SWISS_UNIHOCKEY).setParameter("season", season.id)
                .setParameter("team", team.id).getResultList();
        Map<Integer, Match> existing = new LinkedHashMap<>();
        previous.forEach(m -> existing.put(m.suMatchId, m));
        Set<Integer> ofOtherTeams = ofOtherTeams(byId.keySet(), existing.keySet());

        // Existing imported match days by day and hall, so that new matches can join them (SUH-04)
        Map<SyncRules.Key, Event> matchDays = new LinkedHashMap<>();
        previous.forEach(m -> matchDays.putIfAbsent(key(m.event), m.event));

        Counts counts = new Counts();
        Set<Event> createdMatchDays = new HashSet<>();
        Set<Event> toRecalculate = new LinkedHashSet<>();
        Map<Event, List<String>> changeTexts = new LinkedHashMap<>();
        Map<Event, Instant> oldStart = new LinkedHashMap<>();
        List<SuMatch> newMatches = new ArrayList<>();
        Map<Event, List<Move>> moves = new LinkedHashMap<>();
        List<Match> changedMatches = new ArrayList<>();

        for (SuMatch suMatch : byId.values()) {
            if (ofOtherTeams.contains(suMatch.suMatchId())) {
                // The match already belongs to a match day of another team (e.g. a derby): it stays there.
                continue;
            }
            Match match = existing.remove(suMatch.suMatchId());
            if (match == null) {
                newMatches.add(suMatch);
                continue;
            }
            Event matchDay = match.event;
            SyncRules.Side side = SyncRules.side(suMatch, ownTeams.get(suMatch.suMatchId()));
            SyncRules.MatchState old = new SyncRules.MatchState(match.opponent, match.startTime, match.home,
                    matchDay.location == null ? null : matchDay.location.name);
            SyncRules.MatchState incoming = new SyncRules.MatchState(side.opponent(), suMatch.startTime(), side.home(),
                    suMatch.hall());
            boolean significant = SyncRules.significantlyChanged(old, incoming);
            // The sync only takes over the result if swiss unihockey delivers one (SUH-08, O-47)
            boolean result = suMatch.result() != null && !suMatch.result().equals(match.result);
            if (!significant && !result && !match.missingAtSu) {
                counts.unchanged++;
                continue;
            }
            counts.changed++;
            if (significant) {
                if (matchDay.status == EventStatus.PLANNED && !matchDay.isOver(now)) {
                    oldStart.putIfAbsent(matchDay, matchDay.start);
                    changeTexts.computeIfAbsent(matchDay, k -> new ArrayList<>()).add(SyncRules.change(old, incoming));
                    // Warning for coaches and admins until one of them confirms the change (SUH-12)
                    match.noteSuChange(now, old.hall());
                    changedMatches.add(match);
                }
                SyncRules.Key target = SyncRules.Key.of(suMatch.startTime(), suMatch.hall());
                if (!target.equals(key(matchDay))) {
                    // Collect first, then move: this way a whole match day moves together (SUH-04, SUH-06).
                    moves.computeIfAbsent(matchDay, k -> new ArrayList<>()).add(new Move(match, suMatch, target));
                }
                toRecalculate.add(matchDay);
            }
            match.opponent = incoming.opponent();
            match.startTime = incoming.startTime();
            match.home = incoming.home();
            if (result) {
                match.result = suMatch.result();
            }
            match.missingAtSu = false;
            counts.affected.add(matchDay.id);
        }

        // Move matches to another day or another hall (SUH-04, SUH-05, SUH-06)
        moves.forEach((matchDay, list) -> move(team, season, matchDay, list, matchDays, createdMatchDays,
                toRecalculate, counts));

        // No longer delivered: mark, do not delete. The coach decides (docs/states.md).
        for (Match match : existing.values()) {
            counts.missing++;
            if (!match.missingAtSu) {
                match.missingAtSu = true;
                counts.affected.add(match.event.id);
            }
        }

        // New matches: join an existing match day on the same day in the same hall, otherwise a new one (SUH-04)
        SyncRules.groupByDayAndHall(newMatches).forEach((key, group) -> {
            Event matchDay = matchDays.get(key);
            if (matchDay == null) {
                matchDay = newMatchDay(team, season, group.get(0));
                createdMatchDays.add(matchDay);
                matchDays.put(key, matchDay);
            }
            for (SuMatch suMatch : group) {
                SyncRules.Side side = SyncRules.side(suMatch, ownTeams.get(suMatch.suMatchId()));
                Match match = Match.create(matchDay);
                match.source = MatchSource.SWISS_UNIHOCKEY;
                match.suMatchId = suMatch.suMatchId();
                match.opponent = side.opponent();
                match.home = side.home();
                match.startTime = suMatch.startTime();
                match.result = suMatch.result();
                match.persist();
                counts.created++;
            }
            toRecalculate.add(matchDay);
            counts.affected.add(matchDay.id);
        });

        // Start and end of the affected match days from their matches
        for (Event matchDay : toRecalculate) {
            List<Instant> times = Match.of(matchDay.id).stream().map(m -> m.startTime).toList();
            if (times.isEmpty()) {
                continue;
            }
            SyncRules.Period period = SyncRules.period(times, matchDuration);
            if (!createdMatchDays.contains(matchDay)) {
                matchDay.rescheduled = EventRules.rescheduled(matchDay.rescheduled, matchDay.start, period.start());
            }
            matchDay.start = period.start();
            matchDay.end = period.end();
        }

        // swiss unihockey changed a match back to what it was before: nothing left to warn about (SUH-12)
        em.flush();
        for (Match match : previous) {
            if (match.suChangedAt != null && match.suChange() == null) {
                match.clearSuChange();
            }
        }
        // Notify the team about changed matches (BEN-06)
        List<Message> messages = new ArrayList<>();
        changeTexts.forEach((matchDay, texts) -> {
            String text = SyncRules.matchChangedText(matchDay.title, oldStart.get(matchDay), texts);
            EventNotifications.members(matchDay)
                    .forEach(p -> messages.add(new Message(NotificationType.MATCH_CHANGED, p, matchDay.id, text)));
        });
        notifications.send(messages);

        LOG.infof("swiss unihockey sync team %s: %d new, %d changed, %d no longer delivered, %d unchanged",
                team.id, counts.created, counts.changed, counts.missing, counts.unchanged);
        return new SyncResultDto(counts.created, counts.changed, counts.missing, counts.unchanged,
                List.copyOf(counts.affected));
    }

    /** A match that moves to another day or another hall. */
    private record Move(Match match, SuMatch delivered, SyncRules.Key target) {
    }

    /**
     * Moves the matches of a match day that change to another day or another hall.
     * <p>
     * If no match stays on the previous day in the previous hall, the match day itself moves along and keeps
     * meeting point, notes, responses and call-up (SUH-06). It moves to where most of its matches go (on a tie,
     * to where the earliest match takes place), provided there is no other match day there yet. The remaining
     * matches join the match day at their target or a new one. This way a day and a hall always belong to at
     * most one match day (SUH-04).
     */
    private static void move(Team team, Season season, Event matchDay, List<Move> list,
                                 Map<SyncRules.Key, Event> matchDays, Set<Event> createdMatchDays,
                                 Set<Event> toRecalculate, Counts counts) {
        Map<SyncRules.Key, List<Move>> byTarget = new LinkedHashMap<>();
        list.stream()
                .sorted(Comparator.comparing((Move move) -> move.delivered().startTime())
                        .thenComparing(move -> move.delivered().suMatchId()))
                .forEach(move -> byTarget.computeIfAbsent(move.target(), k -> new ArrayList<>()).add(move));

        SyncRules.Key matchDayTarget = null;
        if (Match.count("event.id", matchDay.id) == list.size()) {
            int most = 0;
            for (var entry : byTarget.entrySet()) {
                Event atTarget = matchDays.get(entry.getKey());
                if ((atTarget == null || atTarget == matchDay) && entry.getValue().size() > most) {
                    most = entry.getValue().size();
                    matchDayTarget = entry.getKey();
                }
            }
        }
        if (matchDayTarget != null) {
            matchDays.remove(key(matchDay), matchDay);
            setLocation(matchDay, byTarget.get(matchDayTarget).get(0).delivered());
            matchDays.put(matchDayTarget, matchDay);
        }
        toRecalculate.add(matchDay);
        for (var entry : byTarget.entrySet()) {
            if (entry.getKey().equals(matchDayTarget)) {
                continue;
            }
            Event target = matchDays.get(entry.getKey());
            if (target == null) {
                target = newMatchDay(team, season, entry.getValue().get(0).delivered());
                createdMatchDays.add(target);
                matchDays.put(entry.getKey(), target);
            }
            for (Move move : entry.getValue()) {
                move.match().event = target;
            }
            toRecalculate.add(target);
            counts.affected.add(target.id);
        }
    }

    private static final class Counts {
        int created;
        int changed;
        int missing;
        int unchanged;
        final Set<UUID> affected = new LinkedHashSet<>();
    }

    /** Delivered match IDs that are not ours but already belong to a match day of another team. */
    private Set<Integer> ofOtherTeams(Set<Integer> deliveredIds, Set<Integer> ownIds) {
        List<Integer> unknown = deliveredIds.stream().filter(id -> !ownIds.contains(id)).toList();
        if (unknown.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(em.createQuery("select s.suMatchId from Match s where s.suMatchId in :ids", Integer.class)
                .setParameter("ids", unknown).getResultList());
    }

    private static SyncRules.Key key(Event matchDay) {
        return SyncRules.Key.of(matchDay.start, matchDay.location == null ? null : matchDay.location.name);
    }

    private static Event newMatchDay(Team team, Season season, SuMatch first) {
        Event matchDay = Event.create();
        matchDay.season = season;
        matchDay.type = EventType.MATCH_DAY;
        matchDay.title = SyncRules.TITLE;
        matchDay.start = first.startTime();
        matchDay.end = first.startTime().plus(Duration.ofMinutes(team.matchDurationMinutes));
        setLocation(matchDay, first);
        // The team usually meets at the same place (AUF-32); the time stays open until a coach sets it.
        matchDay.meetingPointLocation = team.meetingPointLocation;
        // New match days start with the defaults of the variant «swiss unihockey Spieltag» (VAR-09)
        ch.stvbero.platform.attendance.variant.EventVariant.<ch.stvbero.platform.attendance.variant.EventVariant>find(
                        "teamId = ?1 and kind = ?2", team.id, ch.stvbero.platform.attendance.variant.VariantKind.SU_MATCH_DAY)
                .firstResultOptional().ifPresent(variant -> {
                    matchDay.responseDeadlineHours = variant.responseDeadlineHours;
                    matchDay.attendanceCheck = variant.attendanceCheck;
                });
        matchDay.teams.add(team);
        matchDay.persist();
        return matchDay;
    }

    /** Venue from swiss unihockey, with a Google Maps link (SUH-07). */
    private static void setLocation(Event matchDay, SuMatch suMatch) {
        Location location = new Location();
        location.name = SyncRules.truncate(suMatch.hall(), 120);
        location.address = suMatch.location() == null ? null : SyncRules.truncate(suMatch.location(), 200);
        location.mapsUrl = SyncRules.mapsUrl(suMatch);
        location.latitude = suMatch.latitude();
        location.longitude = suMatch.longitude();
        matchDay.location = location;
    }

    private static Team find(UUID id) {
        return Team.<Team>findByIdOptional(id)
                .orElseThrow(() -> DomainError.notFound("Dieses Team gibt es nicht."));
    }

    /** Name in the current season, otherwise the most recently used one. */
    private static String name(Team team) {
        return Season.current().flatMap(team::in).map(ts -> ts.name)
                .orElseGet(() -> team.seasons.stream().max(Comparator.comparing((TeamSeason ts) -> ts.season.startDate))
                        .map(ts -> ts.name).orElse(""));
    }
}
