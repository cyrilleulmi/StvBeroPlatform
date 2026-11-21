package ch.stvbero.platform.core.swissunihockey;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Small request and response records of the connection to swiss unihockey. */
public final class SuDtos {

    private SuDtos() {
    }

    /** Request: link a team with a team at swiss unihockey (SUH-02). */
    @Schema(name = "SuLinkInput")
    public record LinkInput(
            @NotNull(message = "Bitte ein Team wählen oder die Team-ID eingeben.")
            @Positive(message = "Die Team-ID ist eine positive Zahl.") Integer suTeamId,
            @Schema(nullable = true, description = "Name at swiss unihockey; omitted: looked up in the club's teams")
            @Size(max = 120, message = "Höchstens 120 Zeichen.") String name) {
    }

    /** A team of the club at swiss unihockey, for the link dialog (SUH-02). */
    @Schema(name = "SuClubTeam")
    public record ClubTeam(int suTeamId, String name,
                           @Schema(nullable = true, description = "Team of the app it is linked with this season")
                           UUID linkedTeamId,
                           @Schema(nullable = true) String linkedTeamName) {
    }

    /** How the connection to swiss unihockey is doing (SUH-13): for the hint to coaches and admins. */
    @Schema(name = "SuStatus")
    public record Status(
            @Schema(description = "Teams of the current season with at least one link") int linkedTeams,
            @Schema(nullable = true, description = "Time of the last successful sync of any team") OffsetDateTime lastSyncAt,
            @Schema(description = "Links whose last sync failed, by team name") List<Failure> failures) {
    }

    /** A link whose last sync failed. */
    @Schema(name = "SuSyncFailure")
    public record Failure(UUID teamId, String teamName, int suTeamId,
                          @Schema(nullable = true) String suTeamName, OffsetDateTime at, String message) {
    }
}
