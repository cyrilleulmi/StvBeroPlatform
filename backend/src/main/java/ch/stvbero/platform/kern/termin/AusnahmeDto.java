package ch.stvbero.platform.kern.termin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;

/** Zeitraum, der von einer Serie ausgenommen ist, z.B. Ferien (TER-07). */
@Schema(name = "Ausnahme")
public record AusnahmeDto(@NotNull LocalDate von, @NotNull LocalDate bis, @NotBlank @Size(max = 200) String grund) {
}
