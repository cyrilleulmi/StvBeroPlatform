package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.error.DomainError;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Domain rules of the attendance check (PRS). No database, so they can be checked in unit tests. */
public final class AttendanceRules {

    public static final String SWITCHED_OFF = "Für diesen Termin wird keine Präsenz erfasst.";
    public static final String NOT_STARTED = "Die Präsenz lässt sich erst ab Beginn des Termins erfassen.";
    public static final String CANCELLED = "Der Termin ist abgesagt. Dafür wird keine Präsenz erfasst.";
    public static final String ONLY_PLAYERS = "Präsenz gibt es nur für Spielerinnen der Teams. Coaches gehören nicht dazu.";
    public static final String DUPLICATE = "Jede Spielerin darf nur einmal vorkommen.";

    private AttendanceRules() {
    }

    /** Recording attendance: coaches (of every team, E-69) and admins (PRS-10). */
    public static boolean canRecord(boolean allTeams) {
        return allTeams;
    }

    public static void checkCanRecord(boolean allTeams) {
        if (!canRecord(allTeams)) {
            throw DomainError.forbidden();
        }
    }

    /**
     * Attendance can be recorded: the check is switched on (PRS-07), the event is not cancelled (PRS-09) and has
     * started (PRS-02). Otherwise 409 with the rule. There is no end (assumption PRS-02).
     */
    public static void checkRecordable(boolean attendanceCheck, EventStatus status, Instant start, Instant now) {
        if (!attendanceCheck) {
            throw DomainError.conflict("Präsenz ausgeschaltet", SWITCHED_OFF, "PRS-07");
        }
        if (status == EventStatus.CANCELLED) {
            throw DomainError.conflict("Termin abgesagt", CANCELLED, "PRS-09");
        }
        if (now.isBefore(start)) {
            throw DomainError.conflict("Termin noch nicht begonnen", NOT_STARTED, "PRS-02");
        }
    }

    /** Same conditions as {@link #checkRecordable}, for showing «Präsenz erfassen» (PRS-02). */
    public static boolean recordable(boolean attendanceCheck, EventStatus status, Instant start, Instant now) {
        return attendanceCheck && status == EventStatus.PLANNED && !now.isBefore(start);
    }

    /** Only players of the involved teams, no coaches and nobody else (PRS-05): otherwise 422. */
    public static void checkOnlyPlayers(Collection<UUID> persons, Set<UUID> players) {
        if (!players.containsAll(persons)) {
            throw DomainError.ruleViolation("entries", "Keine Spielerin", ONLY_PLAYERS, "PRS-05");
        }
    }

    /** Every person at most once per request: otherwise 400. */
    public static void checkUnique(Collection<UUID> persons) {
        if (Set.copyOf(persons).size() != persons.size()) {
            throw DomainError.invalidField("entries", DUPLICATE);
        }
    }

    /**
     * Status shown on the screen (PRS-03): the recorded one if there is one, otherwise the preselection. «dabei»
     * is preselected as present, everything else (declined, maybe, pending) as absent. The response itself is
     * never changed.
     */
    public static AttendanceStatus shown(AttendanceStatus recorded, ResponseStatus response) {
        if (recorded != null) {
            return recorded;
        }
        return response == ResponseStatus.ATTENDING ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT;
    }

    /**
     * Own attendance a player sees on the event (PRS-06): only if the check is on and something is recorded for
     * her, otherwise nothing.
     */
    public static AttendanceStatus mine(boolean attendanceCheck, AttendanceStatus recorded) {
        return attendanceCheck ? recorded : null;
    }
}
