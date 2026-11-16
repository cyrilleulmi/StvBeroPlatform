package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.team.TeamRole;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Fachregeln zu Antworten (ANM, RR-06). Ohne Datenbank, damit sie als Unit-Test prüfbar sind. */
public final class ResponseRules {

    public static final String COMMENT_REQUIRED = "Bei «vielleicht» bitte kurz begründen.";
    public static final String CANCELLED = "Der Termin ist abgesagt. Antworten sind nicht mehr möglich.";
    public static final String OVER = "Der Termin ist vorbei. Antworten sind nicht mehr möglich.";

    private ResponseRules() {
    }

    // ------------------------------------------------------------------ Rechte

    /** Für sich antworten: Mitglied eines beteiligten Teams, in jeder Funktion (ANM-01, RR-02). */
    public static void checkCanRespondForSelf(boolean member) {
        if (!member) {
            throw DomainError.forbidden();
        }
    }

    /**
     * Stellvertretend antworten (ANM-11, RR-03, RR-06): Admins für jedes Mitglied eines beteiligten Teams,
     * Coaches nur für Personen, die in einem Team Spielerin sind, in dem die Coach Coach ist.
     *
     * @param meineFunktionen    Funktion der anfragenden Person pro beteiligtem Team
     * @param funktionenPerson   Funktion der Person, für die geantwortet wird, pro beteiligtem Team
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
     * Kommentare und Gründe sehen die Person selbst, Coaches des Teams, in dem der Eintrag steht, und
     * Admins (ANM-07, RR-06).
     */
    public static boolean commentVisible(boolean admin, boolean ownResponse, TeamRole myTeamRoleInTeam) {
        return admin || ownResponse || myTeamRoleInTeam == TeamRole.COACH;
    }

    // ------------------------------------------------------------------ Zustand und Eingabe

    /** Abgesagte und vergangene Termine nehmen keine Antworten an (409, TER-06). */
    public static void checkOpenForResponses(EventStatus status, Instant end, Instant now) {
        if (status == EventStatus.CANCELLED) {
            throw DomainError.conflict("Termin abgesagt", CANCELLED, "TER-06");
        }
        if (!end.isAfter(now)) {
            throw DomainError.conflict("Termin vorbei", OVER, "TER-06");
        }
    }

    /** «Vielleicht» verlangt einen Kommentar (ANM-04). Bei «nicht dabei» ist er freiwillig (ANM-05). */
    public static void checkComment(ResponseStatus status, String comment) {
        if (status == ResponseStatus.PENDING) {
            throw DomainError.invalidField("status", "Die Antwort ist «dabei», «nicht dabei» oder «vielleicht».");
        }
        if (status == ResponseStatus.MAYBE && EventRules.blankToNull(comment) == null) {
            throw DomainError.ruleViolation("comment", "Kommentar fehlt", COMMENT_REQUIRED, "ANM-04");
        }
    }

    /**
     * Kurzfristige Absage (ANM-10): Wechsel weg von «dabei» nach der Antwortfrist. Die Markierung bleibt
     * bei weiteren Wechseln zwischen «nicht dabei» und «vielleicht» bestehen und fällt mit «dabei» weg.
     *
     * @param bisher             bisherige Antwort, {@code OFFEN} ohne Antwort
     * @param bisherKurzfristig  bisherige Antwort war schon eine kurzfristige Absage
     */
    public static boolean lateCancellation(ResponseStatus previous, boolean previousLate, ResponseStatus newValue,
                                      Instant responseDeadline, Instant now) {
        if (newValue == ResponseStatus.ATTENDING) {
            return false;
        }
        if (previousLate) {
            return true;
        }
        return previous == ResponseStatus.ATTENDING && !now.isBefore(responseDeadline);
    }
}
