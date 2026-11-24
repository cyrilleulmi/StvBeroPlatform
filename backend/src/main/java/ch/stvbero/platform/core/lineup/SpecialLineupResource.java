package ch.stvbero.platform.core.lineup;

import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.error.Problem;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/** Special line-ups of a team, e.g. power play and penalty kill (LIN-13 to LIN-17). */
@Path("/api/teams/{teamId}/special-lineups")
@Tag(name = "Lines")
public class SpecialLineupResource {

    @Inject
    AuthContext auth;

    @Inject
    SpecialLineupService lineups;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getSpecialLineups",
            summary = "Special line-ups of a team in the current season, with the players that can be set (LIN-13)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SpecialLineupDto.OfTeam.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public SpecialLineupDto.OfTeam get(@PathParam("teamId") UUID teamId) {
        return lineups.get(auth.person(), teamId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "createSpecialLineup", summary = "Create a special line-up with its places (LIN-13 to LIN-15, coach)")
    @APIResponse(responseCode = "201", description = "Created",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SpecialLineupDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Too many line-ups, or the season is archived",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "422", description = "Name already used, a player twice, or not a player of the team",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response create(@PathParam("teamId") UUID teamId, @Valid @NotNull SpecialLineupDto.Input input) {
        return Response.status(201).entity(lineups.create(auth.person(), teamId, input)).build();
    }

    @PUT
    @Path("/{lineupId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateSpecialLineup",
            summary = "Change name, number of places, labels and players of a special line-up (LIN-15, coach)")
    @APIResponse(responseCode = "200", description = "Changed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SpecialLineupDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "The season is archived",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "422", description = "Name already used, a player twice, or not a player of the team",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public SpecialLineupDto update(@PathParam("teamId") UUID teamId, @PathParam("lineupId") UUID lineupId,
                                   @Valid @NotNull SpecialLineupDto.Input input) {
        return lineups.update(auth.person(), teamId, lineupId, input);
    }

    @DELETE
    @Path("/{lineupId}")
    @Operation(operationId = "deleteSpecialLineup", summary = "Delete a special line-up (LIN-15, coach)")
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "The season is archived",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response delete(@PathParam("teamId") UUID teamId, @PathParam("lineupId") UUID lineupId) {
        lineups.delete(auth.person(), teamId, lineupId);
        return Response.noContent().build();
    }
}
