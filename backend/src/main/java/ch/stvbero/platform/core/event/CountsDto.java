package ch.stvbero.platform.core.event;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Number of players per response status. */
@Schema(name = "Counts")
public record CountsDto(int attending, int declined, int maybe, int pending) {
}
