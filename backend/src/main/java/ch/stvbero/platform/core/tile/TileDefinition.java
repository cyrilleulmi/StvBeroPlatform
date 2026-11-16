package ch.stvbero.platform.core.tile;

import ch.stvbero.platform.core.auth.CurrentUser;

/**
 * Eine Kachel registriert sich, indem sie dieses Interface als CDI-Bean umsetzt (NF-10, NF-11).
 * Das Dashboard zeigt alle Kacheln, die für die angemeldete Person sichtbar sind (NF-12, RR-05).
 */
public interface TileDefinition {

    /** Fachlicher Schlüssel, z.B. «anwesenheit». Ergibt die Test-ID {@code kachel-<id>}. */
    String id();

    String title();

    /** Lucide-Icon-Name, z.B. «calendar-check». */
    String icon();

    /** Route im Frontend, z.B. «/termine». */
    String route();

    TileColor color();

    /** Reihenfolge auf dem Dashboard, aufsteigend. */
    int sortOrder();

    /** Ob die Person die Kachel sehen darf (RR-05). Standard: alle Angemeldeten. */
    default boolean visibleFor(CurrentUser person) {
        return true;
    }

    /** Kurzer Status, z.B. «2 offen». */
    default String hint(CurrentUser person) {
        return null;
    }
}
