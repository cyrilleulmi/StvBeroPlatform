package ch.stvbero.platform.kern.swissunihockey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;

/** Vereinfachte Form eines Spiels, wie sie der Simulator liefert (Test-Hilfe). */
@Schema(name = "SuSpiel", description = "Vereinfachte Form eines Spiels, wie sie der Simulator liefert")
public record SuSpielDto(@NotNull @Positive Integer suSpielId,
                         @NotNull OffsetDateTime anspielzeit,
                         @NotBlank String heimteam,
                         @NotBlank String gastteam,
                         @NotBlank String halle,
                         String ort,
                         String mapsUrl,
                         Double breite,
                         Double laenge,
                         String resultat) {

    public SuSpiel alsSpiel() {
        return new SuSpiel(suSpielId, anspielzeit.toInstant(), heimteam.strip(), gastteam.strip(), halle.strip(),
                leer(ort), leer(mapsUrl), breite, laenge, leer(resultat));
    }

    private static String leer(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
