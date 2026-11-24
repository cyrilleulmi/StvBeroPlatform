package ch.stvbero.platform.attendance;

import ch.stvbero.platform.core.tile.TileColor;
import ch.stvbero.platform.core.tile.TileDefinition;
import jakarta.enterprise.context.ApplicationScoped;

/** Tile «Fahrerliste»: the season page of the drivers (DRV-24). Visible to everyone who is logged in. */
@ApplicationScoped
public class DriverPlanTile implements TileDefinition {

    @Override
    public String id() {
        return "fahrerliste";
    }

    @Override
    public String title() {
        return "Fahrerliste";
    }

    @Override
    public String icon() {
        return "car";
    }

    @Override
    public String route() {
        return "/fahrerliste";
    }

    @Override
    public TileColor color() {
        return TileColor.blue;
    }

    @Override
    public int sortOrder() {
        return 20;
    }
}
