package ch.stvbero.platform.attendance.event;

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
     * Respond on behalf (ANM-11, RR-03, RR-06): admins and coaches ({@code allTeams}) for every member of a
     * participating team. Otherwise only a coach of the team for its players (kept for completeness; today
     * every coach has {@code allTeams}).
     *
     * @param myTeamRoles     team role of the requesting person per participating team
     * @param teamRolesPerson team role of the person the response is for, per participating team
     */
    public static boolean canRespondOnBehalf(boolean allTeams, Map<UUID, TeamRole> myTeamRoles,
                                             Map<UUID, TeamRole> teamRolesPerson) {
        if (allTeams) {
            return !teamRolesPerson.isEmpty();
        }
        return teamRolesPerson.entrySet().stream().anyMatch(e -> e.getValue() == TeamRole.PLAYER
                && myTeamRoles.get(e.getKey()) == TeamRole.COACH);
    }

    public static void checkCanRespondOnBehalf(boolean allTeams, Map<UUID, TeamRole> myTeamRoles,
                                                Map<UUID, TeamRole> teamRolesPerson) {
        if (!canRespondOnBehalf(allTeams, myTeamRoles, teamRolesPerson)) {
            throw DomainError.forbidden();
        }
    }

    /**
     * Comments and reasons are visible to the person themselves, to coaches (of every team, E-69) and to
     * admins (ANM-07, RR-03, RR-06).
     */
    public static boolean commentVisible(boolean allTeams, boolean ownResponse, TeamRole myTeamRoleInTeam) {
        return allTeams || ownResponse || myTeamRoleInTeam == TeamRole.COACH;
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

    public static final String REASON_REQUIRED = "Bitte gib einen Grund an.";

    /**
     * «vielleicht» requires a comment (ANM-04). For «nicht dabei» the reason is optional, unless the event variant
     * requires one (ANM-05, VAR-12).
     */
    public static void checkComment(ResponseStatus status, String comment, boolean declineReasonRequired) {
        if (status == ResponseStatus.PENDING) {
            throw DomainError.invalidField("status", "Die Antwort ist «dabei», «nicht dabei» oder «vielleicht».");
        }
        if (status == ResponseStatus.MAYBE && EventRules.blankToNull(comment) == null) {
            throw DomainError.ruleViolation("comment", "Kommentar fehlt", COMMENT_REQUIRED, "ANM-04");
        }
        if (status == ResponseStatus.DECLINED && declineReasonRequired && EventRules.blankToNull(comment) == null) {
            throw DomainError.ruleViolation("comment", "Grund fehlt", REASON_REQUIRED, "ANM-05");
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
