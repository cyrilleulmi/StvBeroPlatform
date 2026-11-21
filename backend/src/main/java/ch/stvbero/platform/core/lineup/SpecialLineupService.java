package ch.stvbero.platform.core.lineup;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.error.Unique;
import ch.stvbero.platform.core.event.CallUp;
import ch.stvbero.platform.core.event.Event;
import ch.stvbero.platform.core.event.EventRules;
import ch.stvbero.platform.core.event.EventType;
import ch.stvbero.platform.core.event.ResponseStatus;
import ch.stvbero.platform.core.line.LineRules;
import ch.stvbero.platform.core.person.PersonSummary;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.team.TeamMembership;
import ch.stvbero.platform.core.team.TeamRole;
import ch.stvbero.platform.core.variant.Variants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Special line-ups of a team (LIN-13 to LIN-17), e.g. power play and penalty kill. Members of the team see them;
 * coaches (of any team, RR-03) and admins change them. Enforced here in the backend (RR-07).
 */
@ApplicationScoped
public class SpecialLineupService {

    private static final String NAME_INDEX = "special_lineup_name";

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    private record Context(Team team, Season season, Map<UUID, PersonSummary> players, TeamRole myTeamRole) {
    }

    private record Stored(UUID id, String name) {
    }

    private record StoredPlace(int position, String label, UUID person) {
    }

    // ------------------------------------------------------------------ Team

    public SpecialLineupDto.OfTeam get(CurrentUser user, UUID teamId) {
        Context c = context(user, teamId);
        LineRules.checkCanView(user.allTeams(), c.myTeamRole());
        List<SpecialLineupDto> lineups = new ArrayList<>();
        Map<UUID, List<StoredPlace>> places = places(lineups(c.team().id, c.season().id).stream().map(Stored::id).toList());
        for (Stored lineup : lineups(c.team().id, c.season().id)) {
            lineups.add(new SpecialLineupDto(lineup.id(), lineup.name(),
                    places.getOrDefault(lineup.id(), List.of()).stream()
                            .map(p -> new SpecialLineupDto.Place(p.position(), p.label(), c.players().get(p.person())))
                            .toList()));
        }
        return new SpecialLineupDto.OfTeam(c.team().id, user.allTeams() && !c.season().isArchived(), lineups,
                c.players().values().stream().sorted(PersonSummary.ORDER).toList());
    }

    @Transactional
    public SpecialLineupDto create(CurrentUser user, UUID teamId, SpecialLineupDto.Input input) {
        Context c = editable(user, teamId);
        SpecialLineupRules.checkRoom(lineups(c.team().id, c.season().id).size());
        checkPlaces(input, c);
        UUID id = UUID.randomUUID();
        Number order = (Number) em.createNativeQuery(
                        "select coalesce(max(sort_order), 0) + 1 from special_lineup where team_id = ? and season_id = ?")
                .setParameter(1, c.team().id).setParameter(2, c.season().id).getSingleResult();
        save(() -> em.createNativeQuery("""
                        insert into special_lineup (id, team_id, season_id, name, sort_order, created_at)
                        values (?, ?, ?, ?, ?, ?)""")
                .setParameter(1, id).setParameter(2, c.team().id).setParameter(3, c.season().id)
                .setParameter(4, input.name().strip()).setParameter(5, order.intValue()).setParameter(6, clock.now())
                .executeUpdate());
        storePlaces(id, input);
        return dto(id, input.name().strip(), c);
    }

    @Transactional
    public SpecialLineupDto update(CurrentUser user, UUID teamId, UUID lineupId, SpecialLineupDto.Input input) {
        Context c = editable(user, teamId);
        find(c, lineupId);
        checkPlaces(input, c);
        save(() -> em.createNativeQuery("update special_lineup set name = ? where id = ?")
                .setParameter(1, input.name().strip()).setParameter(2, lineupId).executeUpdate());
        em.createNativeQuery("delete from special_lineup_place where lineup_id = ?").setParameter(1, lineupId)
                .executeUpdate();
        storePlaces(lineupId, input);
        return dto(lineupId, input.name().strip(), c);
    }

    @Transactional
    public void delete(CurrentUser user, UUID teamId, UUID lineupId) {
        Context c = editable(user, teamId);
        find(c, lineupId);
        em.createNativeQuery("delete from special_lineup where id = ?").setParameter(1, lineupId).executeUpdate();
    }

    // ------------------------------------------------------------------ Match day

    /**
     * The special line-ups of the teams of a match day with the response of every player and, with a published
     * call-up, whether she is called up (LIN-16). For everyone who may see the match day.
     */
    public SpecialLineupDto.OfEvent ofEvent(CurrentUser user, UUID eventId) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> DomainError.notFound("Diesen Termin gibt es nicht."));
        List<TeamMembership> members = event.teams.isEmpty() ? List.of() : em.createQuery("""
                        select m from TeamMembership m join fetch m.person
                        where m.season.id = :season and m.team.id in :teams""", TeamMembership.class)
                .setParameter("season", event.season.id)
                .setParameter("teams", event.teams.stream().map(team -> team.id).toList()).getResultList();
        Set<TeamRole> mine = new HashSet<>();
        members.stream().filter(m -> m.person.id.equals(user.personId())).forEach(m -> mine.add(m.teamRole));
        EventRules.checkCanView(user.allTeams(), mine);
        if (event.type != EventType.MATCH_DAY) {
            throw DomainError.conflict("Kein Spieltag", "Aufstellungen gibt es nur für Spieltage.", "LIN-16");
        }

        Map<UUID, ResponseStatus> responses = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("select person_id, status from response where event_id = ?")
                .setParameter(1, event.id).getResultList();
        rows.forEach(row -> responses.put((UUID) row[0], ResponseStatus.valueOf((String) row[1])));
        CallUp callUp = Variants.of(event).callUp ? CallUp.findById(event.id) : null;
        boolean published = callUp != null && callUp.publishedAt != null;

        List<SpecialLineupDto.EventLineup> result = new ArrayList<>();
        for (Team team : event.teams) {
            Map<UUID, PersonSummary> players = new HashMap<>();
            members.stream().filter(m -> m.team.id.equals(team.id) && m.teamRole == TeamRole.PLAYER)
                    .forEach(m -> players.put(m.person.id, PersonSummary.of(m.person)));
            List<Stored> lineups = lineups(team.id, event.season.id);
            Map<UUID, List<StoredPlace>> places = places(lineups.stream().map(Stored::id).toList());
            for (Stored lineup : lineups) {
                List<SpecialLineupDto.EventPlace> shown = new ArrayList<>();
                for (StoredPlace place : places.getOrDefault(lineup.id(), List.of())) {
                    PersonSummary person = players.get(place.person());
                    ResponseStatus response = person == null ? null
                            : responses.getOrDefault(person.id(), ResponseStatus.PENDING);
                    boolean calledUp = person != null && published && callUp.personIds.contains(person.id());
                    shown.add(new SpecialLineupDto.EventPlace(place.position(), place.label(), person, response,
                            person != null && published ? calledUp : null,
                            SpecialLineupRules.gap(person != null, response, published, calledUp)));
                }
                result.add(new SpecialLineupDto.EventLineup(lineup.id(), lineup.name(), shown));
            }
        }
        return new SpecialLineupDto.OfEvent(event.id, result);
    }

    // ------------------------------------------------------------------ New season (LIN-17)

    /**
     * Copies the special line-ups of a season into the next one, together with the lines. A place whose player is
     * no longer a player of the team there is emptied.
     */
    public static void carryOver(EntityManager em, Season source, Season target, Instant now) {
        em.flush();
        em.createNativeQuery("""
                        insert into special_lineup (id, team_id, season_id, name, sort_order, created_at)
                        select md5(l.id::text || cast(:target as text))::uuid, l.team_id, :target, l.name, l.sort_order, :now
                        from special_lineup l
                        where l.season_id = :source
                          and exists (select 1 from team_season ts where ts.team_id = l.team_id and ts.season_id = :target)""")
                .setParameter("source", source.id).setParameter("target", target.id).setParameter("now", now)
                .executeUpdate();
        em.createNativeQuery("""
                        insert into special_lineup_place (lineup_id, position, label, person_id)
                        select md5(l.id::text || cast(:target as text))::uuid, p.position, p.label,
                               case when exists (select 1 from team_membership m
                                                 where m.person_id = p.person_id and m.team_id = l.team_id
                                                   and m.season_id = :target and m.team_role = 'PLAYER')
                                    then p.person_id end
                        from special_lineup_place p join special_lineup l on l.id = p.lineup_id
                        where l.season_id = :source
                          and exists (select 1 from team_season ts where ts.team_id = l.team_id and ts.season_id = :target)""")
                .setParameter("source", source.id).setParameter("target", target.id).executeUpdate();
    }

    // ------------------------------------------------------------------ Helpers

    /** Team in the current season with its players and the caller's team role. */
    private Context context(CurrentUser user, UUID teamId) {
        Season season = Season.currentOrFail();
        Team team = Team.<Team>findByIdOptional(teamId)
                .orElseThrow(() -> DomainError.notFound("Dieses Team gibt es nicht."));
        List<TeamMembership> memberships = em.createQuery("""
                        select m from TeamMembership m join fetch m.person
                        where m.team.id = :team and m.season.id = :season""", TeamMembership.class)
                .setParameter("team", team.id).setParameter("season", season.id).getResultList();
        TeamRole mine = memberships.stream().filter(m -> m.person.id.equals(user.personId()))
                .map(m -> m.teamRole).findFirst().orElse(null);
        if (!team.belongsTo(season)) {
            LineRules.checkCanView(user.allTeams(), mine);
            throw DomainError.notFound("Dieses Team gibt es in der Saison " + season.name + " nicht.");
        }
        Map<UUID, PersonSummary> players = new LinkedHashMap<>();
        memberships.stream().filter(m -> m.teamRole == TeamRole.PLAYER)
                .forEach(m -> players.put(m.person.id, PersonSummary.of(m.person)));
        return new Context(team, season, players, mine);
    }

    private Context editable(CurrentUser user, UUID teamId) {
        Context c = context(user, teamId);
        LineRules.checkCanEdit(user.allTeams(), c.myTeamRole());
        c.season().checkWritable();
        return c;
    }

    private void find(Context c, UUID lineupId) {
        if (lineups(c.team().id, c.season().id).stream().noneMatch(l -> l.id().equals(lineupId))) {
            throw DomainError.notFound("Diese Aufstellung gibt es nicht.");
        }
    }

    private static void checkPlaces(SpecialLineupDto.Input input, Context c) {
        SpecialLineupRules.checkPlayers(input.places().stream().map(SpecialLineupDto.PlaceInput::personId).toList(),
                c.players().keySet());
    }

    /** Runs a statement that can violate the unique name (two requests at once, or a name already used). */
    private void save(Runnable statement) {
        try {
            statement.run();
            Unique.save(em, NAME_INDEX, SpecialLineupService::nameTaken);
        } catch (jakarta.persistence.PersistenceException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                        && NAME_INDEX.equalsIgnoreCase(violation.getConstraintName())) {
                    throw nameTaken();
                }
            }
            throw exception;
        }
    }

    private static DomainError nameTaken() {
        return DomainError.ruleViolation("name", "Name vergeben", SpecialLineupRules.NAME_TAKEN, "LIN-13");
    }

    private void storePlaces(UUID lineupId, SpecialLineupDto.Input input) {
        for (int i = 0; i < input.places().size(); i++) {
            SpecialLineupDto.PlaceInput place = input.places().get(i);
            String label = place.label() == null || place.label().isBlank() ? null : place.label().strip();
            em.createNativeQuery("""
                            insert into special_lineup_place (lineup_id, position, label, person_id)
                            values (?, ?, cast(? as varchar), cast(? as uuid))""")
                    .setParameter(1, lineupId).setParameter(2, i + 1).setParameter(3, label)
                    .setParameter(4, place.personId() == null ? null : place.personId().toString())
                    .executeUpdate();
        }
    }

    private SpecialLineupDto dto(UUID id, String name, Context c) {
        return new SpecialLineupDto(id, name, places(List.of(id)).getOrDefault(id, List.of()).stream()
                .map(p -> new SpecialLineupDto.Place(p.position(), p.label(), c.players().get(p.person()))).toList());
    }

    private List<Stored> lineups(UUID teamId, UUID seasonId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select id, name from special_lineup where team_id = ? and season_id = ?
                        order by sort_order, created_at, id""")
                .setParameter(1, teamId).setParameter(2, seasonId).getResultList();
        return rows.stream().map(row -> new Stored((UUID) row[0], (String) row[1])).toList();
    }

    private Map<UUID, List<StoredPlace>> places(List<UUID> lineupIds) {
        Map<UUID, List<StoredPlace>> places = new HashMap<>();
        if (lineupIds.isEmpty()) {
            return places;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select lineup_id, position, label, person_id from special_lineup_place
                        where lineup_id in (:ids) order by lineup_id, position""")
                .setParameter("ids", lineupIds).getResultList();
        for (Object[] row : rows) {
            places.computeIfAbsent((UUID) row[0], key -> new ArrayList<>())
                    .add(new StoredPlace(((Number) row[1]).intValue(), (String) row[2], (UUID) row[3]));
        }
        return places;
    }
}
