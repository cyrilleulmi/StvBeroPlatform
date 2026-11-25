package ch.stvbero.platform.core.team;

import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.error.Problem;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/** Team memberships of the whole club in one season (PTF-16, PTF-17). */
@Path("/api/team-memberships")
@Tag(name = "Teams")
public class TeamMembershipResource {

    @Inject
    AuthContext auth;

    @Inject
    TeamService teams;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listTeamMemberships",
            summary = "Everyone of the club with their teams in a season and in the season before (PTF-16, admin, coach)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = MembershipOverviewDto.class)))
    @APIResponse(responseCode = "401", description = "Not logged in",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public MembershipOverviewDto list(@QueryParam("season") UUID season) {
        return teams.memberships(auth.person(), season);
    }

    @POST
    @Path("/batch")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "changeTeamMemberships",
            summary = "Put several people into teams and take several out at once (PTF-17, admin, coach)")
    @APIResponse(responseCode = "200", description = "Changed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = MembershipOverviewDto.BatchResult.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public MembershipOverviewDto.BatchResult batch(@QueryParam("season") UUID season,
                                                   @Valid @NotNull MembershipBatchInput input) {
        return teams.changeMemberships(auth.person(), season, input);
    }
}
