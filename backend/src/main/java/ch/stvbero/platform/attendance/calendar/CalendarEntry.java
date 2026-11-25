package ch.stvbero.platform.attendance.calendar;

import ch.stvbero.platform.attendance.event.EventType;
import ch.stvbero.platform.attendance.event.ResponseStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One event as it goes into the feed of one person (CAL-03). Holds only what this person may see: her own
 * response, no responses or comments of others (CAL-08).
 */
record CalendarEntry(UUID id, EventType type, String title, Instant start, Instant end, boolean cancelled,
                     String cancellationReason, List<String> teams, String locationName, String locationAddress,
                     String mapsUrl, Double latitude, Double longitude, Instant meetingPointTime,
                     String meetingPointLocation, String notes, List<MatchLine> matches, ResponseStatus myResponse,
                     String myComment) {

    record MatchLine(Instant start, String opponent, boolean home, String result) {
    }
}
