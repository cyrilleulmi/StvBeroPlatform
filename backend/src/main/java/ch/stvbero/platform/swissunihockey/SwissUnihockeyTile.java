package ch.stvbero.platform.swissunihockey;

import ch.stvbero.platform.core.tile.TileColor;
import ch.stvbero.platform.core.tile.TileDefinition;
import jakarta.enterprise.context.ApplicationScoped;

/** Tile «swiss unihockey»: the links of the own teams to swiss unihockey (SUH-16). Visible to everyone who is logged in. */
@ApplicationScoped
public class SwissUnihockeyTile implements TileDefinition {

    @Override
    public String id() {
        return "swissunihockey";
    }

    @Override
    public String title() {
        return "swiss unihockey";
    }

    @Override
    public String icon() {
        return "trophy";
    }

    @Override
    public String route() {
        return "/swissunihockey";
    }

    @Override
    public TileColor color() {
        return TileColor.mustard;
    }

    @Override
    public int sortOrder() {
        return 30;
    }
}
