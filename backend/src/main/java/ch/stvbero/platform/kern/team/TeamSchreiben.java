package ch.stvbero.platform.kern.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "TeamSchreiben")
public record TeamSchreiben(
        @NotBlank(message = "Bitte einen Namen eingeben.") @Size(max = 60, message = "Höchstens 60 Zeichen.") String name) {
}
