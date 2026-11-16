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
    @Operation(operationId = "getLineup", summary = "Linien und Goalies eines Teams mit Spielerinnen, plus «Ohne Linie»")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TeamDetailDto.Lineup.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public TeamDetailDto.Lineup get(@PathParam("teamId") UUID teamId) {
        return lines.get(auth.person(), teamId);
    }

    @POST
    @Path("/groups")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "createLine", summary = "Linie anlegen (LIN-07, Coach). Goalies gibt es pro Team automatisch genau einmal.")
    @APIResponse(responseCode = "201", description = "Angelegt",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = GroupDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response create(@PathParam("teamId") UUID teamId, @Valid @NotNull LineInput input) {
        return Response.status(201).entity(lines.create(auth.person(), teamId, input)).build();
    }

    @PUT
    @Path("/groups/{groupId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateLine", summary = "Linie umbenennen oder Reihenfolge ändern (Coach)")
    @APIResponse(responseCode = "200", description = "Geändert",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = GroupDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public GroupDto update(@PathParam("teamId") UUID teamId, @PathParam("groupId") UUID groupId,
                             @Valid @NotNull LineInput input) {
        return lines.update(auth.person(), teamId, groupId, input);
    }

    @DELETE
    @Path("/groups/{groupId}")
    @Operation(operationId = "deleteLine", summary = "Linie löschen (Coach). Ihre Spielerinnen sind danach «ohne Linie». Goalies lassen sich nicht löschen (409).")
    @APIResponse(responseCode = "204", description = "Gelöscht")
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public void delete(@PathParam("teamId") UUID teamId, @PathParam("groupId") UUID groupId) {
        lines.delete(auth.person(), teamId, groupId);
    }

    @PUT
    @Path("/assignments/{personId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "setAssignment", summary = "Spielerin einer Gruppe zuweisen oder auf «ohne Linie» setzen (LIN-02, LIN-07, LIN-10)")
    @APIResponse(responseCode = "204", description = "Gesetzt")
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public void assign(@PathParam("teamId") UUID teamId, @PathParam("personId") UUID personId,
                         @Valid @NotNull AssignmentInput input) {
        lines.assign(auth.person(), teamId, personId, input.groupId());
    }
}
