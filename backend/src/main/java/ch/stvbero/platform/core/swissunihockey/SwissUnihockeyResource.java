package ch.stvbero.platform.core.swissunihockey;

import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.error.Problem;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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

/**
 * Connection of a team to swiss unihockey (SUH-02, SUH-03, SUH-11). Coaches (of any team) and admins
 * (docs/requirements/07-roles-permissions.md, E-84).
 */
@Path("/api/teams/{teamId}/swiss-unihockey")
@Tag(name = "SwissUnihockey")
public class SwissUnihockeyResource {

    @Inject
    AuthContext auth;

    @Inject
    SuSync sync;

    @Inject
    SuLinks links;

    @Inject
    SyncJob job;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getSuConnection",
            summary = "Links of the team to swiss unihockey in the current season, with the state of the last sync (SUH-11, SUH-13)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SuConnectionDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "No current season",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public SuConnectionDto connection(@PathParam("teamId") UUID teamId) {
        return links.connection(auth.person(), teamId);
    }

    @POST
    @Path("/links")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "addSuLink",
            summary = "Link the team with a team at swiss unihockey and load its matches and league right away "
                    + "(SUH-02, SUH-11, admin, coach). If swiss unihockey cannot be reached, the link is kept and "
                    + "the failure shows in `lastSyncOk`.")
    @APIResponse(responseCode = "201", description = "Linked",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SuConnectionDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Team ID already linked this season, or team not in the current season",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response addLink(@PathParam("teamId") UUID teamId, @Valid @NotNull SuDtos.LinkInput input) {
        links.add(auth.person(), teamId, input);
        // The link is stored. Loading runs in its own transactions and never fails the request (SUH-09).
        job.load(teamId);
        return Response.status(201).entity(links.connection(auth.person(), teamId)).build();
    }

    @DELETE
    @Path("/links/{linkId}")
    @Operation(operationId = "removeSuLink",
            summary = "Remove a link (SUH-11, admin, coach). Imported matches stay; without any link left, those "
                    + "of upcoming match days are marked as missing at swiss unihockey.")
    @APIResponse(responseCode = "204", description = "Removed")
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response removeLink(@PathParam("teamId") UUID teamId, @PathParam("linkId") UUID linkId) {
        links.remove(auth.person(), teamId, linkId);
        return Response.noContent().build();
    }

    @POST
    @Path("/sync")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "syncSu",
            summary = "Run the sync now instead of waiting for the daily run (SUH-03, admin, coach)")
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
