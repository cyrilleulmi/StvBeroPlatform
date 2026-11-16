package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.team.Team;
import ch.stvbero.platform.core.clock.BackendClock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Series: once created, they create the events in their validity period (TER-05). Changes apply to future
 * events (TER-08), series exceptions exclude periods (TER-07). Permissions as for events (TER-11, RR-06).
 */
@ApplicationScoped
public class SeriesService {

    @Inject
    EventNotifications notifications;

    /** Reason used to cancel an event that no longer fits the series after a change. */
    static final String REASON_SERIES_CHANGED = "Serie geändert";

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    /** Create a series and its future events (TER-05). */
    @Transactional
    public SeriesDto create(CurrentUser user, SeriesInput input) {
        Set<Team> teams = EventService.teams(input.teamIds());
        Season season = EventService.seasonOn(input.validFrom(), null);
        EventRules.checkCanCreate(user.admin(), EventService.teamRoles(user, teams, season));
        checkInput(input, teams, season);
        Series s = new Series();
        s.id = UUID.randomUUID();
        s.season = season;
        apply(s, input, teams);
        s.persist();
        Instant now = clock.now();
        for (LocalDate day : dates(s)) {
            if (EventRules.instant(day, s.startTime).isAfter(now)) {
                create(s, day).persist();
            }
        }
        return SeriesDto.of(s, user);
    }

    public SeriesDto get(CurrentUser user, UUID seriesId) {
        Series s = find(seriesId);
        EventRules.checkCanView(user.admin(), EventService.teamRoles(user, s.teams, s.season));
        return SeriesDto.of(s, user);
    }

    /**
     * Change a series (TER-08). Future events take over the new details, responses are kept. Individually
     * changed and cancelled events stay as they are (TER-06). Events that no longer fit the series are removed,
     * or cancelled if someone has already responded.
     */
    @Transactional
    public SeriesDto update(CurrentUser user, UUID seriesId, SeriesInput input) {
        Series s = find(seriesId);
        EventRules.checkCanCreate(user.admin(), EventService.teamRoles(user, s.teams, s.season));
        Set<Team> teams = EventService.teams(input.teamIds());
        EventRules.checkCanCreate(user.admin(), EventService.teamRoles(user, teams, s.season));
        checkInput(input, teams, s.season);
        apply(s, input, teams);

        Instant now = clock.now();
        Map<LocalDate, Event> all = new HashMap<>();
        Map<LocalDate, EventRules.Existing> future = new HashMap<>();
        for (Event event : events(s)) {
            all.put(event.seriesDate, event);
            if (event.start.isAfter(now)) {
                future.put(event.seriesDate, new EventRules.Existing(event.status == EventStatus.PLANNED, event.individuallyChanged));
            }
        }
        List<LocalDate> target = dates(s).stream()
                .filter(day -> EventRules.instant(day, s.startTime).isAfter(now))
                .filter(day -> !all.containsKey(day) || future.containsKey(day))
                .toList();
        EventRules.SeriesSync sync = EventRules.syncSeries(future, target);
        for (LocalDate day : sync.apply()) {
            apply(all.get(day), s, day);
        }
        for (LocalDate day : sync.drop()) {
            drop(user, all.get(day), REASON_SERIES_CHANGED);
        }
        em.flush();
        for (LocalDate day : sync.create()) {
            create(s, day).persist();
        }
        return SeriesDto.of(s, user);
    }

    /**
     * Exclude a period, e.g. holidays (TER-07). Future planned events of the series in this period are
     * removed; if they already have responses, they are cancelled with the reason of the exception.
     */
    @Transactional
    public SeriesDto createException(CurrentUser user, UUID seriesId, SeriesExceptionDto input) {
        Series s = find(seriesId);
        EventRules.checkCanCreate(user.admin(), EventService.teamRoles(user, s.teams, s.season));
        EventRules.checkException(input.startDate(), input.endDate());
        s.season.checkWritable();
        SeriesException exception = new SeriesException();
        exception.id = UUID.randomUUID();
        exception.series = s;
        exception.startDate = input.startDate();
        exception.endDate = input.endDate();
        exception.reason = input.reason().strip();
        s.exceptions.add(exception);

        Instant now = clock.now();
        EventRules.Period period = new EventRules.Period(exception.startDate, exception.endDate);
        for (Event event : events(s)) {
            if (event.status == EventStatus.PLANNED && event.start.isAfter(now)
                    && period.contains(EventRules.date(event.start))) {
                drop(user, event, exception.reason);
            }
        }
        return SeriesDto.of(s, user);
    }

    private static Series find(UUID id) {
        return Series.<Series>findByIdOptional(id)
                .orElseThrow(() -> DomainError.notFound("Diese Serie gibt es nicht."));
    }

    private static List<Event> events(Series s) {
        return Event.list("series.id = ?1 order by seriesDate", s.id);
    }

    private static List<LocalDate> dates(Series s) {
        return EventRules.dates(s.weekday.day, s.validFrom, s.validUntil, s.exceptionPeriods());
    }

    private static void checkInput(SeriesInput input, Set<Team> teams, Season season) {
        EventRules.checkSeries(EventRules.time(input.startTime()), EventRules.time(input.endTime()),
                input.validFrom(), input.validUntil());
        EventService.checkTeamsInSeason(teams, season);
    }

    private static void apply(Series s, SeriesInput input, Set<Team> teams) {
        s.type = input.type();
        s.title = input.title().strip();
        s.weekday = input.weekday();
        s.startTime = EventRules.time(input.startTime());
        s.endTime = EventRules.time(input.endTime());
        s.validFrom = input.validFrom();
        s.validUntil = input.validUntil();
        s.location = Location.of(input.location());
        s.responseDeadlineHours = EventRules.responseDeadline(input.responseDeadlineHours());
        s.teams.clear();
        s.teams.addAll(teams);
    }

    /** New event of the series on this day. */
    private static Event create(Series s, LocalDate day) {
        Event event = Event.create();
        event.series = s;
        event.seriesDate = day;
        event.season = EventService.seasonOn(day, s.season);
        apply(event, s, day);
        return event;
    }

    /** Copy the details of the series to the event. */
    private static void apply(Event event, Series s, LocalDate day) {
        event.type = s.type.eventType();
        event.title = s.title;
        event.start = EventRules.instant(day, s.startTime);
        event.end = EventRules.instant(day, s.endTime);
        event.location = s.location == null ? null : Location.of(s.location.dto());
        event.responseDeadlineHours = s.responseDeadlineHours;
        event.teams.clear();
        event.teams.addAll(s.teams);
    }

    /**
     * Remove the event or, if someone has already responded, cancel it (TER-07, docs/states.md). A cancellation
     * notifies all members of the participating teams (BEN-07).
     */
    private void drop(CurrentUser user, Event event, String reason) {
        Number responseCount = (Number) em.createNativeQuery("select count(*) from response where event_id = ?1")
                .setParameter(1, event.id).getSingleResult();
        switch (EventRules.onException(responseCount.longValue() > 0)) {
            case REMOVE -> event.delete();
            case CANCEL -> {
                event.status = EventStatus.CANCELLED;
                event.cancellationReason = reason;
                notifications.cancelled(user, event);
            }
        }
    }
}
