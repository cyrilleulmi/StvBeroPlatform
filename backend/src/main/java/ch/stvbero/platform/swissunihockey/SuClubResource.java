package ch.stvbero.platform.swissunihockey;

import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.error.Problem;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/** The club at swiss unihockey: its teams for the link dialog (SUH-02) and the state of the connection (SUH-13). */
@Path("/api/swiss-unihockey")
@Tag(name = "SwissUnihockey")
public class SuClubResource {

    @Inject
    AuthContext auth;

    @Inject
    SuLinks links;

    @GET
    @Path("/teams")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listSuClubTeams",
            summary = "Teams of the club at swiss unihockey in the current season, with the team they are linked with (SUH-02, admin, coach)")
    @APIResponse(responseCode = "200", description = "In the order of swiss unihockey",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(type = SchemaType.ARRAY, implementation = SuDtos.ClubTeam.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "No current season",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "502", description = "swiss unihockey unavailable or unexpected response (SUH-09)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public List<SuDtos.ClubTeam> teams() {
        return links.clubTeams(auth.person());
    }

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getSuStatus",
            summary = "State of the connection: linked teams, last successful sync, links whose last sync failed (SUH-13, admin, coach)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SuDtos.Status.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "No current season",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public SuDtos.Status status() {
        return links.status(auth.person());
    }
}
