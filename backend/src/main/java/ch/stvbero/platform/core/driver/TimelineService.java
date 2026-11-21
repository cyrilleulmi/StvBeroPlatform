package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.event.Event;
import ch.stvbero.platform.core.event.EventRules;
import ch.stvbero.platform.core.event.EventStatus;
import ch.stvbero.platform.core.event.Match;
import ch.stvbero.platform.core.team.TeamMembership;
import ch.stvbero.platform.core.team.TeamRole;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Timeline of a match day (TML): everyone who may see the match day sees it; coaches of the team and admins take
 * over the suggested departure as meeting time. The driving time is the one of the driver list (DRV-05).
 */
@ApplicationScoped
public class TimelineService {

    static final String NO_SUGGESTION = "Für diesen Spieltag gibt es im Moment keinen Vorschlag für die Abfahrt.";

    @Inject
    EntityManager em;

    @Inject
    BackendClock clock;

    @Inject
    Routes routes;

    @Inject
    MatchDayRoutes matchDayRoutes;

    private record Context(Event event, boolean canEdit) {
    }

    /** The timeline. Calculates the route first if needed (DRV-05), outside of a transaction. */
    public TimelineDto get(CurrentUser user, UUID eventId) {
        ensureRoute(user, eventId);
        return QuarkusTransaction.requiringNew().call(() -> dto(context(user, eventId)));
    }

    /**
     * Takes over the suggested departure as meeting time (TML-04): coaches of the team and admins, for a planned
     * match day that is not over.
     */
    public TimelineDto applySuggestion(CurrentUser user, UUID eventId) {
        ensureRoute(user, eventId);
        return QuarkusTransaction.requiringNew().call(() -> {
            Context c = context(user, eventId);
            if (!c.canEdit()) {
                throw DomainError.forbidden();
            }
            EventRules.checkChangeable(c.event().status, c.event().end, clock.now());
            c.event().season.checkWritable();
            TimelineRules.Plan plan = plan(c.event());
            if (plan.suggestedDeparture() == null) {
                throw DomainError.conflict("Kein Vorschlag", NO_SUGGESTION, "TML-04");
            }
            c.event().meetingPointTime = plan.suggestedDeparture();
            return dto(c);
        });
    }

    private void ensureRoute(CurrentUser user, UUID eventId) {
        Optional<Place[]> places = QuarkusTransaction.requiringNew().call(() -> {
            Context c = context(user, eventId);
            return MatchDayRoutes.missing(c.event()).isEmpty() && c.event().status != EventStatus.CANCELLED
                    ? Optional.of(matchDayRoutes.places(c.event())) : Optional.<Place[]>empty();
        });
        places.ifPresent(p -> routes.ensure(eventId, p[0], p[1]));
    }

    private Context context(CurrentUser user, UUID eventId) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> DomainError.notFound("Diesen Termin gibt es nicht."));
        Set<TeamRole> mine = new HashSet<>();
        List<UUID> teams = event.teams.stream().map(team -> team.id).toList();
        if (!teams.isEmpty()) {
            TeamMembership.<TeamMembership>list("person.id = ?1 and season.id = ?2 and team.id in ?3",
                    user.personId(), event.season.id, teams).forEach(m -> mine.add(m.teamRole));
        }
        EventRules.checkCanView(user.allTeams(), mine);
        DriverRules.checkMatchDay(event.type);
        return new Context(event, EventRules.canEdit(user.allTeams(), mine));
    }

    private Duration drive(Event event) {
        return matchDayRoutes.current(event).map(Routes.Stored::durationSeconds)
                .map(seconds -> Duration.ofMinutes(DriverRules.minutes(seconds))).orElse(null);
    }

    private TimelineRules.Plan plan(Event event) {
        List<TimelineRules.MatchSlot> matches = Match.of(event.id).stream().filter(match -> !match.missingAtSu)
                .map(match -> new TimelineRules.MatchSlot(match.id, match.startTime, match.opponent, match.home))
                .toList();
        return TimelineRules.plan(matches, Duration.ofMinutes(matchDuration(event)),
                Duration.ofMinutes(arrivalLead(event)), event.meetingPointTime, drive(event));
    }

    private static int matchDuration(Event event) {
        return TimelineRules.longest(event.teams.stream().map(team -> team.matchDurationMinutes).toList(), 50);
    }

    private static int arrivalLead(Event event) {
        return TimelineRules.longest(event.teams.stream().map(team -> team.arrivalLeadMinutes).toList(), 70);
    }

    private TimelineDto dto(Context c) {
        Event event = c.event();
        TimelineRules.Plan plan = plan(event);
        Optional<Routes.Stored> route = matchDayRoutes.current(event);
        Duration drive = drive(event);
        List<TimelineDto.Hint> hints = new ArrayList<>();
        if (plan.items().isEmpty()) {
            hints.add(TimelineDto.Hint.NO_MATCHES);
        }
        if (event.meetingPointLocation == null || event.meetingPointLocation.isBlank()) {
            hints.add(TimelineDto.Hint.MEETING_POINT_MISSING);
        } else if (drive == null && event.status != EventStatus.CANCELLED) {
            hints.add(TimelineDto.Hint.DRIVE_TIME_UNKNOWN);
        }
        if (plan.arrivesLate()) {
            hints.add(TimelineDto.Hint.ARRIVES_LATE);
        }
        Instant now = clock.now();
        boolean canApply = c.canEdit() && event.status == EventStatus.PLANNED && event.end.isAfter(now)
                && !event.season.isArchived() && plan.suggestedDeparture() != null
                && !plan.suggestedDeparture().equals(event.meetingPointTime);
        return new TimelineDto(event.id, event.status == EventStatus.CANCELLED, hints, time(plan.departure()),
                plan.departureSuggested(), time(plan.suggestedDeparture()), time(plan.arriveBy()),
                drive == null ? null : (int) drive.toMinutes(),
                route.map(Routes.Stored::distanceMeters).map(DriverRules::kilometres).orElse(null),
                matchDuration(event), arrivalLead(event), (int) TimelineRules.COOL_DOWN.toMinutes(), canApply,
                plan.items().stream().map(item -> new TimelineDto.Item(item.kind(), time(item.start()),
                        time(item.end()), item.minutes(), item.matchId(), item.opponent(), item.home())).toList());
    }

    private static OffsetDateTime time(Instant instant) {
        return instant == null ? null : instant.atZone(BackendClock.ZONE).toOffsetDateTime();
    }
}
