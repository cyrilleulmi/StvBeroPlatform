package ch.stvbero.platform.core.team;

import java.util.UUID;

/**
 * Extension point: modules react when teams change. The core knows neither events nor absences, so the modules
 * that own them implement this. Called in the transaction of the change.
 */
public interface TeamListener {

    /** A new team exists. */
    default void teamCreated(Team team) {
    }

    /** A person joined a team. */
    default void memberJoined(UUID personId) {
    }
}
