package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.clock.BackendClock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Create, change (e.g. the result) and delete the matches of a match day by hand (AUF-14 to AUF-16).
 * Only coaches of the team and admins (TER-11, RR-06).
 */
@ApplicationScoped
public class MatchService {

    @Inject
    BackendClock clock;

    @Transactional
    public MatchDto add(CurrentUser user, UUID eventId, MatchInput input) {
        Event event = EventService.find(eventId);
        checkCanEdit(user, event);
        MatchRules.checkMatchDay(event.type);
        EventRules.checkChangeable(event.status, event.end, clock.now());
        event.season.checkWritable();
        Instant startTime = input.startTime().toInstant();
        MatchRules.checkOnMatchDay(startTime, event.start);
        Match match = Match.create(event);
        apply(match, input);
        match.persist();
        return match.dto();
    }

    /**
     * Change a match. The result can also be entered after the match day (AUF-15), so the only check here is
     * "not cancelled". For imported matches, opponent, start time and home/away are protected.
     */
    @Transactional
    public MatchDto update(CurrentUser user, UUID eventId, UUID matchId, MatchInput input) {
        Event event = EventService.find(eventId);
        checkCanEdit(user, event);
        Match match = find(event, matchId);
        MatchRules.checkNotCancelled(event.status);
        event.season.checkWritable();
        Instant startTime = input.startTime().toInstant();
        MatchRules.checkChangeable(match.source, match.opponent, match.startTime, match.home,
                input.opponent().strip(), startTime, input.home());
        MatchRules.checkOnMatchDayWhenChanging(match.startTime, startTime, event.start);
        apply(match, input);
        return match.dto();
    }

    @Transactional
    public void delete(CurrentUser user, UUID eventId, UUID matchId) {
        Event event = EventService.find(eventId);
        checkCanEdit(user, event);
        Match match = find(event, matchId);
        EventRules.checkChangeable(event.status, event.end, clock.now());
        event.season.checkWritable();
        MatchRules.checkDeletable(match.source, match.missingAtSu);
        match.delete();
    }

    /**
     * A coach or admin confirms that she has seen what swiss unihockey changed about the match (SUH-12). The
     * warning disappears; the change itself was already applied by the sync.
     */
    @Transactional
    public MatchDto confirmSuChange(CurrentUser user, UUID eventId, UUID matchId) {
        Event event = EventService.find(eventId);
        checkCanEdit(user, event);
        Match match = find(event, matchId);
        event.season.checkWritable();
        MatchRules.checkHasSuChange(match.suChangedAt);
        match.clearSuChange();
        return match.dto();
    }

    private static void checkCanEdit(CurrentUser user, Event event) {
        EventRules.checkCanCreate(user.allTeams(), EventService.teamRoles(user, event.teams, event.season));
    }

    private static Match find(Event event, UUID matchId) {
        return Match.<Match>findByIdOptional(matchId).filter(match -> match.event.id.equals(event.id))
                .orElseThrow(() -> DomainError.notFound("Dieses Spiel gibt es nicht."));
    }

    private static void apply(Match match, MatchInput input) {
        match.opponent = input.opponent().strip();
        match.startTime = input.startTime().toInstant();
        match.home = input.home();
        match.result = EventRules.blankToNull(input.result());
    }
}
