package ch.stvbero.platform.core.calendar;

import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.event.Event;
import ch.stvbero.platform.core.event.EventStatus;
import ch.stvbero.platform.core.event.EventType;
import ch.stvbero.platform.core.event.Match;
import ch.stvbero.platform.core.event.ResponseStatus;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.team.TeamSeason;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Calendar subscription (CAL): the personal address and the iCalendar feed behind it. The feed is reached with
 * the token alone, without login, and shows only what the person may see anyway (CAL-06, CAL-08).
 */
@ApplicationScoped
public class CalendarService {

    static final String CALENDAR_NAME = "STV Bero";

    private final SecureRandom random = new SecureRandom();

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    /** Address of the app, for the links to the events in the feed (CAL-03). */
    @ConfigProperty(name = "stv.app-url")
    String appUrl;

    /** The person's address; created on first use (CAL-01). */
    @Transactional
    public CalendarSubscriptionDto mine(UUID personId) {
        em.createNativeQuery("""
                        insert into calendar_subscription (person_id, token, created_at) values (?, ?, ?)
                        on conflict (person_id) do nothing""")
                .setParameter(1, personId).setParameter(2, CalendarRules.newToken(random))
                .setParameter(3, clock.now())
                .executeUpdate();
        return new CalendarSubscriptionDto(CalendarRules.feedPath(token(personId)));
    }

    /** New address; the old one no longer works from now on (CAL-07). */
    @Transactional
    public CalendarSubscriptionDto renew(UUID personId) {
        em.createNativeQuery("""
                        insert into calendar_subscription (person_id, token, created_at) values (?, ?, ?)
                        on conflict (person_id) do update set token = excluded.token, created_at = excluded.created_at""")
                .setParameter(1, personId).setParameter(2, CalendarRules.newToken(random))
                .setParameter(3, clock.now())
                .executeUpdate();
        return new CalendarSubscriptionDto(CalendarRules.feedPath(token(personId)));
    }

    /** The feed for a token; empty if the token is unknown (CAL-06, CAL-07). */
    @Transactional
    public Optional<String> feed(String token) {
        if (!CalendarRules.wellFormed(token)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        List<UUID> persons = em.createNativeQuery("select person_id from calendar_subscription where token = ?")
                .setParameter(1, token).getResultList();
        if (persons.isEmpty()) {
            return Optional.empty();
        }
        Instant now = clock.now();
        return Optional.of(ICalendarWriter.write(CALENDAR_NAME, now, appUrl, entries(persons.getFirst(), now)));
    }

    private String token(UUID personId) {
        return (String) em.createNativeQuery("select token from calendar_subscription where person_id = ?")
                .setParameter(1, personId).getSingleResult();
    }

    /**
     * Events of the person's teams (member in the season of the event, as in the event list, TER-13) that end
     * after «now» minus 30 days, cancelled ones included (CAL-02, CAL-04). Chronological.
     */
    List<CalendarEntry> entries(UUID personId, Instant now) {
        List<Event> events = em.createQuery("""
                        select distinct t from Event t left join fetch t.teams join fetch t.season
                        where exists (select z.id from TeamMembership z
                                      where z.person.id = :person and z.season = t.season and z.team member of t.teams)
                          and t.end > :from
                        order by t.start, t.id""", Event.class)
                .setParameter("person", personId).setParameter("from", CalendarRules.from(now))
                .getResultList();
        if (events.isEmpty()) {
            return List.of();
        }
        Set<UUID> seasons = new LinkedHashSet<>();
        events.forEach(e -> seasons.add(e.season.id));
        Map<String, String> names = new HashMap<>();
        TeamSeason.<TeamSeason>list("season.id in ?1", seasons)
                .forEach(ts -> names.put(ts.team.id + ":" + ts.season.id, ts.name));
        List<UUID> ids = events.stream().map(e -> e.id).toList();

        // Only the person's own response, nothing of others (CAL-08)
        Map<UUID, Object[]> responses = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "select event_id, status, comment from response where person_id = :person and event_id in (:ids)")
                .setParameter("person", personId).setParameter("ids", ids).getResultList();
        rows.forEach(row -> responses.put((UUID) row[0], row));

        Map<UUID, List<CalendarEntry.MatchLine>> matches = new HashMap<>();
        List<UUID> matchDays = events.stream().filter(e -> e.type == EventType.MATCH_DAY).map(e -> e.id).toList();
        if (!matchDays.isEmpty()) {
            Match.<Match>list("event.id in ?1 order by startTime, id", matchDays)
                    .forEach(m -> matches.computeIfAbsent(m.event.id, key -> new ArrayList<>())
                            .add(new CalendarEntry.MatchLine(m.startTime, m.opponent, m.home, m.result)));
        }

        List<CalendarEntry> entries = new ArrayList<>();
        for (Event event : events) {
            List<String> teams = event.teams.stream()
                    .map((Team team) -> names.getOrDefault(team.id + ":" + event.season.id, ""))
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList();
            Object[] response = responses.get(event.id);
            ResponseStatus status = response == null ? ResponseStatus.PENDING : ResponseStatus.valueOf((String) response[1]);
            String comment = response == null ? null : (String) response[2];
            entries.add(new CalendarEntry(event.id, event.type, event.title, event.start, event.end,
                    event.status == EventStatus.CANCELLED, event.cancellationReason, teams,
                    event.location == null ? null : event.location.name,
                    event.location == null ? null : event.location.address,
                    event.location == null ? null : event.location.mapsUrl,
                    event.location == null ? null : event.location.latitude,
                    event.location == null ? null : event.location.longitude,
                    event.meetingPointTime, event.meetingPointLocation, event.notes,
                    matches.getOrDefault(event.id, List.of()), status, comment));
        }
        entries.sort(Comparator.comparing(CalendarEntry::start).thenComparing(CalendarEntry::id));
        return entries;
    }
}
