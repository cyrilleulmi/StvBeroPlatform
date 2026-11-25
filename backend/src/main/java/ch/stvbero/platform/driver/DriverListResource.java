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
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/** Driver list of a match day (DRV). Every change answers with the whole list. */
@Path("/api/events/{eventId}/driver-list")
@Tag(name = "DriverList")
public class DriverListResource {

    @Inject
    AuthContext auth;

    @Inject
    DriverListService drivers;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getDriverList", summary = "Driver list of a match day with cars, passengers, route and who has no seat (DRV-01 to DRV-10)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverListDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverListDto get(@PathParam("eventId") UUID eventId) {
        return drivers.get(auth.person(), eventId);
    }

    @POST
    @Path("/cars")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "addCar", summary = "Enter a car: oneself, or someone else as coach or admin (DRV-02)")
    @APIResponse(responseCode = "200", description = "The driver list after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverListDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "422", description = "Domain rule violated",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverListDto addCar(@PathParam("eventId") UUID eventId, @Valid @NotNull CarInput input) {
        drivers.addCar(auth.person(), eventId, input);
        return drivers.get(auth.person(), eventId);
    }

    @PUT
    @Path("/cars/{carId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "changeCarSeats", summary = "Change the number of seats of a car (DRV-02, DRV-03)")
    @APIResponse(responseCode = "200", description = "The driver list after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverListDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "422", description = "Domain rule violated",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverListDto changeSeats(@PathParam("eventId") UUID eventId, @PathParam("carId") UUID carId, @Valid @NotNull SeatsInput input) {
        drivers.changeSeats(auth.person(), eventId, carId, input);
        return drivers.get(auth.person(), eventId);
    }

    @DELETE
    @Path("/cars/{carId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "removeCar", summary = "Remove a car; its passengers lose their seat (DRV-02, DRV-11)")
    @APIResponse(responseCode = "200", description = "The driver list after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverListDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverListDto removeCar(@PathParam("eventId") UUID eventId, @PathParam("carId") UUID carId) {
        drivers.removeCar(auth.person(), eventId, carId);
        return drivers.get(auth.person(), eventId);
    }

    @PUT
    @Path("/cars/{carId}/passengers/{personId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "addPassenger", summary = "Put a person into a car, or move her from another car (DRV-03)")
    @APIResponse(responseCode = "200", description = "The driver list after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverListDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "422", description = "Domain rule violated",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverListDto addPassenger(@PathParam("eventId") UUID eventId, @PathParam("carId") UUID carId, @PathParam("personId") UUID personId) {
        drivers.addPassenger(auth.person(), eventId, carId, personId);
        return drivers.get(auth.person(), eventId);
    }

    @DELETE
    @Path("/cars/{carId}/passengers/{personId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "removePassenger", summary = "Take a person out of a car (DRV-03)")
    @APIResponse(responseCode = "200", description = "The driver list after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverListDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverListDto removePassenger(@PathParam("eventId") UUID eventId, @PathParam("carId") UUID carId, @PathParam("personId") UUID personId) {
        drivers.removePassenger(auth.person(), eventId, carId, personId);
        return drivers.get(auth.person(), eventId);
    }

    @PUT
    @Path("/route")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "setRouteDistance", summary = "Enter the kilometres of the route by hand (DRV-07, coach)")
    @APIResponse(responseCode = "200", description = "The driver list after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverListDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverListDto setDistance(@PathParam("eventId") UUID eventId, @Valid @NotNull RouteInput input) {
        drivers.setDistance(auth.person(), eventId, input);
        return drivers.get(auth.person(), eventId);
    }

    @PUT
    @Path("/route/duration")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "setRouteDuration", summary = "Overwrite the driving time by hand (DRV-21, coach)")
    @APIResponse(responseCode = "200", description = "The driver list after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverListDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverListDto setDuration(@PathParam("eventId") UUID eventId, @Valid @NotNull DurationInput input) {
        drivers.setDuration(auth.person(), eventId, input.minutes());
        return drivers.get(auth.person(), eventId);
    }

    @DELETE
    @Path("/route/duration")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "clearRouteDuration", summary = "Use the calculated driving time again (DRV-21, coach)")
    @APIResponse(responseCode = "200", description = "The driver list after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverListDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverListDto clearDuration(@PathParam("eventId") UUID eventId) {
        drivers.setDuration(auth.person(), eventId, null);
        return drivers.get(auth.person(), eventId);
    }

    @POST
    @Path("/route/recalculate")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "recalculateRoute", summary = "Calculate the route again with the routing service (DRV-07, coach)")
    @APIResponse(responseCode = "200", description = "The driver list after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverListDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverListDto recalculate(@PathParam("eventId") UUID eventId) {
        drivers.recalculate(auth.person(), eventId);
        return drivers.get(auth.person(), eventId);
    }

    @PUT
    @Path("/individual/{personId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "setIndividualTravel",
            summary = "Mark a person as travelling on her own: oneself, or someone else as coach or admin (DRV-20)")
    @APIResponse(responseCode = "200", description = "The driver list after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverListDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "The person drives a car, or the list cannot be changed",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "422", description = "Not a member of a team of the match day",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverListDto setIndividual(@PathParam("eventId") UUID eventId, @PathParam("personId") UUID personId) {
        drivers.setIndividual(auth.person(), eventId, personId, true);
        return drivers.get(auth.person(), eventId);
    }

    @DELETE
    @Path("/individual/{personId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "removeIndividualTravel", summary = "The person no longer travels on her own (DRV-20)")
    @APIResponse(responseCode = "200", description = "The driver list after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverListDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "The list cannot be changed",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "422", description = "Not a member of a team of the match day",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverListDto removeIndividual(@PathParam("eventId") UUID eventId, @PathParam("personId") UUID personId) {
        drivers.setIndividual(auth.person(), eventId, personId, false);
        return drivers.get(auth.person(), eventId);
    }

    @POST
    @Path("/proposal")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "proposeDriverList",
            summary = "Propose a driver list: who drives and who rides with whom; changes nothing (DRV-19, coach)")
    @APIResponse(responseCode = "200", description = "The proposal",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverProposalDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "The list cannot be changed",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverProposalDto propose(@PathParam("eventId") UUID eventId) {
        return drivers.propose(auth.person(), eventId);
    }

    @POST
    @Path("/proposal/apply")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "applyDriverProposal", summary = "Enter a proposed driver list (DRV-19, coach)")
    @APIResponse(responseCode = "200", description = "The driver list after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DriverListDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "422", description = "A rule of the driver list is violated",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DriverListDto applyProposal(@PathParam("eventId") UUID eventId, @Valid @NotNull DriverProposalDto.Input input) {
        drivers.applyProposal(auth.person(), eventId, input);
        return drivers.get(auth.person(), eventId);
    }
}
