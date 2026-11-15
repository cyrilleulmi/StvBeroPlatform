package ch.stvbero.platform.kern.team;

import jakarta.validation.constraints.NotNull;

public record FunktionSchreiben(@NotNull(message = "Bitte eine Funktion wählen.") Funktion funktion) {
}
