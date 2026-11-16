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
 * Serien: einmal erfasst, erzeugen sie die Termine im Gültigkeitszeitraum (TER-05). Änderungen gelten für
 * künftige Termine (TER-08), Ausnahmen nehmen Zeiträume aus (TER-07). Rechte wie bei Terminen (TER-11, RR-06).
 */
@ApplicationScoped
public class SeriesService {

    @Inject
    EventNotifications notifications;

    /** Grund, mit dem ein Termin abgesagt wird, der nach einer Änderung nicht mehr zur Serie passt. */
    static final String REASON_SERIES_CHANGED = "Serie geändert";

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    /** Serie anlegen und ihre künftigen Termine erzeugen (TER-05). */
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
     * Serie ändern (TER-08). Künftige Termine übernehmen die neuen Angaben, Antworten bleiben erhalten.
     * Einzeln geänderte und abgesagte Termine bleiben, wie sie sind (TER-06). Termine, die nicht mehr zur
     * Serie passen, verschwinden bzw. werden abgesagt, wenn schon jemand geantwortet hat.
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
        for (Event t : events(s)) {
            all.put(t.seriesDate, t);
            if (t.start.isAfter(now)) {
                future.put(t.seriesDate, new EventRules.Existing(t.status == EventStatus.PLANNED, t.individuallyChanged));
            }
        }
        List<LocalDate> target = dates(s).stream()
                .filter(day -> EventRules.instant(day, s.startTime).isAfter(now))
                .filter(day -> !all.containsKey(day) || future.containsKey(day))
                .toList();
        EventRules.SuSync sync = EventRules.sync(future, target);
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
     * Zeitraum ausnehmen, z.B. Ferien (TER-07). Künftige geplante Termine der Serie in diesem Zeitraum
     * verschwinden; haben sie schon Antworten, werden sie mit dem Grund der Ausnahme abgesagt.
     */
    @Transactional
    public SeriesDto createException(CurrentUser user, UUID seriesId, SeriesExceptionDto input) {
        Series s = find(seriesId);
        EventRules.checkCanCreate(user.admin(), EventService.teamRoles(user, s.teams, s.season));
        EventRules.checkException(input.startDate(), input.endDate());
        s.season.checkWritable();
        SeriesException a = new SeriesException();
        a.id = UUID.randomUUID();
        a.series = s;
        a.startDate = input.startDate();
        a.endDate = input.endDate();
        a.reason = input.reason().strip();
        s.exceptions.add(a);

        Instant now = clock.now();
        EventRules.Period period = new EventRules.Period(a.startDate, a.endDate);
        for (Event t : events(s)) {
            if (t.status == EventStatus.PLANNED && t.start.isAfter(now)
                    && period.contains(EventRules.date(t.start))) {
                drop(user, t, a.reason);
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

    private static void checkInput(SeriesInput e, Set<Team> teams, Season season) {
        EventRules.checkSeries(EventRules.time(e.startTime()), EventRules.time(e.endTime()),
                e.validFrom(), e.validUntil());
        EventService.checkTeamsInSeason(teams, season);
    }

    private static void apply(Series s, SeriesInput e, Set<Team> teams) {
        s.type = e.type();
        s.title = e.title().strip();
        s.weekday = e.weekday();
        s.startTime = EventRules.time(e.startTime());
        s.endTime = EventRules.time(e.endTime());
        s.validFrom = e.validFrom();
        s.validUntil = e.validUntil();
        s.location = Location.of(e.location());
        s.responseDeadlineHours = EventRules.responseDeadline(e.responseDeadlineHours());
        s.teams.clear();
        s.teams.addAll(teams);
    }

    /** Neuer Termin der Serie an diesem Tag. */
    private static Event create(Series s, LocalDate day) {
        Event t = Event.create();
        t.series = s;
        t.seriesDate = day;
        t.season = EventService.seasonOn(day, s.season);
        apply(t, s, day);
        return t;
    }

    /** Angaben der Serie auf den Termin übertragen. */
    private static void apply(Event t, Series s, LocalDate day) {
        t.type = s.type.eventType();
        t.title = s.title;
        t.start = EventRules.instant(day, s.startTime);
        t.end = EventRules.instant(day, s.endTime);
        t.location = s.location == null ? null : Location.of(s.location.dto());
        t.responseDeadlineHours = s.responseDeadlineHours;
        t.teams.clear();
        t.teams.addAll(s.teams);
    }

    /**
     * Termin entfernen oder, wenn schon jemand geantwortet hat, absagen (TER-07, zustaende.md). Eine Absage
     * benachrichtigt alle Mitglieder der beteiligten Teams (BEN-07).
     */
    private void drop(CurrentUser user, Event t, String reason) {
        Number responses = (Number) em.createNativeQuery("select count(*) from response where event_id = ?1")
                .setParameter(1, t.id).getSingleResult();
        switch (EventRules.onException(responses.longValue() > 0)) {
            case REMOVE -> t.delete();
            case CANCEL -> {
                t.status = EventStatus.CANCELLED;
                t.cancellationReason = reason;
                notifications.cancelled(user, t);
            }
        }
    }
}
