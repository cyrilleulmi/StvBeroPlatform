package ch.stvbero.platform.kern.termin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Einzeltermin erfassen oder ändern (TER-04, TER-10, TER-11). */
@Schema(name = "TerminSchreiben")
public record TerminSchreiben(
        @NotNull TerminTyp typ,
        @NotBlank @Size(max = 80) String titel,
        @NotNull OffsetDateTime beginn,
        @NotNull OffsetDateTime ende,
        @NotEmpty List<@NotNull UUID> teamIds,
        @Valid OrtDto ort,
        @Size(max = 500) String hinweise,
        @Min(0) @Max(TerminRegeln.ANTWORTFRIST_HOECHSTENS) Integer antwortfristStunden,
        @Valid TreffpunktDto treffpunkt) {
}
