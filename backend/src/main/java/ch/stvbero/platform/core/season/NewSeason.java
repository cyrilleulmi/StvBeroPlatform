package ch.stvbero.platform.core.season;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "NewSeason")
public record NewSeason(
        @NotBlank(message = "Bitte einen Namen eingeben, z.B. 2027/28.") @Size(max = 20, message = "Höchstens 20 Zeichen.") String name,
        @NotNull(message = "Bitte ein Datum eingeben.") LocalDate startDate,
        @NotNull(message = "Bitte ein Datum eingeben.") LocalDate endDate,
        UUID copyFrom) {
}
