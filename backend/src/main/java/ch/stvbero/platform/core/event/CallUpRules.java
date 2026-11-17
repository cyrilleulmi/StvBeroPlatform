package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.team.TeamRole;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Domain rules for the call-up of a match day (AUF). No database, so they can be checked in unit tests. */
public final class CallUpRules {

    public static final String ONLY_MATCH_DAY = "Nur ein Spieltag hat ein Aufgebot.";
    public static final String ONLY_PLAYERS = "Aufbieten lassen sich nur Spielerinnen des Teams. Coaches gehören nicht zum Aufgebot.";
    public static final String ALREADY_PUBLISHED = "Das Aufgebot ist schon veröffentlicht.";
    public static final String EMPTY = "Wähle zuerst mindestens eine Spielerin aus.";

    private CallUpRules() {
    }

    /** Only match days have a call-up (AUF-01, AUF-02). */
    public static void checkMatchDay(EventType type) {
        if (type != EventType.MATCH_DAY) {
            throw DomainError.conflict("Kein Spieltag", ONLY_MATCH_DAY, "AUF-02");
        }
    }

    /** View, create and publish a call-up: coaches of the team and admins (RR-03, permission table). */
    public static boolean canEdit(boolean admin, Collection<TeamRole> teamRolesInTeam) {
        return admin || teamRolesInTeam.contains(TeamRole.COACH);
    }

    public static void checkCanEdit(boolean admin, Collection<TeamRole> teamRolesInTeam) {
        if (!canEdit(admin, teamRolesInTeam)) {
            throw DomainError.forbidden();
        }
    }

    /**
     * All players of the team can be called up, regardless of their response (AUF-22), but no coaches
     * (AUF-28) and nobody outside the team: 422.
     */
    public static void checkCanBeCalledUp(Collection<UUID> selection, Set<UUID> players) {
        if (!players.containsAll(selection)) {
            throw DomainError.ruleViolation("personIds", "Nicht aufbietbar", ONLY_PLAYERS, "AUF-28");
        }
    }

    /** Only a draft with at least one player is published, and only once (AUF-23): otherwise 409. */
    public static void checkPublishable(CallUpStatus status) {
        switch (status) {
            case PUBLISHED -> throw DomainError.conflict("Schon veröffentlicht", ALREADY_PUBLISHED, "AUF-23");
            case NONE -> throw DomainError.conflict("Aufgebot leer", EMPTY, "AUF-23");
            case DRAFT -> {
                // ok
            }
        }
    }

    /** NONE without a selection, DRAFT until it is published, PUBLISHED afterwards (docs/states.md). */
    public static CallUpStatus status(boolean published, int calledUpCount) {
        if (published) {
            return CallUpStatus.PUBLISHED;
        }
        return calledUpCount > 0 ? CallUpStatus.DRAFT : CallUpStatus.NONE;
    }

    /**
     * What someone sees of the call-up status: coaches and admins see the real status, everyone else sees
     * something only after it is published (AUF-23, assumption: even the existence of a draft stays hidden).
     */
    public static CallUpStatus visibleStatus(CallUpStatus status, boolean coachOrAdmin) {
        if (coachOrAdmin || status == CallUpStatus.PUBLISHED) {
            return status;
        }
        return CallUpStatus.NONE;
    }

    /** View of a player (AUF-24): before publication «noch kein Aufgebot». */
    public static MyCallUp myCallUp(CallUpStatus status, boolean calledUp) {
        if (status != CallUpStatus.PUBLISHED) {
            return MyCallUp.NO_CALL_UP_YET;
        }
        return calledUp ? MyCallUp.CALLED_UP : MyCallUp.NOT_CALLED_UP;
    }
}
