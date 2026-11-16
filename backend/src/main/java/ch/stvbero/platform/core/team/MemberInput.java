package ch.stvbero.platform.core.team;

import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "MemberInput")
public record MemberInput(
        @NotNull(message = "Bitte eine Person wählen.") UUID personId,
        @NotNull(message = "Bitte eine Funktion wählen.") TeamRole teamRole) {
}
