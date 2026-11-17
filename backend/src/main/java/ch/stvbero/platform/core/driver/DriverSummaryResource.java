package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.error.Problem;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
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

/** Trips and kilometres per person in a season (DRV-09). */
@Path("/api/driver-summary")
@Tag(name = "Driver list")
public class DriverSummaryResource {

    @Inject
    AuthContext auth;

    @Inject
    DriverListService drivers;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getDriverSummary", summary = "Trips and credited kilometres per person in a season (DRV-09)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverSummaryDto.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverSummaryDto get(@QueryParam("season") UUID season) {
        return drivers.summary(auth.person(), season);
    }
}
