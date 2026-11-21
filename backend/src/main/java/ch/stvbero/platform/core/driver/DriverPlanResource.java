package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.error.Problem;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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

import java.util.UUID;

/** Driver plan of a team for a whole season (DRV-14, DRV-17). */
@Path("/api/teams/{teamId}/driver-plan")
@Tag(name = "DriverList")
public class DriverPlanResource {

    @Inject
    AuthContext auth;

    @Inject
    DriverPlanService plan;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getDriverPlan",
            summary = "Driver plan of a team for a season: all match days with their cars, and the trips per member (DRV-14, DRV-17)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverPlanDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverPlanDto get(@PathParam("teamId") UUID teamId, @QueryParam("season") UUID season) {
        return plan.get(auth.person(), teamId, season);
    }

    @POST
    @Path("/cars")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "planDriver",
            summary = "Enter one driver for several match days at once (DRV-14): members themselves, coaches and "
                    + "admins also others. Match days on which she already drives stay as they are.")
    @APIResponse(responseCode = "200", description = "Entered",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverPlanDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "A match day is not ready, cancelled or over (DRV-01, DRV-10)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "422", description = "Domain rule violated",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverPlanDto plan(@PathParam("teamId") UUID teamId, @QueryParam("season") UUID season,
                              @Valid @NotNull DriverPlanDto.Input input) {
        return plan.plan(auth.person(), teamId, season, input);
    }
}
