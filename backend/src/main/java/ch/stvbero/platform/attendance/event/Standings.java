package ch.stvbero.platform.attendance.event;

import jakarta.enterprise.inject.Instance;

/**
 * Ranks of the league table for the matches of a match day (AUF-34): the own team and its opponents. The events
 * know no league: the module that loads the league data implements {@link Source}. Without one, and without
 * league data, everything is unknown.
 */
public interface Standings {

    /** Nothing known. */
    Standings NONE = new Standings() {
        @Override
        public StandingDto own() {
            return null;
        }

        @Override
        public Integer rankOf(String team) {
            return null;
        }
    };

    /** Rank of the own team, or {@code null}. */
    StandingDto own();

    /** Rank of a team by its name as in the matches, or {@code null} if it is not in the table. */
    Integer rankOf(String team);

    /** The source of the application, or one that knows nothing if no module offers ranks. */
    static Source sourceOf(Instance<Source> sources) {
        return sources.isResolvable() ? sources.get() : event -> NONE;
    }

    /** Extension point: where the ranks for a match day come from. */
    interface Source {

        /** Never {@code null}; {@link Standings#NONE} if nothing is known. */
        Standings of(Event event);
    }
}
