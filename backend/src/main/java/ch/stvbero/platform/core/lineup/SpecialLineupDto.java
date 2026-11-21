package ch.stvbero.platform.core.lineup;

import ch.stvbero.platform.core.event.ResponseStatus;
import ch.stvbero.platform.core.person.PersonSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * A special line-up of a team (LIN-13, LIN-14): a named line-up for a situation such as power play or penalty
 * kill, with a free number of places. Each place has an optional label and at most one player.
 */
@Schema(name = "SpecialLineup")
public record SpecialLineupDto(UUID id, String name, List<Place> places) {

    /** A place of a line-up. {@code person} is missing while the place is empty. */
    @Schema(name = "SpecialLineupPlace")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Place(int position, String label, PersonSummary person) {
    }

    /** The special line-ups of a team in the current season, with the players that can be set (LIN-15). */
    @Schema(name = "SpecialLineups")
    public record OfTeam(UUID teamId,
                         @Schema(description = "The caller may create, change and delete line-ups (coach, admin)") boolean canEdit,
                         @Schema(description = "In the order they were created") List<SpecialLineupDto> lineups,
                         @Schema(description = "Players of the team, by name") List<PersonSummary> players) {
    }

    /** Request: create or change a line-up; the places replace the existing ones (LIN-15). */
    @Schema(name = "SpecialLineupInput")
    public record Input(
            @NotBlank(message = "Bitte einen Namen eingeben.") @Size(max = 40, message = "Höchstens 40 Zeichen.") String name,
            @NotEmpty(message = SpecialLineupRules.PLACES_TEXT)
            @Size(max = SpecialLineupRules.MAX_PLACES, message = SpecialLineupRules.PLACES_TEXT)
            List<@Valid @NotNull PlaceInput> places) {
    }

    @Schema(name = "SpecialLineupPlaceInput")
    public record PlaceInput(
            @Schema(nullable = true) @Size(max = 30, message = "Höchstens 30 Zeichen.") String label,
            @Schema(nullable = true, description = "Player of the team; missing: the place stays empty") UUID personId) {
    }

    /** The special line-ups of the team of a match day, with the responses of the players (LIN-16). */
    @Schema(name = "EventLineups")
    public record OfEvent(UUID eventId, List<EventLineup> lineups) {
    }

    @Schema(name = "EventLineup")
    public record EventLineup(UUID id, String name, List<EventPlace> places) {
    }

    /**
     * A place on a match day.
     *
     * @param response response of the player to the match day; missing for an empty place
     * @param calledUp with a published call-up: whether she is called up; otherwise missing
     * @param gap      the player is not coming: declined, or not called up (LIN-16)
     */
    @Schema(name = "EventLineupPlace")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EventPlace(int position, String label, PersonSummary person, ResponseStatus response,
                             Boolean calledUp, boolean gap) {
    }
}
