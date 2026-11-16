package ch.stvbero.platform.core.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;

/** Zeitraum, der von einer Serie ausgenommen ist, z.B. Ferien (TER-07). */
@Schema(name = "SeriesException")
public record SeriesExceptionDto(@NotNull LocalDate startDate, @NotNull LocalDate endDate, @NotBlank @Size(max = 200) String reason) {
}
