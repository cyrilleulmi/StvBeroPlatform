package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.clock.BackendClock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Spiele eines Spieltags von Hand erfassen, ändern (z.B. Resultat) und löschen (AUF-14 bis AUF-16).
 * Nur Coaches des Teams und Admins (TER-11, RR-06).
 */
@ApplicationScoped
public class MatchService {

    @Inject
    BackendClock clock;

    @Transactional
    public MatchDto add(CurrentUser user, UUID eventId, MatchInput input) {
        Event t = EventService.find(eventId);
        checkCanEdit(user, t);
        MatchRules.checkMatchDay(t.type);
        EventRules.checkChangeable(t.status, t.end, clock.now());
        t.season.checkWritable();
        Instant startTime = input.startTime().toInstant();
        MatchRules.checkOnMatchDay(startTime, t.start);
        Match s = Match.create(t);
        apply(s, input);
        s.persist();
        return s.dto();
    }

    /**
     * Spiel ändern. Das Resultat lässt sich auch nach dem Spieltag erfassen (AUF-15), deshalb gilt hier nur
     * «nicht abgesagt». Bei importierten Spielen sind Gegner, Anspielzeit und Heim/Auswärts geschützt.
     */
    @Transactional
    public MatchDto update(CurrentUser user, UUID eventId, UUID matchId, MatchInput input) {
        Event t = EventService.find(eventId);
        checkCanEdit(user, t);
        Match s = find(t, matchId);
        MatchRules.checkNotCancelled(t.status);
        t.season.checkWritable();
        Instant startTime = input.startTime().toInstant();
        MatchRules.checkChangeable(s.source, s.opponent, s.startTime, s.home,
                input.opponent().strip(), startTime, input.home());
        MatchRules.checkOnMatchDayWhenChanging(s.startTime, startTime, t.start);
        apply(s, input);
        return s.dto();
    }

    @Transactional
    public void delete(CurrentUser user, UUID eventId, UUID matchId) {
        Event t = EventService.find(eventId);
        checkCanEdit(user, t);
        Match s = find(t, matchId);
        EventRules.checkChangeable(t.status, t.end, clock.now());
        t.season.checkWritable();
        MatchRules.checkDeletable(s.source, s.missingAtSu);
        s.delete();
    }

    private static void checkCanEdit(CurrentUser user, Event t) {
        EventRules.checkCanCreate(user.admin(), EventService.teamRoles(user, t.teams, t.season));
    }

    private static Match find(Event t, UUID matchId) {
        return Match.<Match>findByIdOptional(matchId).filter(s -> s.event.id.equals(t.id))
                .orElseThrow(() -> DomainError.notFound("Dieses Spiel gibt es nicht."));
    }

    private static void apply(Match s, MatchInput e) {
        s.opponent = e.opponent().strip();
        s.startTime = e.startTime().toInstant();
        s.home = e.home();
        s.result = EventRules.blankToNull(e.result());
    }
}
