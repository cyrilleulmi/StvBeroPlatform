package ch.stvbero.platform.attendance.event;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Set or change a response. Comment: required for «vielleicht» (ANM-04), optional for «nicht dabei» (ANM-05). */
@Schema(name = "ResponseInput")
public record ResponseInput(
        @NotNull ResponseChoice status,
        @Size(max = 200, message = "Höchstens 200 Zeichen.") String comment,
        @Schema(nullable = true, description = "Match days: with «dabei», the person travels on her own and needs no "
                + "seat (ANM-13). Missing: unchanged")
        Boolean travelsIndividually) {
}
