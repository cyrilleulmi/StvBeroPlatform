package ch.stvbero.platform.kern.team;

import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "MitgliedSchreiben")
public record MitgliedSchreiben(
        @NotNull(message = "Bitte eine Person wählen.") UUID personId,
        @NotNull(message = "Bitte eine Funktion wählen.") Funktion funktion) {
}
