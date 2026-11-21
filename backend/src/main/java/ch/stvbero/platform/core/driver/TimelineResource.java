package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.error.Problem;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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

/** Timeline of a match day (TML). */
@Path("/api/events/{eventId}/timeline")
@Tag(name = "MatchDays")
public class TimelineResource {

    @Inject
    AuthContext auth;

    @Inject
    TimelineService timeline;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getTimeline",
            summary = "Timeline of a match day: departure, arrival, matches, cool down, approximate return (TML-01)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TimelineDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Not a match day",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public TimelineDto get(@PathParam("eventId") UUID eventId) {
        return timeline.get(auth.person(), eventId);
    }

    @POST
    @Path("/apply-departure")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "applySuggestedDeparture",
            summary = "Take over the suggested departure as meeting time of the match day (TML-04, coach)")
    @APIResponse(responseCode = "200", description = "Taken over",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TimelineDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "No suggestion, not a match day, cancelled or over",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public TimelineDto applyDeparture(@PathParam("eventId") UUID eventId) {
        return timeline.applySuggestion(auth.person(), eventId);
    }
}
