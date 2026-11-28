package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.error.Problem;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Events (TER). */
@Path("/api/events")
@Tag(name = "Events")
public class EventResource {

    @Inject
    AuthContext auth;

    @Inject
    EventService events;

    @Inject
    DepartureRefresh departure;

    @Inject
    EventCalendarService calendar;

    @Inject
    EventPins pins;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listEvents", summary = "Events of the person's own teams with the person's own response (TER-13)")
    @APIResponse(responseCode = "200", description = "Chronologisch sortiert (vergangene absteigend)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = EventSummaryDto.class)))
    @APIResponse(responseCode = "401", description = "Not logged in",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public List<EventSummaryDto> list(@QueryParam("from") LocalDate from, @QueryParam("to") LocalDate to,
                                     @QueryParam("team") List<UUID> teams, @QueryParam("type") EventType type,
                                     @QueryParam("view") String view) {
        return events.list(auth.person(), from, to, teams, type, view);
    }

    @GET
    @Path("/calendar")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getCalendarView",
            summary = "Events of the chosen teams in a period of at most six weeks, with the own absences (KAL-01 to KAL-04)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CalendarViewDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "401", description = "Not logged in",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07): all teams or a team of others as a player",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public CalendarViewDto calendar(@QueryParam("from") LocalDate from, @QueryParam("to") LocalDate to,
                                    @QueryParam("team") List<UUID> teams, @QueryParam("all") boolean all) {
        return calendar.view(auth.person(), from, to, teams, all);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "createEvent", summary = "Create a single event, match day or other event (TER-11, AUF-16, coach)")
    @APIResponse(responseCode = "201", description = "Created",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = EventDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response create(@Valid @NotNull EventInput input) {
        EventDto created = events.create(auth.person(), input);
        return Response.status(201).entity(departure.apply(auth.person(), created)).build();
    }

    @GET
    @Path("/{eventId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getEvent", summary = "Event with the person's own response; for a match day with matches, meeting point and own call-up status")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = EventDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public EventDto get(@PathParam("eventId") UUID eventId) {
        return departure.apply(auth.person(), events.get(auth.person(), eventId));
    }

    @PUT
    @Path("/{eventId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateEvent", summary = "Update or reschedule an event (TER-06, TER-12, coach)")
    @APIResponse(responseCode = "200", description = "Updated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = EventDto.class)))
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
    public EventDto update(@PathParam("eventId") UUID eventId, @Valid @NotNull EventInput input) {
        return departure.apply(auth.person(), events.update(auth.person(), eventId, input));
    }

    @POST
    @Path("/{eventId}/cancel")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "cancelEvent", summary = "Cancel an event (TER-06, TER-12, coach). After that, no more responses are possible.")
    @APIResponse(responseCode = "200", description = "Abgesagt",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = EventDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public EventDto cancel(@PathParam("eventId") UUID eventId, @Valid @NotNull CancellationDto input) {
        return events.cancel(auth.person(), eventId, input.reason());
    }

    @POST
    @Path("/{eventId}/pin")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "pinEvent",
            summary = "Pin an event to the start page of the members of its teams until it is over (TER-26, TER-27). Pinning twice changes nothing.")
    @APIResponse(responseCode = "200", description = "Angeheftet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = EventDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state: cancelled, over, archived season",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public EventDto pin(@PathParam("eventId") UUID eventId) {
        return departure.apply(auth.person(), pins.pin(auth.person(), eventId));
    }

    @POST
    @Path("/{eventId}/unpin")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "unpinEvent", summary = "Take the pin of an event away (TER-26, TER-27)")
    @APIResponse(responseCode = "200", description = "Nicht mehr angeheftet",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = EventDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state: cancelled, over, archived season",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public EventDto unpin(@PathParam("eventId") UUID eventId) {
        return departure.apply(auth.person(), pins.unpin(auth.person(), eventId));
    }
}
