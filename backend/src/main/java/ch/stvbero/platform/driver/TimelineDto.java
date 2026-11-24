package ch.stvbero.platform.driver;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Timeline of a match day (TML): departure, arrival, time before the first match, the matches with the time
 * between them, cool down and the approximate return. Times that cannot be worked out are missing; {@code hints}
 * says why.
 */
@Schema(name = "Timeline")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimelineDto(
        UUID eventId,
        boolean cancelled,
        @Schema(description = "Why parts of the timeline are missing, or what to look at") List<Hint> hints,
        @Schema(description = "When the team leaves: the meeting time, otherwise the suggestion") OffsetDateTime departure,
        @Schema(description = "The departure is the suggestion; no meeting time is set yet (TML-04)") boolean departureSuggested,
        @Schema(description = "The meeting time was set by the automatic departure, not by hand (TML-09)") boolean departureAuto,
        @Schema(description = "Arrival target minus driving time, rounded down to five minutes (TML-04)") OffsetDateTime suggestedDeparture,
        @Schema(description = "When the team wants to be at the venue: first match minus the arrival lead (TML-03)") OffsetDateTime arriveBy,
        @Schema(description = "Driving time from the meeting point to the venue") Integer driveMinutes,
        @Schema(description = "One-way distance") Double distanceKm,
        int matchDurationMinutes,
        int arrivalLeadMinutes,
        int coolDownMinutes,
        @Schema(description = "Coach or admin can take over the suggestion as meeting time right now") boolean canApplySuggestion,
        List<Item> items) {

    /** Why parts of the timeline are missing, or what to look at. */
    @Schema(name = "TimelineHint")
    public enum Hint {
        /** The match day has no matches yet. */
        NO_MATCHES,
        /** No meeting point: no route, so no departure, arrival or return. */
        MEETING_POINT_MISSING,
        /** Meeting point and venue are set, but there is no driving time (no route found, service unavailable, kilometres entered by hand). */
        DRIVE_TIME_UNKNOWN,
        /** With the set meeting time the team arrives later than its arrival lead wants. */
        ARRIVES_LATE
    }

    /** A line of the timeline. {@code end} and {@code minutes} only for spans; the match fields only for a match. */
    @Schema(name = "TimelineItem")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(TimelineRules.Kind kind, OffsetDateTime start, OffsetDateTime end, Integer minutes,
                       UUID matchId, String opponent, Boolean home) {
    }
}
