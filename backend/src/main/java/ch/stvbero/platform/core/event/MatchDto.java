package ch.stvbero.platform.core.event;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Match of a match day (AUF-14, AUF-15). */
@Schema(name = "Match")
public record MatchDto(UUID id, String opponent, OffsetDateTime startTime, boolean home, String result,
                       MatchSource source, Integer suMatchId, boolean missingAtSu) {
}
