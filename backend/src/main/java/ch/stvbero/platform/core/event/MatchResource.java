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

/** Spiele und Aufgebot eines Spieltags (AUF). */
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
    @Operation(operationId = "addMatch", summary = "Spiel zu einem Spieltag hinzufügen (AUF-14, Coach)")
    @APIResponse(responseCode = "201", description = "Angelegt",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = MatchDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response add(@PathParam("eventId") UUID eventId, @Valid @NotNull MatchInput input) {
        return Response.status(201).entity(matches.add(auth.person(), eventId, input)).build();
    }

    @PUT
    @Path("/matches/{matchId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateMatch", summary = "Spiel ändern, z.B. Resultat erfassen (AUF-15, Coach). Importierte Felder sind schreibgeschützt (409).")
    @APIResponse(responseCode = "200", description = "Geändert",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = MatchDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public MatchDto update(@PathParam("eventId") UUID eventId, @PathParam("matchId") UUID matchId,
                            @Valid @NotNull MatchInput input) {
        return matches.update(auth.person(), eventId, matchId, input);
    }

    @DELETE
    @Path("/matches/{matchId}")
    @Operation(operationId = "deleteMatch", summary = "Von Hand erfasstes Spiel löschen (Coach)")
    @APIResponse(responseCode = "204", description = "Gelöscht")
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response delete(@PathParam("eventId") UUID eventId, @PathParam("matchId") UUID matchId) {
        matches.delete(auth.person(), eventId, matchId);
        return Response.noContent().build();
    }

    @GET
    @Path("/call-up")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getCallUp", summary = "Aufgebot mit allen Spielerinnen des Teams, gruppiert, mit Antwortstatus (AUF-21, AUF-22, Coach)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CallUpDto.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public CallUpDto callUp(@PathParam("eventId") UUID eventId) {
        return callUps.get(auth.person(), eventId);
    }

    @PUT
    @Path("/call-up")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "setCallUp", summary = "Auswahl speichern (Coach). Ist das Aufgebot veröffentlicht, werden Betroffene benachrichtigt (AUF-27).")
    @APIResponse(responseCode = "200", description = "Gespeichert",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CallUpDto.class)))
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
    public CallUpDto setCallUp(@PathParam("eventId") UUID eventId, @Valid @NotNull CallUpInput input) {
        return callUps.set(auth.person(), eventId, input);
    }

    @POST
    @Path("/call-up/publish")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "publishCallUp", summary = "Aufgebot veröffentlichen und alle Spielerinnen des Teams benachrichtigen (AUF-23, AUF-24, Coach)")
    @APIResponse(responseCode = "200", description = "Veröffentlicht",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CallUpDto.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public CallUpDto publish(@PathParam("eventId") UUID eventId) {
        return callUps.publish(auth.person(), eventId);
    }
}
