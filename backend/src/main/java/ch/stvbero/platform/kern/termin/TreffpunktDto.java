package ch.stvbero.platform.kern.termin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;

/** Zeit und Ort, an dem sich das Team vor einem Spieltag trifft (AUF-12). */
@Schema(name = "Treffpunkt")
public record TreffpunktDto(@NotNull OffsetDateTime zeit, @NotBlank @Size(max = 120) String ort) {
}
