package ch.stvbero.platform.swissunihockey.league;

import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.error.DomainError;
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

/** League data of a team from swiss unihockey (F-17). */
@Path("/api/teams/{teamId}/league")
@Tag(name = "SwissUnihockey")
public class LeagueResource {

    @Inject
    AuthContext auth;

    @Inject
    LeagueService league;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getLeague",
            summary = "League table, upcoming matches and latest results of a team, from the cache (F-17)")
    @APIResponse(responseCode = "200", description = "League data; `linked: false` if the team is not linked",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = LeagueDto.class)))
    @APIResponse(responseCode = "403", description = "Not a member of the team, not a coach or admin",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public LeagueDto get(@PathParam("teamId") UUID teamId) {
        return league.get(auth.person(), teamId);
    }

    @POST
    @Path("/refresh")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "refreshLeague",
            summary = "Reload the league data from swiss unihockey now instead of waiting for the daily sync (F-17, admin)")
    @APIResponse(responseCode = "200", description = "Reloaded",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = LeagueDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Not linked or not in the current season",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "502",
            description = "swiss unihockey unavailable or unexpected response; the last loaded data stays (SUH-09)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public LeagueDto refresh(@PathParam("teamId") UUID teamId) {
        // The failed attempt is stored (committed) before the 502 is returned.
        switch (league.refresh(auth.person(), teamId)) {
            case FAILED_KEPT_DATA -> throw new DomainError(502, "swiss unihockey nicht erreichbar",
                    LeagueService.UNAVAILABLE, "SUH-09");
            case FAILED_NO_DATA -> throw new DomainError(502, "swiss unihockey nicht erreichbar",
                    LeagueService.UNAVAILABLE_NEVER_LOADED, "SUH-09");
            case LOADED -> {
            }
        }
        return league.get(auth.person(), teamId);
    }
}
