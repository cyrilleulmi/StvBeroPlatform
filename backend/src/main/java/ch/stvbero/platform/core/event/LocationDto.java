package ch.stvbero.platform.core.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Ort mit optionalem Google-Maps-Link (AUF-10), der mit https:// beginnen muss. */
@Schema(name = "Location")
public record LocationDto(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 200) String address,
        @Size(max = 500) @Pattern(regexp = "^https://\\S+$", message = "Der Link muss mit https:// beginnen.") String mapsUrl,
        Double latitude,
        Double longitude) {
}
