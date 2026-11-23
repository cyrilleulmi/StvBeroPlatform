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

    @Inject
    ch.stvbero.platform.core.absence.AbsenceService absences;

    /** Create a series and its future events (TER-05). */
    @Transactional
    public SeriesDto create(CurrentUser user, SeriesInput input) {
        Set<Team> teams = EventService.teams(input.teamIds());
        Season season = EventService.seasonOn(input.validFrom(), null);
        EventRules.checkCanCreate(user.allTeams(), EventService.teamRoles(user, teams, season));
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
        // Practices in the time of an absence are declined by themselves (ABS-04)
        absences.applyForEvents(events(s).stream().map(event -> event.id).toList());
        return SeriesDto.of(s, user);
    }

    public SeriesDto get(CurrentUser user, UUID seriesId) {
        Series s = find(seriesId);
        EventRules.checkCanView(user.allTeams(), EventService.teamRoles(user, s.teams, s.season));
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
        EventRules.checkCanCreate(user.allTeams(), EventService.teamRoles(user, s.teams, s.season));
        Set<Team> teams = EventService.teams(input.teamIds());
        EventRules.checkCanCreate(user.allTeams(), EventService.teamRoles(user, teams, s.season));
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
        em.flush();
        absences.applyForEvents(events(s).stream().map(event -> event.id).toList());
        return SeriesDto.of(s, user);
    }

    /**
     * Exclude a period, e.g. holidays (TER-07). Future planned events of the series in this period are
     * removed; if they already have responses, they are cancelled with the reason of the exception.
     */
    @Transactional
    public SeriesDto createException(CurrentUser user, UUID seriesId, SeriesExceptionDto input) {
        Series s = find(seriesId);
        EventRules.checkCanCreate(user.allTeams(), EventService.teamRoles(user, s.teams, s.season));
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

    // ------------------------------------------------------------------ Reworking (TER-18 to TER-20)

    /** Reason used to cancel an event when its series is ended. */
    static final String REASON_SERIES_ENDED = "Serie beendet";

    /**
     * The series of a team in a season, with their upcoming events (TER-18): series that still run first, then by
     * weekday and start. Everyone who may see the team.
     */
    @Transactional
    public List<SeriesPlanDto.Overview> ofTeam(CurrentUser user, UUID teamId, UUID seasonId) {
        Team team = Team.<Team>findByIdOptional(teamId)
                .orElseThrow(() -> DomainError.notFound("Dieses Team gibt es nicht."));
        Season season = Season.orCurrent(seasonId);
        EventRules.checkCanView(user.allTeams(), EventService.teamRoles(user, Set.of(team), season));
        Instant now = clock.now();
        List<Series> list = Series.list("select distinct s from Series s join s.teams t where t.id = ?1 and s.season.id = ?2",
                team.id, season.id);
        return list.stream()
                .map(s -> {
                    List<Event> upcoming = events(s).stream()
                            .filter(e -> e.start.isAfter(now) && e.status != EventStatus.CANCELLED).toList();
                    boolean canEdit = !s.season.isArchived()
                            && EventRules.canEdit(user.allTeams(), EventService.teamRoles(user, s.teams, s.season));
                    return new SeriesPlanDto.Overview(SeriesDto.of(s, user), upcoming.size(),
                            upcoming.isEmpty() ? null : upcoming.get(0).start.atZone(BackendClock.ZONE).toOffsetDateTime(),
                            canEdit);
                })
                .sorted(java.util.Comparator.comparing((SeriesPlanDto.Overview o) -> o.upcoming() == 0)
                        .thenComparing(o -> o.series().weekday())
                        .thenComparing(o -> o.series().startTime()))
                .toList();
    }

    /** What {@link #update} would do to the upcoming events; changes nothing (TER-19). */
    @Transactional
    public SeriesPlanDto.Preview preview(CurrentUser user, UUID seriesId, SeriesInput input) {
        Series s = find(seriesId);
        EventRules.checkCanCreate(user.allTeams(), EventService.teamRoles(user, s.teams, s.season));
        Set<Team> teams = EventService.teams(input.teamIds());
        checkInput(input, teams, s.season);
        Instant now = clock.now();
        Map<LocalDate, Event> all = new HashMap<>();
        Map<LocalDate, EventRules.Existing> future = new HashMap<>();
        int kept = 0;
        for (Event event : events(s)) {
            all.put(event.seriesDate, event);
            if (event.start.isAfter(now)) {
                boolean planned = event.status == EventStatus.PLANNED;
                future.put(event.seriesDate, new EventRules.Existing(planned, event.individuallyChanged));
                if (!planned || event.individuallyChanged) {
                    kept++;
                }
            }
        }
        java.time.LocalTime start = EventRules.time(input.startTime());
        List<LocalDate> target = EventRules.dates(input.weekday().day, input.validFrom(), input.validUntil(),
                        s.exceptionPeriods()).stream()
                .filter(day -> EventRules.instant(day, start).isAfter(now))
                .filter(day -> !all.containsKey(day) || future.containsKey(day))
                .toList();
        EventRules.SeriesSync sync = EventRules.syncSeries(future, target);
        int adjusted = (int) sync.apply().stream().filter(day -> changes(all.get(day), s, input, teams, day)).count();
        int cancelled = (int) sync.drop().stream().filter(day -> hasResponses(all.get(day))).count();
        java.util.TreeSet<LocalDate> touched = new java.util.TreeSet<>(sync.create());
        touched.addAll(sync.drop());
        sync.apply().stream().filter(day -> changes(all.get(day), s, input, teams, day)).forEach(touched::add);
        return new SeriesPlanDto.Preview(adjusted, sync.create().size(), sync.drop().size() - cancelled, cancelled,
                kept, touched.isEmpty() ? null : touched.first());
    }

    /** What {@link #end} would do; changes nothing (TER-20). */
    @Transactional
    public SeriesPlanDto.Preview previewEnd(CurrentUser user, UUID seriesId, LocalDate from) {
        Series s = find(seriesId);
        EventRules.checkCanCreate(user.allTeams(), EventService.teamRoles(user, s.teams, s.season));
        List<Event> gone = ending(s, from);
        int cancelled = (int) gone.stream().filter(this::hasResponses).count();
        return new SeriesPlanDto.Preview(0, 0, gone.size() - cancelled, cancelled, 0,
                gone.isEmpty() ? null : gone.get(0).seriesDate);
    }

    /**
     * Ends a series from a day on (TER-20): its upcoming events from that day on are removed, or cancelled («Serie
     * beendet») if someone has responded; also events changed by hand. Past events stay. The series keeps its
     * history and ends the day before.
     */
    @Transactional
    public SeriesDto end(CurrentUser user, UUID seriesId, LocalDate from) {
        Series s = find(seriesId);
        EventRules.checkCanCreate(user.allTeams(), EventService.teamRoles(user, s.teams, s.season));
        s.season.checkWritable();
        for (Event event : ending(s, from)) {
            drop(user, event, REASON_SERIES_ENDED);
        }
        LocalDate until = from.minusDays(1);
        if (!until.isBefore(s.validFrom)) {
            // Never longer than before
            if (until.isBefore(s.validUntil)) {
                s.validUntil = until;
            }
            return SeriesDto.of(s, user);
        }
        // Ended before it began: no day of the series is left. The shortest period plus an exception on its
        // only day keep the series valid without any date
        s.validUntil = s.validFrom;
        SeriesException none = new SeriesException();
        none.id = UUID.randomUUID();
        none.series = s;
        none.startDate = s.validFrom;
        none.endDate = s.validFrom;
        none.reason = REASON_SERIES_ENDED;
        s.exceptions.add(none);
        return SeriesDto.of(s, user);
    }

    /** Upcoming events of the series on or after {@code from} that are still planned. */
    private List<Event> ending(Series s, LocalDate from) {
        Instant now = clock.now();
        return events(s).stream()
                .filter(e -> e.status == EventStatus.PLANNED && e.start.isAfter(now)
                        && !EventRules.date(e.start).isBefore(from))
                .toList();
    }

    private boolean hasResponses(Event event) {
        Number count = (Number) em.createNativeQuery(
                        "select count(*) from response where event_id = ?1 and absence_id is null")
                .setParameter(1, event.id).getSingleResult();
        return count.longValue() > 0;
    }

    /** The event would look different after the change. */
    private static boolean changes(Event event, Series s, SeriesInput input, Set<Team> teams, LocalDate day) {
        if (event == null) {
            return true;
        }
        Location location = Location.of(input.location());
        return !event.start.equals(EventRules.instant(day, EventRules.time(input.startTime())))
                || !event.end.equals(EventRules.instant(day, EventRules.time(input.endTime())))
                || !java.util.Objects.equals(event.title, input.title().strip())
                || !java.util.Objects.equals(event.location == null ? null : event.location.dto(),
                        location == null ? null : location.dto())
                || !event.teams.equals(teams)
                || event.responseDeadlineHours != EventRules.responseDeadline(input.responseDeadlineHours())
                || (input.attendanceCheck() != null && event.attendanceCheck != input.attendanceCheck());
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
        if (input.attendanceCheck() != null) {
            s.attendanceCheck = input.attendanceCheck();
        }
        s.teams.clear();
        s.teams.addAll(teams);
        List<UUID> optional = Participation.check(teams.stream().map(team -> team.id).toList(), input.optionalTeamIds());
        s.optionalTeamIds.retainAll(optional);
        s.optionalTeamIds.addAll(optional);
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
        event.attendanceCheck = s.attendanceCheck;
        event.teams.clear();
        event.teams.addAll(s.teams);
        event.optionalTeamIds.retainAll(s.optionalTeamIds);
        event.optionalTeamIds.addAll(s.optionalTeamIds);
    }

    /**
     * Remove the event or, if someone has already responded, cancel it (TER-07, docs/states.md). A cancellation
     * notifies all members of the participating teams (BEN-07).
     */
    private void drop(CurrentUser user, Event event, String reason) {
        // Declines set by an absence do not count: nobody answered by hand (ABS-04)
        Number responseCount = (Number) em.createNativeQuery(
                        "select count(*) from response where event_id = ?1 and absence_id is null")
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
