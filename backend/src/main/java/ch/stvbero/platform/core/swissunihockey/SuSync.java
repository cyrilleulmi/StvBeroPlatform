package ch.stvbero.platform.core.swissunihockey;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.notification.NotificationType;
import ch.stvbero.platform.core.notification.Notifications;
import ch.stvbero.platform.core.notification.Message;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.team.TeamDto;
import ch.stvbero.platform.core.team.TeamSeason;
import ch.stvbero.platform.core.event.Location;
import ch.stvbero.platform.core.event.MatchSource;
import ch.stvbero.platform.core.event.Match;
import ch.stvbero.platform.core.event.Event;
import ch.stvbero.platform.core.event.EventNotifications;
import ch.stvbero.platform.core.event.EventRules;
import ch.stvbero.platform.core.event.EventStatus;
import ch.stvbero.platform.core.event.EventType;
import ch.stvbero.platform.core.clock.BackendClock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Abgleich der Spiele eines Teams mit swiss unihockey (SUH-03 bis SUH-10).
 * <ul>
 *   <li>Neue Spiele werden zu Spieltagen: gleicher Tag und gleiche Halle ergeben einen Spieltag (SUH-04).</li>
 *   <li>Geänderte Spiele werden übernommen. Treffpunkt, Hinweise, Antworten und Aufgebot bleiben (SUH-05,
 *       SUH-06); das Team wird benachrichtigt (BEN-06).</li>
 *   <li>Spiele, die swiss unihockey nicht mehr liefert, werden markiert, nicht gelöscht.</li>
 *   <li>Von Hand erfasste Spieltage und Spiele bleiben unberührt (SUH-10).</li>
 *   <li>Ist swiss unihockey nicht erreichbar, ändert sich nichts (502, SUH-09).</li>
 * </ul>
 */
@ApplicationScoped
public class SuSync {

    private static final Logger LOG = Logger.getLogger(SuSync.class);

    public static final String UNAVAILABLE = "swiss unihockey ist im Moment nicht erreichbar. Es wurde nichts "
            + "geändert. Der nächste Abgleich versucht es wieder.";

    @Inject
    SwissUnihockeySource source;

    @Inject
    Notifications notifications;

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    // ------------------------------------------------------------------ Verknüpfen (SUH-02)

    /** Team mit swiss unihockey verknüpfen ({@code null} löst die Verknüpfung). Nur Admins. */
    @Transactional
    public TeamDto link(CurrentUser user, UUID teamId, Integer suTeamId) {
        user.checkAdmin();
        Team team = find(teamId);
        if (suTeamId != null && suTeamId <= 0) {
            throw DomainError.invalidField("suTeamId", "Die Team-ID ist eine positive Zahl.");
        }
        if (suTeamId != null) {
            Team.<Team>find("suTeamId = ?1 and id <> ?2", suTeamId, team.id).firstResultOptional().ifPresent(t -> {
                throw DomainError.conflict("Schon verknüpft", "Die Team-ID " + suTeamId + " ist schon mit «"
                        + name(t) + "» verknüpft.", "SUH-02");
            });
        }
        team.suTeamId = suTeamId;
        return new TeamDto(team.id, name(team), team.suTeamId, null);
    }

    // ------------------------------------------------------------------ Abgleich

    /** Abgleich von Hand auslösen (Admin). */
    @Transactional
    public SyncResultDto sync(CurrentUser user, UUID teamId) {
        user.checkAdmin();
        return sync(teamId);
    }

    /** Ein Team abgleichen, in der aktuellen Saison. Wird auch vom täglichen Lauf verwendet. */
    @Transactional
    public SyncResultDto sync(UUID teamId) {
        Team team = find(teamId);
        Season season = Season.currentOrFail();
        if (team.suTeamId == null) {
            throw DomainError.conflict("Nicht verknüpft",
                    "«" + name(team) + "» ist nicht mit swiss unihockey verknüpft.", "SUH-02");
        }
        if (!team.belongsTo(season)) {
            throw DomainError.conflict("Nicht in der aktuellen Saison",
                    "«" + name(team) + "» gehört nicht zur Saison " + season.name + ".", "SUH-03");
        }
        // Zwei Abgleiche desselben Teams laufen nacheinander, nie gleichzeitig.
        em.lock(team, LockModeType.PESSIMISTIC_WRITE);
        List<SuMatch> delivered;
        try {
            delivered = source.matches(team.suTeamId, season.startDate.getYear());
        } catch (SuUnavailable e) {
            LOG.warnf("Abgleich swiss unihockey für Team %s (SU %d) fehlgeschlagen: %s", team.id, team.suTeamId,
                    e.getMessage());
            throw new DomainError(502, "swiss unihockey nicht erreichbar", UNAVAILABLE, "SUH-09");
        }
        return apply(team, season, delivered);
    }

    /** IDs der verknüpften Teams der aktuellen Saison, für den täglichen Lauf. */
    @Transactional
    public List<UUID> linkedTeams() {
        return em.createQuery("""
                        select ts.team.id from TeamSeason ts
                        where ts.season.current = true and ts.team.suTeamId is not null
                        order by ts.name, ts.team.id""", UUID.class)
                .getResultList();
    }

    private SyncResultDto apply(Team team, Season season, List<SuMatch> delivered) {
        Instant now = clock.now();
        String own = SyncRules.ownTeam(delivered, team.nameIn(season));
        Map<Integer, SuMatch> byId = new LinkedHashMap<>();
        delivered.stream().filter(s -> SyncRules.inSeason(s.startTime(), season.startDate, season.endDate))
                .forEach(s -> byId.put(s.suMatchId(), s));

        List<Match> previous = em.createQuery("""
                        select s from Match s join s.event t join t.teams tm
                        where s.source = :source and t.season.id = :season and tm.id = :team
                        order by s.startTime, s.id""", Match.class)
                .setParameter("source", MatchSource.SWISS_UNIHOCKEY).setParameter("season", season.id)
                .setParameter("team", team.id).getResultList();
        Map<Integer, Match> existing = new LinkedHashMap<>();
        previous.forEach(s -> existing.put(s.suMatchId, s));
        Set<Integer> atOtherTeams = atOtherTeams(byId.keySet(), existing.keySet());

        // Bestehende importierte Spieltage nach Tag und Halle, damit neue Spiele dazukommen (SUH-04)
        Map<SyncRules.Key, Event> matchDays = new LinkedHashMap<>();
        previous.forEach(s -> matchDays.putIfAbsent(key(s.event), s.event));

        Counts z = new Counts();
        Set<Event> newCreated = new HashSet<>();
        Set<Event> periodNew = new LinkedHashSet<>();
        Map<Event, List<String>> changeTexts = new LinkedHashMap<>();
        Map<Event, Instant> oldStart = new LinkedHashMap<>();
        List<SuMatch> newOnes = new ArrayList<>();
        Map<Event, List<Move>> moves = new LinkedHashMap<>();

        for (SuMatch g : byId.values()) {
            if (atOtherTeams.contains(g.suMatchId())) {
                // Spiel gehört schon zu einem Spieltag eines anderen Teams (z.B. Derby): dort bleibt es.
                continue;
            }
            Match s = existing.remove(g.suMatchId());
            if (s == null) {
                newOnes.add(g);
                continue;
            }
            Event t = s.event;
            SyncRules.Side side = SyncRules.side(g, own);
            SyncRules.MatchState old = new SyncRules.MatchState(s.opponent, s.startTime, s.home,
                    t.location == null ? null : t.location.name);
            SyncRules.MatchState fresh = new SyncRules.MatchState(side.opponent(), g.startTime(), side.home(),
                    g.hall());
            boolean significant = SyncRules.significantlyChanged(old, fresh);
            // Das Resultat übernimmt der Abgleich nur, wenn swiss unihockey eines liefert (SUH-08, O-47)
            boolean result = g.result() != null && !g.result().equals(s.result);
            if (!significant && !result && !s.missingAtSu) {
                z.unchanged++;
                continue;
            }
            z.changed++;
            if (significant) {
                if (t.status == EventStatus.PLANNED && !t.isOver(now)) {
                    oldStart.putIfAbsent(t, t.start);
                    changeTexts.computeIfAbsent(t, k -> new ArrayList<>()).add(SyncRules.change(old, fresh));
                }
                SyncRules.Key target = SyncRules.Key.of(g.startTime(), g.hall());
                if (!target.equals(key(t))) {
                    // Erst sammeln, dann umziehen: So zieht ein ganzer Spieltag gemeinsam um (SUH-04, SUH-06).
                    moves.computeIfAbsent(t, k -> new ArrayList<>()).add(new Move(s, g, target));
                }
                periodNew.add(t);
            }
            s.opponent = fresh.opponent();
            s.startTime = fresh.startTime();
            s.home = fresh.home();
            if (result) {
                s.result = g.result();
            }
            s.missingAtSu = false;
            z.affected.add(t.id);
        }

        // Spiele an einen anderen Tag oder in eine andere Halle verschieben (SUH-04, SUH-05, SUH-06)
        moves.forEach((t, list) -> move(team, season, t, list, matchDays, newCreated, periodNew, z));

        // Nicht mehr geliefert: markieren, nicht löschen. Die Coach entscheidet (zustaende.md).
        for (Match s : existing.values()) {
            z.missing++;
            if (!s.missingAtSu) {
                s.missingAtSu = true;
                z.affected.add(s.event.id);
            }
        }

        // Neue Spiele: zu einem bestehenden Spieltag am selben Tag in derselben Halle, sonst ein neuer (SUH-04)
        SyncRules.groupByDayAndHall(newOnes).forEach((key, group) -> {
            Event t = matchDays.get(key);
            if (t == null) {
                t = newMatchDay(team, season, group.get(0));
                newCreated.add(t);
                matchDays.put(key, t);
            }
            for (SuMatch g : group) {
                SyncRules.Side side = SyncRules.side(g, own);
                Match s = Match.create(t);
                s.source = MatchSource.SWISS_UNIHOCKEY;
                s.suMatchId = g.suMatchId();
                s.opponent = side.opponent();
                s.home = side.home();
                s.startTime = g.startTime();
                s.result = g.result();
                s.persist();
                z.fresh++;
            }
            periodNew.add(t);
            z.affected.add(t.id);
        });

        // Beginn und Ende der betroffenen Spieltage aus ihren Spielen
        for (Event t : periodNew) {
            List<Instant> times = Match.of(t.id).stream().map(s -> s.startTime).toList();
            if (times.isEmpty()) {
                continue;
            }
            SyncRules.Period zr = SyncRules.period(times);
            if (!newCreated.contains(t)) {
                t.rescheduled = EventRules.rescheduled(t.rescheduled, t.start, zr.start());
            }
            t.start = zr.start();
            t.end = zr.end();
        }

        // Team über geänderte Spiele benachrichtigen (BEN-06)
        List<Message> messages = new ArrayList<>();
        changeTexts.forEach((t, texts) -> {
            String text = SyncRules.matchChangedText(t.title, oldStart.get(t), texts);
            EventNotifications.members(t)
                    .forEach(p -> messages.add(new Message(NotificationType.MATCH_CHANGED, p, t.id, text)));
        });
        notifications.send(messages);

        LOG.infof("Abgleich swiss unihockey Team %s: %d neu, %d geändert, %d nicht mehr vorhanden, %d unverändert",
                team.id, z.fresh, z.changed, z.missing, z.unchanged);
        return new SyncResultDto(z.fresh, z.changed, z.missing, z.unchanged,
                List.copyOf(z.affected));
    }

    /** Ein Spiel, das an einen anderen Tag oder in eine andere Halle wechselt. */
    private record Move(Match match, SuMatch delivered, SyncRules.Key target) {
    }

    /**
     * Verschiebt die Spiele eines Spieltags, die an einen anderen Tag oder in eine andere Halle wechseln.
     * <p>
     * Bleibt kein Spiel am bisherigen Tag in der bisherigen Halle, zieht der Spieltag selbst mit und behält
     * Treffpunkt, Hinweise, Antworten und Aufgebot (SUH-06). Er zieht dorthin, wo die meisten seiner Spiele
     * hingehen (bei Gleichstand dorthin, wo das früheste Spiel stattfindet), sofern dort noch kein anderer
     * Spieltag ist. Die übrigen Spiele kommen zum Spieltag am Ziel oder in einen neuen. Ein Tag und eine Halle
     * gehören so immer zu höchstens einem Spieltag (SUH-04).
     */
    private static void move(Team team, Season season, Event t, List<Move> list,
                                 Map<SyncRules.Key, Event> matchDays, Set<Event> newCreated,
                                 Set<Event> periodNew, Counts z) {
        Map<SyncRules.Key, List<Move>> byTarget = new LinkedHashMap<>();
        list.stream()
                .sorted(Comparator.comparing((Move u) -> u.delivered().startTime())
                        .thenComparing(u -> u.delivered().suMatchId()))
                .forEach(u -> byTarget.computeIfAbsent(u.target(), k -> new ArrayList<>()).add(u));

        SyncRules.Key moveAlong = null;
        if (Match.count("event.id", t.id) == list.size()) {
            int most = 0;
            for (var e : byTarget.entrySet()) {
                Event there = matchDays.get(e.getKey());
                if ((there == null || there == t) && e.getValue().size() > most) {
                    most = e.getValue().size();
                    moveAlong = e.getKey();
                }
            }
        }
        if (moveAlong != null) {
            matchDays.remove(key(t), t);
            setLocation(t, byTarget.get(moveAlong).get(0).delivered());
            matchDays.put(moveAlong, t);
        }
        periodNew.add(t);
        for (var e : byTarget.entrySet()) {
            if (e.getKey().equals(moveAlong)) {
                continue;
            }
            Event target = matchDays.get(e.getKey());
            if (target == null) {
                target = newMatchDay(team, season, e.getValue().get(0).delivered());
                newCreated.add(target);
                matchDays.put(e.getKey(), target);
            }
            for (Move u : e.getValue()) {
                u.match().event = target;
            }
            periodNew.add(target);
            z.affected.add(target.id);
        }
    }

    private static final class Counts {
        int fresh;
        int changed;
        int missing;
        int unchanged;
        final Set<UUID> affected = new LinkedHashSet<>();
    }

    private Set<Integer> atOtherTeams(Set<Integer> delivered, Set<Integer> own) {
        List<Integer> unknown = delivered.stream().filter(id -> !own.contains(id)).toList();
        if (unknown.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(em.createQuery("select s.suMatchId from Match s where s.suMatchId in :ids", Integer.class)
                .setParameter("ids", unknown).getResultList());
    }

    private static SyncRules.Key key(Event t) {
        return SyncRules.Key.of(t.start, t.location == null ? null : t.location.name);
    }

    private static Event newMatchDay(Team team, Season season, SuMatch first) {
        Event t = Event.create();
        t.season = season;
        t.type = EventType.MATCH_DAY;
        t.title = SyncRules.TITLE;
        t.start = first.startTime();
        t.end = first.startTime().plus(SyncRules.MATCH_DURATION);
        setLocation(t, first);
        t.teams.add(team);
        t.persist();
        return t;
    }

    /** Spielort aus swiss unihockey, mit Google-Maps-Link (SUH-07). */
    private static void setLocation(Event t, SuMatch g) {
        Location o = new Location();
        o.name = SyncRules.truncate(g.hall(), 120);
        o.address = g.location() == null ? null : SyncRules.truncate(g.location(), 200);
        o.mapsUrl = SyncRules.mapsUrl(g);
        o.latitude = g.latitude();
        o.longitude = g.longitude();
        t.location = o;
    }

    private static Team find(UUID id) {
        return Team.<Team>findByIdOptional(id)
                .orElseThrow(() -> DomainError.notFound("Dieses Team gibt es nicht."));
    }

    /** Name in der aktuellen Saison, sonst der zuletzt verwendete. */
    private static String name(Team team) {
        return Season.current().flatMap(team::in).map(ts -> ts.name)
                .orElseGet(() -> team.seasons.stream().max(Comparator.comparing((TeamSeason ts) -> ts.season.startDate))
                        .map(ts -> ts.name).orElse(""));
    }
}
