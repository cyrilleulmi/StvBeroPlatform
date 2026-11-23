package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.error.Problem;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/** Special permissions per series, for all its events (RR-10 to RR-14). */
@Path("/api/series/{seriesId}/grants")
@Tag(name = "Events")
public class SeriesGrantResource {

    @Inject
    AuthContext auth;

    @Inject
    EventGrants grants;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getSeriesGrants", summary = "Who manages all events of the series like a coach (RR-10)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = EventGrantsDto.class)))
    @APIResponse(responseCode = "401", description = "Not logged in",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Not a member of a team of the series",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Unknown series",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public EventGrantsDto ofSeries(@PathParam("seriesId") UUID seriesId) {
        return grants.ofSeries(auth.person(), seriesId);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "changeSeriesGrants",
            summary = "Set the special permissions of a series; they apply to all its events (RR-10, RR-11)")
    @APIResponse(responseCode = "200", description = "Changed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = EventGrantsDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "401", description = "Not logged in",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Not a coach or admin",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Unknown series",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "422", description = "Person is not a member of a team of the series (RR-12)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public EventGrantsDto changeSeries(@PathParam("seriesId") UUID seriesId, @Valid @NotNull EventGrantsInput input) {
        return grants.changeSeries(auth.person(), seriesId, input);
    }
}
