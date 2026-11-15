package ch.stvbero.platform.kern.termin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Ort mit optionalem Google-Maps-Link (AUF-10), der mit https:// beginnen muss. */
@Schema(name = "Ort")
public record OrtDto(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 200) String adresse,
        @Size(max = 500) @Pattern(regexp = "^https://\\S+$", message = "Der Link muss mit https:// beginnen.") String mapsUrl,
        Double breite,
        Double laenge) {
}
