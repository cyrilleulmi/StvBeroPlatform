package ch.stvbero.platform.core.line;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Name and (optional) position of a line. Without a sort order a new line goes to the end (LIN-09). */
@Schema(name = "LineInput")
public record LineInput(
        @NotBlank(message = "Bitte einen Namen eingeben.") @Size(max = 30, message = "Höchstens 30 Zeichen.") String name,
        @Min(value = 1, message = "Die Reihenfolge beginnt bei 1.") Integer sortOrder) {
}
