package ch.stvbero.platform.kern.termin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Grund einer Absage (TER-06). */
public record AbsagenDto(@NotBlank @Size(max = 200) String grund) {
}
