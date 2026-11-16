package ch.stvbero.platform.core.swissunihockey;

import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.error.Problem;
import ch.stvbero.platform.core.team.TeamDto;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/** Connection to swiss unihockey (SUH-02, SUH-03). Admins only (docs/requirements/07-roles-permissions.md). */
@Path("/api/teams/{teamId}/swiss-unihockey")
@Tag(name = "SwissUnihockey")
public class SwissUnihockeyResource {

    @Inject
    AuthContext auth;

    @Inject
    SuSync sync;

    public record SuLink(
            @Schema(type = SchemaType.INTEGER, nullable = true, description = "`null` removes the link")
            Integer suTeamId) {
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "linkSu",
            summary = "Link a team to swiss unihockey or remove the link (SUH-02, admin)")
    @APIResponse(responseCode = "200", description = "Saved",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TeamDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Team ID already linked to another team",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public TeamDto link(@PathParam("teamId") UUID teamId, @Valid @NotNull SuLink input) {
        return sync.link(auth.person(), teamId, input.suTeamId());
    }

    @POST
    @Path("/sync")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "syncSu",
            summary = "Run the sync now instead of waiting for the daily run (SUH-03, admin)")
    @APIResponse(responseCode = "200", description = "Ergebnis des Abgleichs",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = SyncResultDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Not linked or not in the current season",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "502", description = "swiss unihockey unavailable or unexpected response (SUH-09)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public SyncResultDto sync(@PathParam("teamId") UUID teamId) {
        return sync.sync(auth.person(), teamId);
    }
}
