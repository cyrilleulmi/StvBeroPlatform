package ch.stvbero.platform.core.team;

import jakarta.validation.constraints.NotNull;

public record TeamRoleInput(@NotNull(message = "Bitte eine Funktion wählen.") TeamRole teamRole) {
}
