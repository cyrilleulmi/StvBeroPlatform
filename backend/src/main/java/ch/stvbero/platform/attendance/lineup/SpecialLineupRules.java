package ch.stvbero.platform.attendance.lineup;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.attendance.event.ResponseStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Domain rules of the special line-ups (LIN-13 to LIN-17). Without a database, so that unit tests can check them. */
public final class SpecialLineupRules {

    public static final int MAX_PLACES = 10;
    public static final int MAX_PER_TEAM = 12;

    public static final String PLACES_TEXT = "Eine Aufstellung hat 1 bis 10 Plätze.";
    public static final String TOO_MANY = "Ein Team hat höchstens " + MAX_PER_TEAM + " Spezialaufstellungen.";
    public static final String NAME_TAKEN = "Diesen Namen hat schon eine andere Aufstellung dieses Teams.";
    public static final String TWICE = "Eine Spielerin oder ein Spieler steht höchstens einmal in einer Aufstellung.";
    public static final String NOT_A_PLAYER = "Nur Spielerinnen und Spieler dieses Teams können aufgestellt werden.";

    private SpecialLineupRules() {
    }

    /** A team has at most {@link #MAX_PER_TEAM} special line-ups per season (LIN-13). */
    public static void checkRoom(long existing) {
        if (existing >= MAX_PER_TEAM) {
            throw DomainError.conflict("Zu viele Aufstellungen", TOO_MANY, "LIN-13");
        }
    }

    /**
     * The players of the places (LIN-14): each at most once, and every one a player of the team. Empty places
     * ({@code null}) are fine.
     */
    public static void checkPlayers(List<UUID> personIds, Set<UUID> playersOfTeam) {
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < personIds.size(); i++) {
            UUID person = personIds.get(i);
            if (person == null) {
                continue;
            }
            String field = "places[" + i + "].personId";
            if (!playersOfTeam.contains(person)) {
                throw DomainError.ruleViolation(field, "Nicht im Team", NOT_A_PLAYER, "LIN-14");
            }
            if (!seen.add(person)) {
                throw DomainError.ruleViolation(field, "Doppelt aufgestellt", TWICE, "LIN-14");
            }
        }
    }

    /**
     * A place is a gap on a match day when its player is not coming (LIN-16): she declined, or the call-up is
     * published and she is not called up.
     */
    public static boolean gap(boolean hasPlayer, ResponseStatus response, boolean callUpPublished, boolean calledUp) {
        if (!hasPlayer) {
            return false;
        }
        return response == ResponseStatus.DECLINED || (callUpPublished && !calledUp);
    }
}
