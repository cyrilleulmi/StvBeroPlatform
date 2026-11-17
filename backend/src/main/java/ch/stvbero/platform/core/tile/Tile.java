package ch.stvbero.platform.core.tile;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Tile")
public record Tile(String id, String title, String icon, String route, TileColor color, String hint) {
}
