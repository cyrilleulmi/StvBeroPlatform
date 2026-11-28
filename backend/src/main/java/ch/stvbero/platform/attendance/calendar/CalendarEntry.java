package ch.stvbero.platform.attendance.calendar;

import ch.stvbero.platform.attendance.event.EventType;
import ch.stvbero.platform.attendance.event.ResponseStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One event as it goes into the feed of one person (CAL-03). Holds only what this person may see: her own
 * response, no responses or comments of others (CAL-08). {@code start} is the start of the event itself; with a
 * pre-training the entry begins earlier (TER-31), unless she is at the practice without it (ANM-14).
 */
record CalendarEntry(UUID id, EventType type, String title, Instant start, Instant end, boolean cancelled,
                     String cancellationReason, List<String> teams, String locationName, String locationAddress,
                     String mapsUrl, Double latitude, Double longitude, Instant meetingPointTime,
                     String meetingPointLocation, String notes, List<MatchLine> matches, ResponseStatus myResponse,
                     String myComment, PreTrainingLine preTraining) {

    /** Entry without a pre-training. */
    CalendarEntry(UUID id, EventType type, String title, Instant start, Instant end, boolean cancelled,
                  String cancellationReason, List<String> teams, String locationName, String locationAddress,
                  String mapsUrl, Double latitude, Double longitude, Instant meetingPointTime,
                  String meetingPointLocation, String notes, List<MatchLine> matches, ResponseStatus myResponse,
                  String myComment) {
        this(id, type, title, start, end, cancelled, cancellationReason, teams, locationName, locationAddress,
                mapsUrl, latitude, longitude, meetingPointTime, meetingPointLocation, notes, matches, myResponse,
                myComment, null);
    }

    record MatchLine(Instant start, String opponent, boolean home, String result) {
    }

    /**
     * Pre-training before a practice (TER-29): title and start; it ends when the practice starts.
     * {@code myResponse}: the person's response to it (ANM-14, ANM-15).
     */
    record PreTrainingLine(String title, Instant start, ResponseStatus myResponse) {

        PreTrainingLine(String title, Instant start) {
            this(title, start, ResponseStatus.PENDING);
        }
    }

    /** The person is at the practice, but said «nicht dabei» to the pre-training (ANM-14). */
    boolean withoutPreTraining() {
        return preTraining != null && myResponse == ResponseStatus.ATTENDING
                && preTraining.myResponse() == ResponseStatus.DECLINED;
    }

    /** When the entry begins in the calendar: with the pre-training, if there is one the person does not skip. */
    Instant begin() {
        return preTraining == null || withoutPreTraining() ? start : preTraining.start();
    }
}
