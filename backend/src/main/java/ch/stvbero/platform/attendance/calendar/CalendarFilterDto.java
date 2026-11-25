package ch.stvbero.platform.attendance.calendar;

import ch.stvbero.platform.attendance.event.EventType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Filter of the calendar feed as the profile shows it (CAL-10 to CAL-14).
 *
 * @param types     event types in the feed
 * @param responses participations in the feed besides «dabei», which is always in it
 * @param teams     the person's teams in the current season, each with whether its events are in the feed
 */
@Schema(name = "CalendarFilter")
public record CalendarFilterDto(
        @Schema(required = true) List<EventType> types,
        @Schema(required = true) List<CalendarResponse> responses,
        @Schema(required = true) List<Team> teams) {

    /** A team of the person and whether its events are in the feed (CAL-13). */
    @Schema(name = "CalendarFilterTeam")
    public record Team(
            @Schema(required = true) UUID teamId,
            @Schema(required = true, examples = "Team 1") String name,
            @Schema(required = true) boolean included) {
    }
}
