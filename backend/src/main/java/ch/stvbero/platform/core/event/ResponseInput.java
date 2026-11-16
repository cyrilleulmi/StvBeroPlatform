package ch.stvbero.platform.core.event;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Antwort setzen oder ändern. Kommentar: Pflicht bei «vielleicht» (ANM-04), freiwillig bei «nicht dabei» (ANM-05). */
@Schema(name = "ResponseInput")
public record ResponseInput(
        @NotNull ResponseChoice status,
        @Size(max = 200, message = "Höchstens 200 Zeichen.") String comment) {
}
