package ch.stvbero.platform.core.team;

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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

@Path("/api/teams")
@Tag(name = "Teams")
public class TeamResource {

    @Inject
    AuthContext auth;

    @Inject
    TeamService teams;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listTeams", summary = "Teams of a season. Admins and coaches get all teams, players only their own.")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = TeamDto.class)))
    @APIResponse(responseCode = "401", description = "Not logged in",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public List<TeamDto> list(@QueryParam("season") UUID season) {
        return teams.list(auth.person(), season);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "createTeam", summary = "Create a team (admin, coach)")
    @APIResponse(responseCode = "201", description = "Created",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TeamDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response create(@QueryParam("season") UUID season, @Valid @NotNull TeamInput input) {
        return Response.status(201).entity(teams.create(auth.person(), season, input)).build();
    }

    @GET
    @Path("/{teamId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getTeam", summary = "Team with coaches and players by group")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TeamDetailDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public TeamDetailDto get(@PathParam("teamId") UUID teamId, @QueryParam("season") UUID season) {
        return teams.get(auth.person(), teamId, season);
    }

    @PUT
    @Path("/{teamId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "renameTeam", summary = "Rename a team (admin, coach)")
    @APIResponse(responseCode = "200", description = "Updated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TeamDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public TeamDto update(@PathParam("teamId") UUID teamId, @QueryParam("season") UUID season,
                           @Valid @NotNull TeamInput input) {
        return teams.update(auth.person(), teamId, season, input);
    }

    @PUT
    @Path("/{teamId}/warning-minimums")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "setWarningMinimums", summary = "Change the minimums for the warnings of the team (F-16, admin, coach)")
    @APIResponse(responseCode = "200", description = "Updated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = WarningMinimums.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public WarningMinimums setWarningMinimums(@PathParam("teamId") UUID teamId, @Valid @NotNull WarningMinimums input) {
        return teams.setWarningMinimums(auth.person(), teamId, input);
    }

    @PUT
    @Path("/{teamId}/match-day-settings")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "setMatchDaySettings", summary = "Change match duration, arrival lead and usual meeting point of the team (TML-02, TML-03, AUF-32, admin, coach)")
    @APIResponse(responseCode = "200", description = "Updated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = MatchDaySettings.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public MatchDaySettings setMatchDaySettings(@PathParam("teamId") UUID teamId, @Valid @NotNull MatchDaySettings input) {
        return teams.setMatchDaySettings(auth.person(), teamId, input);
    }

    @POST
    @Path("/{teamId}/members")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "addMember", summary = "Add a person with a team role to the team (PTF-03, PTF-04, admin, coach)")
    @APIResponse(responseCode = "201", description = "Aufgenommen",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TeamMembershipDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response addMember(@PathParam("teamId") UUID teamId, @QueryParam("season") UUID season,
                              @Valid @NotNull MemberInput input) {
        return Response.status(201).entity(teams.addMember(auth.person(), teamId, season, input)).build();
    }

    @PUT
    @Path("/{teamId}/members/{personId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "changeTeamRole", summary = "Change the team role (admin, coach)")
    @APIResponse(responseCode = "200", description = "Updated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TeamMembershipDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public TeamMembershipDto changeTeamRole(@PathParam("teamId") UUID teamId, @PathParam("personId") UUID personId,
                                                 @QueryParam("season") UUID season,
                                                 @Valid @NotNull TeamRoleInput input) {
        return teams.changeTeamRole(auth.person(), teamId, personId, season, input.teamRole());
    }

    @DELETE
    @Path("/{teamId}/members/{personId}")
    @Operation(operationId = "removeMember", summary = "Take a person out of the team (admin, coach, E-103). Existing responses are kept.")
    @APIResponse(responseCode = "204", description = "Removed")
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public void removeMember(@PathParam("teamId") UUID teamId, @PathParam("personId") UUID personId,
                          @QueryParam("season") UUID season) {
        teams.removeMember(auth.person(), teamId, personId, season);
    }
}
