package ch.stvbero.platform.core.absence;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

/** An absence of the logged-in person (ABS-01, ABS-06). */
@Schema(name = "Absence")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AbsenceDto(UUID id, LocalDate from, LocalDate to, AbsenceReason reason, String note,
                         @Schema(description = "Upcoming practices this absence declined (ABS-03)")
                         int declinedPractices) {
}
