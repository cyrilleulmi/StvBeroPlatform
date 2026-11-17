package ch.stvbero.platform.core.statistics;

import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.error.Problem;
import ch.stvbero.platform.core.event.EventType;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.UUID;

/** Attendance statistics (STA). Read only. */
@Path("/api/statistics")
@Tag(name = "Statistics")
@Produces(MediaType.APPLICATION_JSON)
public class StatisticsResource {

    @Inject
    AuthContext auth;

    @Inject
    StatisticsService statistics;

    @GET
    @Operation(operationId = "getTeamStatistics", summary = "Statistics of a team per player, by line, with the team average (STA-01 to STA-05, coach)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = StatisticsDto.TeamStatistics.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public StatisticsDto.TeamStatistics team(@QueryParam("team") UUID team, @QueryParam("from") LocalDate from,
                                             @QueryParam("to") LocalDate to, @QueryParam("type") EventType type) {
        return statistics.team(auth.person(), team, from, to, type);
    }

    @GET
    @Path("/persons/{personId}")
    @Operation(operationId = "getPersonStatistics", summary = "Statistics of one player with her events (STA-06, STA-08)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = StatisticsDto.PersonStatistics.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public StatisticsDto.PersonStatistics person(@PathParam("personId") UUID personId, @QueryParam("team") UUID team,
                                                 @QueryParam("from") LocalDate from, @QueryParam("to") LocalDate to,
                                                 @QueryParam("type") EventType type) {
        return statistics.person(auth.person(), personId, team, from, to, type);
    }
}
