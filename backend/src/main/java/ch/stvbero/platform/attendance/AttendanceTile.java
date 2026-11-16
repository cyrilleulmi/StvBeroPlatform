package ch.stvbero.platform.attendance;

import ch.stvbero.platform.core.tile.TileDefinition;
import ch.stvbero.platform.core.tile.TileColor;
import jakarta.enterprise.context.ApplicationScoped;

/** Kachel «Anwesenheit»: Termine und An-/Abmeldung. Sichtbar für alle Angemeldeten. */
@ApplicationScoped
public class AttendanceTile implements TileDefinition {

    @Override
    public String id() {
        return "anwesenheit";
    }

    @Override
    public String title() {
        return "Anwesenheit";
    }

    @Override
    public String icon() {
        return "calendar-check";
    }

    @Override
    public String route() {
        return "/termine";
    }

    @Override
    public TileColor color() {
        return TileColor.primary;
    }

    @Override
    public int sortOrder() {
        return 10;
    }
}
