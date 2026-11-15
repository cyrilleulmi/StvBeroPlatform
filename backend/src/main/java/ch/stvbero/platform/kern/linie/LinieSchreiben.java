package ch.stvbero.platform.kern.linie;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Name und (freiwillig) Position einer Linie. Ohne Reihenfolge kommt eine neue Linie ans Ende (LIN-09). */
@Schema(name = "LinieSchreiben")
public record LinieSchreiben(
        @NotBlank(message = "Bitte einen Namen eingeben.") @Size(max = 30, message = "Höchstens 30 Zeichen.") String name,
        @Min(value = 1, message = "Die Reihenfolge beginnt bei 1.") Integer reihenfolge) {
}
