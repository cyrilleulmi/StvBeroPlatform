package ch.stvbero.platform.attendance.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

/**
 * Rank of the own team in the league table last loaded (AUF-34, F-17), shown with the matches of a match day.
 *
 * @param teams number of teams in the table
 */
@Schema(name = "Standing")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StandingDto(UUID teamId, int rank, int teams, String league, String group) {
}
