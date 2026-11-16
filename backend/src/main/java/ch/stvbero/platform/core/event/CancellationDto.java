package ch.stvbero.platform.core.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Grund einer Absage (TER-06). */
public record CancellationDto(@NotBlank @Size(max = 200) String reason) {
}
