package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.team.TeamRole;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Fachregeln zum Aufgebot eines Spieltags (AUF). Ohne Datenbank, damit sie als Unit-Test prüfbar sind. */
public final class CallUpRules {

    public static final String ONLY_MATCH_DAY = "Nur ein Spieltag hat ein Aufgebot.";
    public static final String ONLY_PLAYERS = "Aufbieten lassen sich nur Spielerinnen des Teams. Coaches gehören nicht zum Aufgebot.";
    public static final String ALREADY_PUBLISHED = "Das Aufgebot ist schon veröffentlicht.";
    public static final String EMPTY = "Wähle zuerst mindestens eine Spielerin aus.";

    private CallUpRules() {
    }

    /** Ein Aufgebot gibt es nur bei Spieltagen (AUF-01, AUF-02). */
    public static void checkMatchDay(EventType type) {
        if (type != EventType.MATCH_DAY) {
            throw DomainError.conflict("Kein Spieltag", ONLY_MATCH_DAY, "AUF-02");
        }
    }

    /** Aufgebot ansehen, erstellen und veröffentlichen: Coaches des Teams und Admins (RR-03, Rechte-Tabelle). */
    public static boolean canEdit(boolean admin, Collection<TeamRole> teamRolesInTeam) {
        return admin || teamRolesInTeam.contains(TeamRole.COACH);
    }

    public static void checkCanEdit(boolean admin, Collection<TeamRole> teamRolesInTeam) {
        if (!canEdit(admin, teamRolesInTeam)) {
            throw DomainError.forbidden();
        }
    }

    /**
     * Aufbieten lassen sich alle Spielerinnen des Teams, unabhängig von ihrer Antwort (AUF-22), aber keine
     * Coaches (AUF-28) und niemand ausserhalb des Teams: 422.
     */
    public static void checkCanBeCalledUp(Collection<UUID> selection, Set<UUID> players) {
        if (!players.containsAll(selection)) {
            throw DomainError.ruleViolation("personIds", "Nicht aufbietbar", ONLY_PLAYERS, "AUF-28");
        }
    }

    /** Veröffentlicht wird ein Entwurf mit mindestens einer Spielerin, und nur einmal (AUF-23): sonst 409. */
    public static void checkPublishable(CallUpStatus status) {
        switch (status) {
            case PUBLISHED -> throw DomainError.conflict("Schon veröffentlicht", ALREADY_PUBLISHED, "AUF-23");
            case NONE -> throw DomainError.conflict("Aufgebot leer", EMPTY, "AUF-23");
            case DRAFT -> {
                // ok
            }
        }
    }

    /** KEINES ohne Auswahl, ENTWURF bis zur Veröffentlichung, danach VEROEFFENTLICHT (zustaende.md). */
    public static CallUpStatus status(boolean published, int calledUpCount) {
        if (published) {
            return CallUpStatus.PUBLISHED;
        }
        return calledUpCount > 0 ? CallUpStatus.DRAFT : CallUpStatus.NONE;
    }

    /**
     * Was jemand vom Stand des Aufgebots sieht: Coaches und Admins den echten Stand, alle anderen erst nach
     * der Veröffentlichung etwas (AUF-23, Annahme: auch dass es einen Entwurf gibt, bleibt verborgen).
     */
    public static CallUpStatus visibleStatus(CallUpStatus status, boolean coachOrAdmin) {
        if (coachOrAdmin || status == CallUpStatus.PUBLISHED) {
            return status;
        }
        return CallUpStatus.NONE;
    }

    /** Sicht einer Spielerin (AUF-24): vor der Veröffentlichung «noch kein Aufgebot». */
    public static MyCallUp myCallUp(CallUpStatus status, boolean calledUp) {
        if (status != CallUpStatus.PUBLISHED) {
            return MyCallUp.NO_CALL_UP_YET;
        }
        return calledUp ? MyCallUp.CALLED_UP : MyCallUp.NOT_CALLED_UP;
    }
}
