package ch.stvbero.platform.core.tile;

import ch.stvbero.platform.core.auth.CurrentUser;

/**
 * A tile registers itself by implementing this interface as a CDI bean (NF-10, NF-11).
 * The dashboard shows all tiles that are visible to the signed-in user (NF-12, RR-05).
 */
public interface TileDefinition {

    /** Domain key, e.g. {@code anwesenheit}. Gives the test ID {@code tile-<id>}. */
    String id();

    String title();

    /** Lucide icon name, e.g. {@code calendar-check}. */
    String icon();

    /** Route in the frontend, e.g. {@code /termine}. */
    String route();

    TileColor color();

    /** Order on the dashboard, ascending. */
    int sortOrder();

    /** Whether the user may see the tile (RR-05). Default: everyone who is signed in. */
    default boolean visibleFor(CurrentUser user) {
        return true;
    }

    /** Short status, e.g. «2 offen». */
    default String hint(CurrentUser user) {
        return null;
    }
}
