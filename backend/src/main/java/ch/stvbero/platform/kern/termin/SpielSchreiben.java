package ch.stvbero.platform.kern.termin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;

/** Spiel erfassen oder ändern, z.B. das Resultat (AUF-14, AUF-15). */
@Schema(name = "SpielSchreiben")
public record SpielSchreiben(
        @NotBlank @Size(max = 80) String gegner,
        @NotNull OffsetDateTime anspielzeit,
        @NotNull Boolean heim,
        @Pattern(regexp = SpielRegeln.RESULTAT_MUSTER, message = SpielRegeln.RESULTAT_TEXT) String resultat) {
}
