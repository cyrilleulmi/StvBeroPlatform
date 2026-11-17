package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.team.TeamRole;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Domain rules for responses (ANM, RR-06). No database, so they can be checked in unit tests. */
public final class ResponseRules {

    public static final String COMMENT_REQUIRED = "Bei «vielleicht» bitte kurz begründen.";
    public static final String CANCELLED = "Der Termin ist abgesagt. Antworten sind nicht mehr möglich.";
    public static final String OVER = "Der Termin ist vorbei. Antworten sind nicht mehr möglich.";

    private ResponseRules() {
    }

    // ------------------------------------------------------------------ Permissions

    /** Respond for oneself: member of a participating team, in any team role (ANM-01, RR-02). */
    public static void checkCanRespondForSelf(boolean member) {
        if (!member) {
            throw DomainError.forbidden();
        }
    }

    /**
     * Respond on behalf (ANM-11, RR-03, RR-06): admins for every member of a participating team, coaches only
     * for persons who are a player in a team in which the coach is coach.
     *
     * @param myTeamRoles     team role of the requesting person per participating team
     * @param teamRolesPerson team role of the person the response is for, per participating team
     */
    public static boolean canRespondOnBehalf(boolean admin, Map<UUID, TeamRole> myTeamRoles,
                                             Map<UUID, TeamRole> teamRolesPerson) {
        if (admin) {
            return !teamRolesPerson.isEmpty();
        }
        return teamRolesPerson.entrySet().stream().anyMatch(e -> e.getValue() == TeamRole.PLAYER
                && myTeamRoles.get(e.getKey()) == TeamRole.COACH);
    }

    public static void checkCanRespondOnBehalf(boolean admin, Map<UUID, TeamRole> myTeamRoles,
                                                Map<UUID, TeamRole> teamRolesPerson) {
        if (!canRespondOnBehalf(admin, myTeamRoles, teamRolesPerson)) {
            throw DomainError.forbidden();
        }
    }

    /**
     * Comments and reasons are visible to the person themselves, to coaches of the team the entry is in, and
     * to admins (ANM-07, RR-06).
     */
    public static boolean commentVisible(boolean admin, boolean ownResponse, TeamRole myTeamRoleInTeam) {
        return admin || ownResponse || myTeamRoleInTeam == TeamRole.COACH;
    }

    // ------------------------------------------------------------------ State and input

    /** Cancelled and past events accept no responses (409, TER-06). */
    public static void checkOpenForResponses(EventStatus status, Instant end, Instant now) {
        if (status == EventStatus.CANCELLED) {
            throw DomainError.conflict("Termin abgesagt", CANCELLED, "TER-06");
        }
        if (!end.isAfter(now)) {
            throw DomainError.conflict("Termin vorbei", OVER, "TER-06");
        }
    }

    /** «vielleicht» requires a comment (ANM-04). For «nicht dabei» it is optional (ANM-05). */
    public static void checkComment(ResponseStatus status, String comment) {
        if (status == ResponseStatus.PENDING) {
            throw DomainError.invalidField("status", "Die Antwort ist «dabei», «nicht dabei» oder «vielleicht».");
        }
        if (status == ResponseStatus.MAYBE && EventRules.blankToNull(comment) == null) {
            throw DomainError.ruleViolation("comment", "Kommentar fehlt", COMMENT_REQUIRED, "ANM-04");
        }
    }

    /**
     * Late cancellation (ANM-10): a change away from «dabei» after the response deadline. The flag stays on
     * further changes between «nicht dabei» and «vielleicht» and is cleared by «dabei».
     *
     * @param previousStatus previous response, {@code PENDING} without a response
     * @param previousLate   the previous response was already a late cancellation
     */
    public static boolean lateCancellation(ResponseStatus previousStatus, boolean previousLate, ResponseStatus newStatus,
                                           Instant responseDeadline, Instant now) {
        if (newStatus == ResponseStatus.ATTENDING) {
            return false;
        }
        if (previousLate) {
            return true;
        }
        return previousStatus == ResponseStatus.ATTENDING && !now.isBefore(responseDeadline);
    }
}
