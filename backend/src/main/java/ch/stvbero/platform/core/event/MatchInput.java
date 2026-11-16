package ch.stvbero.platform.core.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;

/** Spiel erfassen oder ändern, z.B. das Resultat (AUF-14, AUF-15). */
@Schema(name = "MatchInput")
public record MatchInput(
        @NotBlank @Size(max = 80) String opponent,
        @NotNull OffsetDateTime startTime,
        @NotNull Boolean home,
        @Pattern(regexp = MatchRules.RESULT_PATTERN, message = MatchRules.RESULT_TEXT) String result) {
}
