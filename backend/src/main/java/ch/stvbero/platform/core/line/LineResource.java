package ch.stvbero.platform.core.line;

import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.error.Problem;
import ch.stvbero.platform.core.team.TeamDetailDto;
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

/** Lines and goalies of a team in the current season (LIN). */
@Path("/api/teams/{teamId}")
@Tag(name = "Lines")
public class LineResource {

    @Inject
    AuthContext auth;

    @Inject
    LineService lines;

    @GET
    @Path("/groups")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getLineup", summary = "Lines and goalies of a team with their players, plus «Ohne Linie» (no line)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TeamDetailDto.Lineup.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public TeamDetailDto.Lineup get(@PathParam("teamId") UUID teamId) {
        return lines.get(auth.person(), teamId);
    }

    @POST
    @Path("/groups")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "createLine", summary = "Create a line (LIN-07, coach). Each team automatically has exactly one goalies group.")
    @APIResponse(responseCode = "201", description = "Created",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = GroupDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response create(@PathParam("teamId") UUID teamId, @Valid @NotNull LineInput input) {
        return Response.status(201).entity(lines.create(auth.person(), teamId, input)).build();
    }

    @PUT
    @Path("/groups/{groupId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateLine", summary = "Rename a line or change its order (coach)")
    @APIResponse(responseCode = "200", description = "Updated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = GroupDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public GroupDto update(@PathParam("teamId") UUID teamId, @PathParam("groupId") UUID groupId,
                             @Valid @NotNull LineInput input) {
        return lines.update(auth.person(), teamId, groupId, input);
    }

    @DELETE
    @Path("/groups/{groupId}")
    @Operation(operationId = "deleteLine", summary = "Delete a line (coach). Its players then have no line. Goalies cannot be deleted (409).")
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public void delete(@PathParam("teamId") UUID teamId, @PathParam("groupId") UUID groupId) {
        lines.delete(auth.person(), teamId, groupId);
    }

    @PUT
    @Path("/assignments/{personId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "setAssignment", summary = "Assign a player to a group or set her to no line (LIN-02, LIN-07, LIN-10)")
    @APIResponse(responseCode = "204", description = "Set")
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public void assign(@PathParam("teamId") UUID teamId, @PathParam("personId") UUID personId,
                         @Valid @NotNull AssignmentInput input) {
        lines.assign(auth.person(), teamId, personId, input.groupId());
    }
}
