package ch.stvbero.platform.attendance.event;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * What swiss unihockey changed about an imported match since a coach or admin last confirmed it (SUH-12): the
 * values before and after, as texts for display. The change itself is already applied.
 */
@Schema(name = "SuChange")
public record SuChangeDto(OffsetDateTime changedAt, List<Item> changes) {

    /** What changed. */
    @Schema(name = "SuChangeField")
    public enum Field {
        START_TIME, VENUE, OPPONENT, HOME_AWAY
    }

    /** One changed value, e.g. START_TIME «So 22.11. 09:30» → «So 22.11. 10:15». */
    @Schema(name = "SuChangeItem")
    public record Item(Field field, String before, String after) {
    }
}
