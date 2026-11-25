package ch.stvbero.platform.swissunihockey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;

/** Simplified form of a match as the simulator delivers it (test support). */
@Schema(name = "SuMatch", description = "Simplified form of a match, as the simulator delivers it")
public record SuMatchDto(@NotNull @Positive Integer suMatchId,
                         @NotNull OffsetDateTime startTime,
                         @NotBlank String homeTeam,
                         @NotBlank String awayTeam,
                         @NotBlank String hall,
                         String location,
                         String mapsUrl,
                         Double latitude,
                         Double longitude,
                         String result) {

    public SuMatch toMatch() {
        return new SuMatch(suMatchId, startTime.toInstant(), homeTeam.strip(), awayTeam.strip(), hall.strip(),
                empty(location), empty(mapsUrl), latitude, longitude, empty(result));
    }

    private static String empty(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
