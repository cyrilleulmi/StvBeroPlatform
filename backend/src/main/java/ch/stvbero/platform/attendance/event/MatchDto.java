package ch.stvbero.platform.attendance.event;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Match of a match day (AUF-14, AUF-15). */
@Schema(name = "Match")
public record MatchDto(UUID id, String opponent, OffsetDateTime startTime, boolean home, String result,
                       MatchSource source, Integer suMatchId, boolean missingAtSu,
                       @Schema(nullable = true, description = "Unconfirmed change by swiss unihockey (SUH-12); "
                               + "`null` if there is none")
                       SuChangeDto suChange,
                       @Schema(nullable = true, description = "Rank of the opponent in the league table last loaded "
                               + "(AUF-34); `null` if unknown")
                       Integer opponentRank) {
}
