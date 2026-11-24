package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.attendance.absence.AbsenceService;
import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.team.TeamMembership;
import ch.stvbero.platform.core.team.TeamRole;
import ch.stvbero.platform.core.team.TeamSeason;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Calendar view (KAL): events of the chosen teams in a period, past and upcoming together, plus the own
 * absences. Players see their own teams together, coaches start with the teams they coach and, like admins, may
 * show every team (RR-03, E-69).
 */
@ApplicationScoped
public class EventCalendarService {

    @Inject
    EntityManager em;

    /** The module that knows the league table, if there is one (AUF-34). */
    @Inject
    Instance<Standings.Source> league;

    @Inject
    BackendClock clock;

    @Inject
    AbsenceService absences;

    public CalendarViewDto view(CurrentUser user, LocalDate from, LocalDate to, List<UUID> chosen, boolean all) {
        CalendarRules.checkPeriod(from, to);
        Season season = Season.currentOrFail();
        Map<UUID, String> names = new LinkedHashMap<>();
        TeamSeason.<TeamSeason>list("season.id", season.id).stream()
                .sorted(Comparator.comparing((TeamSeason ts) -> ts.name, String.CASE_INSENSITIVE_ORDER))
                .forEach(ts -> names.put(ts.team.id, ts.name));
        Map<UUID, TeamRole> own = new LinkedHashMap<>();
        TeamMembership.<TeamMembership>list("person.id = ?1 and season.id = ?2", user.personId(), season.id)
                .forEach(m -> own.put(m.team.id, m.teamRole));
        // Order of the defaults as in the team list (by name)
        Map<UUID, TeamRole> ownByName = new LinkedHashMap<>();
        names.keySet().stream().filter(own::containsKey).forEach(id -> ownByName.put(id, own.get(id)));

        List<UUID> defaults = CalendarRules.defaults(ownByName, user.coach(), names.keySet());
        Set<UUID> selection = CalendarRules.selection(chosen, all, user.allTeams(), own.keySet(), names.keySet(),
                defaults);
        List<CalendarViewDto.Team> teams = names.entrySet().stream()
                .filter(e -> user.allTeams() || own.containsKey(e.getKey()))
                .map(e -> new CalendarViewDto.Team(e.getKey(), e.getValue(), own.get(e.getKey()))).toList();

        List<EventSummaryDto> events = List.of();
        if (!selection.isEmpty()) {
            StringBuilder jpql = new StringBuilder("""
                    select distinct t from Event t left join fetch t.teams join fetch t.season
                    where t.start < :until and t.end > :since
                      and exists (select x.id from Event t2 join t2.teams x where t2 = t and x.id in :teams)
                    """);
            if (!user.allTeams()) {
                // Players only see events of teams they belong to in the season of the event (TER-13, RR-02)
                jpql.append("""
                         and exists (select z.id from TeamMembership z
                                     where z.person.id = :person and z.season = t.season and z.team member of t.teams)
                        """);
            }
            jpql.append(" order by t.start, t.id");
            TypedQuery<Event> query = em.createQuery(jpql.toString(), Event.class)
                    .setParameter("since", from.atStartOfDay(BackendClock.ZONE).toInstant())
                    .setParameter("until", to.plusDays(1).atStartOfDay(BackendClock.ZONE).toInstant())
                    .setParameter("teams", selection);
            if (!user.allTeams()) {
                query.setParameter("person", user.personId());
            }
            List<Event> found = query.getResultList();
            EventReader reader = new EventReader(em, user, found, clock.now(), Standings.sourceOf(league));
            events = found.stream().map(reader::summary).toList();
        }
        return new CalendarViewDto(from, to, teams, defaults, List.copyOf(selection), all, user.allTeams(), events,
                absences.inPeriod(user.personId(), from, to));
    }
}
