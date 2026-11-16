package ch.stvbero.platform.core.event;

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

/** Matches and call-up of a match day (AUF). */
@Path("/api/events/{eventId}")
@Tag(name = "MatchDays")
public class MatchResource {

    @Inject
    AuthContext auth;

    @Inject
    MatchService matches;

    @Inject
    CallUpService callUps;

    @POST
    @Path("/matches")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "addMatch", summary = "Add a match to a match day (AUF-14, coach)")
    @APIResponse(responseCode = "201", description = "Created",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = MatchDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response add(@PathParam("eventId") UUID eventId, @Valid @NotNull MatchInput input) {
        return Response.status(201).entity(matches.add(auth.person(), eventId, input)).build();
    }

    @PUT
    @Path("/matches/{matchId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateMatch", summary = "Update a match, e.g. enter the result (AUF-15, coach). Imported fields are read-only (409).")
    @APIResponse(responseCode = "200", description = "Updated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = MatchDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public MatchDto update(@PathParam("eventId") UUID eventId, @PathParam("matchId") UUID matchId,
                            @Valid @NotNull MatchInput input) {
        return matches.update(auth.person(), eventId, matchId, input);
    }

    @DELETE
    @Path("/matches/{matchId}")
    @Operation(operationId = "deleteMatch", summary = "Delete a manually entered match (coach)")
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response delete(@PathParam("eventId") UUID eventId, @PathParam("matchId") UUID matchId) {
        matches.delete(auth.person(), eventId, matchId);
        return Response.noContent().build();
    }

    @GET
    @Path("/call-up")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getCallUp", summary = "Call-up with all players of the team, grouped, with response status (AUF-21, AUF-22, coach)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CallUpDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public CallUpDto callUp(@PathParam("eventId") UUID eventId) {
        return callUps.get(auth.person(), eventId);
    }

    @PUT
    @Path("/call-up")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "setCallUp", summary = "Save the selection (coach). If the call-up is published, the players affected are notified (AUF-27).")
    @APIResponse(responseCode = "200", description = "Saved",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CallUpDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "422", description = "Domain rule violated",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public CallUpDto setCallUp(@PathParam("eventId") UUID eventId, @Valid @NotNull CallUpInput input) {
        return callUps.set(auth.person(), eventId, input);
    }

    @POST
    @Path("/call-up/publish")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "publishCallUp", summary = "Publish the call-up and notify all players of the team (AUF-23, AUF-24, coach)")
    @APIResponse(responseCode = "200", description = "Published",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CallUpDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public CallUpDto publish(@PathParam("eventId") UUID eventId) {
        return callUps.publish(auth.person(), eventId);
    }
}
