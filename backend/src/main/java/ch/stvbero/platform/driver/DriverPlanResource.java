package ch.stvbero.platform.driver;

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

    @Inject
    DriverPlanSettings settings;

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

    @POST
    @Path("/swaps")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "startDriverSwap",
            summary = "Give the trip of a match day to another member, as a takeover or against her trip on another "
                    + "match day: ask first or enter directly (DRV-25)")
    @APIResponse(responseCode = "200", description = "The plan after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverPlanDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state (DRV-25)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverPlanDto swap(@PathParam("teamId") UUID teamId, @QueryParam("season") UUID season,
                              @Valid @NotNull DriverPlanDto.SwapInput input) {
        return plan.swap(auth.person(), teamId, season, input);
    }

    @POST
    @Path("/swaps/{swapId}/accept")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "acceptDriverSwap", summary = "Accept a swap request: it is carried out (DRV-25)")
    @APIResponse(responseCode = "200", description = "The plan after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverPlanDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state (DRV-25)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverPlanDto accept(@PathParam("teamId") UUID teamId, @PathParam("swapId") UUID swapId,
                                @QueryParam("season") UUID season) {
        return plan.answerSwap(auth.person(), teamId, season, swapId, "accept");
    }

    @POST
    @Path("/swaps/{swapId}/decline")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "declineDriverSwap", summary = "Decline a swap request (DRV-25)")
    @APIResponse(responseCode = "200", description = "The plan after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverPlanDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state (DRV-25)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverPlanDto decline(@PathParam("teamId") UUID teamId, @PathParam("swapId") UUID swapId,
                                 @QueryParam("season") UUID season) {
        return plan.answerSwap(auth.person(), teamId, season, swapId, "decline");
    }

    @POST
    @Path("/swaps/{swapId}/withdraw")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "withdrawDriverSwap", summary = "Withdraw an own swap request (DRV-25)")
    @APIResponse(responseCode = "200", description = "The plan after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverPlanDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state (DRV-25)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverPlanDto withdraw(@PathParam("teamId") UUID teamId, @PathParam("swapId") UUID swapId,
                                  @QueryParam("season") UUID season) {
        return plan.answerSwap(auth.person(), teamId, season, swapId, "withdraw");
    }

    @POST
    @Path("/trips")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "addExtraTrip", summary = "Add a trip without an event in the app (DRV-28)")
    @APIResponse(responseCode = "200", description = "The plan after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverPlanDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state (DRV-25)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverPlanDto addTrip(@PathParam("teamId") UUID teamId, @QueryParam("season") UUID season,
                                 @Valid @NotNull DriverPlanDto.TripInput input) {
        return plan.addTrip(auth.person(), teamId, season, input);
    }

    @DELETE
    @Path("/trips/{tripId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "removeExtraTrip", summary = "Remove another trip (DRV-28)")
    @APIResponse(responseCode = "200", description = "The plan after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverPlanDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state (DRV-25)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverPlanDto removeTrip(@PathParam("teamId") UUID teamId, @PathParam("tripId") UUID tripId,
                                    @QueryParam("season") UUID season) {
        return plan.removeTrip(auth.person(), teamId, season, tripId);
    }

    @PUT
    @Path("/expected")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "setDriverPlanExpected",
            summary = "Set how many people are expected to travel to a match day of the team; without a number: "
                    + "all players and coaches (DRV-29, coach)")
    @APIResponse(responseCode = "200", description = "The plan after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverPlanDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverPlanDto setExpected(@PathParam("teamId") UUID teamId, @QueryParam("season") UUID season,
                                     @Valid @NotNull DriverPlanDto.ExpectedInput input) {
        return settings.setExpected(auth.person(), teamId, season, input.expected());
    }

    @PUT
    @Path("/match-days/{eventId}/expected")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "setMatchDayExpected",
            summary = "Set how many people are expected to travel to one match day; without a number: the number "
                    + "of the team (DRV-29, coach)")
    @APIResponse(responseCode = "200", description = "The plan after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverPlanDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverPlanDto setMatchDayExpected(@PathParam("teamId") UUID teamId, @PathParam("eventId") UUID eventId,
                                             @QueryParam("season") UUID season,
                                             @Valid @NotNull DriverPlanDto.ExpectedInput input) {
        return settings.setMatchDayExpected(auth.person(), teamId, season, eventId, input.expected());
    }

    @PUT
    @Path("/match-days/{eventId}/ignored")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "setMatchDayIgnored",
            summary = "Leave a match day out of the driver list of the season or take it in again: its trips do not "
                    + "count and «Fair verteilen» skips it (DRV-35, coach)")
    @APIResponse(responseCode = "200", description = "The plan after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverPlanDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverPlanDto setMatchDayIgnored(@PathParam("teamId") UUID teamId, @PathParam("eventId") UUID eventId,
                                            @QueryParam("season") UUID season,
                                            @Valid @NotNull DriverPlanDto.IgnoredInput input) {
        return settings.setIgnored(auth.person(), teamId, season, eventId, input.ignored());
    }

    @POST
    @Path("/distribute/preview")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "previewDriverPlanDistribution",
            summary = "Propose a fair distribution of the trips that are still missing on the upcoming match days: "
                    + "drivers until there is a place for everyone who is expected. Changes nothing; entered "
                    + "drivers stay (DRV-30, coach)")
    @APIResponse(responseCode = "200", description = "The proposed trips",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverPlanDto.Distribution.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverPlanDto.Distribution previewDistribution(@PathParam("teamId") UUID teamId,
                                                          @QueryParam("season") UUID season) {
        return plan.previewDistribution(auth.person(), teamId, season);
    }

    @POST
    @Path("/distribute")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "distributeDriverPlan",
            summary = "Enter the trips of a proposed distribution as they are; they stay, a later distribution "
                    + "only adds (DRV-30, coach)")
    @APIResponse(responseCode = "200", description = "The plan after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverPlanDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "The proposal no longer fits the plan (DRV-30)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "422", description = "Domain rule violated",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverPlanDto distribute(@PathParam("teamId") UUID teamId, @QueryParam("season") UUID season,
                                    @Valid @NotNull DriverPlanDto.DistributeInput input) {
        return plan.distribute(auth.person(), teamId, season, input.trips());
    }

    @DELETE
    @Path("/cars")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "removeAllPlannedDrivers",
            summary = "Remove every driver of the upcoming match days; passengers lose their seats (DRV-33, coach)")
    @APIResponse(responseCode = "200", description = "The plan after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverPlanDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverPlanDto removeAll(@PathParam("teamId") UUID teamId, @QueryParam("season") UUID season) {
        return plan.removeAll(auth.person(), teamId, season);
    }

    @POST
    @Path("/match-days/{eventId}/next")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "nextDriver",
            summary = "The next driver in line takes a trip of a match day: one more car, or instead of a driver "
                    + "who cannot drive (DRV-31)")
    @APIResponse(responseCode = "200", description = "The plan after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverPlanDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Nobody is left who can drive, or the match day cannot be changed (DRV-31)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverPlanDto next(@PathParam("teamId") UUID teamId, @PathParam("eventId") UUID eventId,
                              @QueryParam("season") UUID season, @Valid @NotNull DriverPlanDto.NextInput input) {
        return plan.next(auth.person(), teamId, season, eventId, input.replaces());
    }
}
