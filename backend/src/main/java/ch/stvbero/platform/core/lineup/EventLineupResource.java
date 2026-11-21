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

/** Special line-ups of the team of a match day, with the responses of the players (LIN-16). */
@Path("/api/events/{eventId}/special-lineups")
@Tag(name = "Lines")
public class EventLineupResource {

    @Inject
    AuthContext auth;

    @Inject
    SpecialLineupService lineups;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getEventLineups",
            summary = "Special line-ups of the team of a match day with the responses of the players (LIN-16)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SpecialLineupDto.OfEvent.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Not a match day",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public SpecialLineupDto.OfEvent ofEvent(@PathParam("eventId") UUID eventId) {
        return lineups.ofEvent(auth.person(), eventId);
    }
}
