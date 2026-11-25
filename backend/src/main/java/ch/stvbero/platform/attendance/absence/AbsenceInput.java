package ch.stvbero.platform.attendance.absence;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;

/** New or changed absence (ABS-01). */
@Schema(name = "AbsenceInput")
public record AbsenceInput(
        @NotNull @Schema(description = "First day away", required = true) LocalDate from,
        @NotNull @Schema(description = "Last day away (inclusive)", required = true) LocalDate to,
        @NotNull @Schema(required = true) AbsenceReason reason,
        @Size(max = 120) @Schema(description = "Optional note; required for OTHER", maxLength = 120) String note) {
}
