package ch.stvbero.platform.kern.termin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Serie anlegen oder ändern (TER-05, TER-08, TER-10). */
@Schema(name = "SerieSchreiben")
public record SerieSchreiben(
        @NotNull SerienTyp typ,
        @NotBlank @Size(max = 80) String titel,
        @NotEmpty List<@NotNull UUID> teamIds,
        @NotNull Wochentag wochentag,
        @NotNull @Pattern(regexp = TerminRegeln.ZEIT_MUSTER, message = "Bitte eine Zeit im Format 18:30 angeben.") String beginnZeit,
        @NotNull @Pattern(regexp = TerminRegeln.ZEIT_MUSTER, message = "Bitte eine Zeit im Format 18:30 angeben.") String endeZeit,
        @NotNull LocalDate gueltigVon,
        @NotNull LocalDate gueltigBis,
        @Valid OrtDto ort,
        @Min(value = 0, message = TerminRegeln.ANTWORTFRIST_MINDESTENS_TEXT)
        @Max(value = TerminRegeln.ANTWORTFRIST_HOECHSTENS, message = TerminRegeln.ANTWORTFRIST_HOECHSTENS_TEXT) Integer antwortfristStunden) {
}
