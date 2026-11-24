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
                         Boolean travelsIndividually) {

    public static final ResponseDto PENDING = new ResponseDto(ResponseStatus.PENDING, null, null, null, false, null);
}
