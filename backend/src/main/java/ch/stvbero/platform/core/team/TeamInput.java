package ch.stvbero.platform.core.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "TeamInput")
public record TeamInput(
        @NotBlank(message = "Bitte einen Namen eingeben.") @Size(max = 60, message = "Höchstens 60 Zeichen.") String name) {
}
