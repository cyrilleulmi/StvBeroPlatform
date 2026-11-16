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

/** Responses and overview of an event (ANM). */
@Path("/api/events/{eventId}")
@Tag(name = "Responses")
public class ResponseResource {

    @Inject
    AuthContext auth;

    @Inject
    ResponseService responses;

    @GET
    @Path("/overview")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getOverview", summary = "Antworten nach Team und Gruppe, Coach-Block, Zähler (ANM-20 bis ANM-32)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = OverviewDto.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public OverviewDto overview(@PathParam("eventId") UUID eventId) {
        return responses.overview(auth.person(), eventId);
    }

    @PUT
    @Path("/responses/me")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "setOwnResponse", summary = "Eigene Antwort setzen oder ändern (ANM-01 bis ANM-10)")
    @APIResponse(responseCode = "200", description = "Gespeichert",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ResponseDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "422", description = "Fachliche Regel verletzt",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public ResponseDto setOwn(@PathParam("eventId") UUID eventId, @Valid @NotNull ResponseInput input) {
        return responses.set(auth.person(), eventId, null, input);
    }

    @PUT
    @Path("/responses/{personId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "setResponseOnBehalf", summary = "Antwort stellvertretend setzen (ANM-11, ANM-12, RR-06)")
    @APIResponse(responseCode = "200", description = "Gespeichert",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ResponseDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "422", description = "Fachliche Regel verletzt",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public ResponseDto setOnBehalf(@PathParam("eventId") UUID eventId, @PathParam("personId") UUID personId,
                                            @Valid @NotNull ResponseInput input) {
        return responses.set(auth.person(), eventId, personId, input);
    }
}
