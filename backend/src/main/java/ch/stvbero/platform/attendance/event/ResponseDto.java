package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.person.PersonSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Response of a person to an event. Without a response: status PENDING. Fields the caller may not see
 * (comment, ANM-07) are left out of the API response.
 */
@Schema(name = "Response")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseDto(ResponseStatus status, String comment, OffsetDateTime changedAt,
                         PersonSummary enteredBy, boolean lateCancellation,
                         @Schema(description = "Travels to the match day on her own (ANM-13); missing if not")
                         Boolean travelsIndividually,
                         @Schema(description = "Response to the pre-training (ANM-14, ANM-15); missing if the event has none")
                         ResponseStatus preTraining) {

    public static final ResponseDto PENDING = new ResponseDto(ResponseStatus.PENDING, null, null, null, false, null,
            null);

    /** Without a response; for a practice with a pre-training that one is pending too (ANM-14). */
    public static ResponseDto pending(boolean hasPreTraining) {
        return hasPreTraining ? new ResponseDto(ResponseStatus.PENDING, null, null, null, false, null,
                ResponseStatus.PENDING) : PENDING;
    }
}
