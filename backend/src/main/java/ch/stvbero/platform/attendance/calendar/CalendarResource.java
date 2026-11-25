package ch.stvbero.platform.attendance.calendar;

import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.auth.Public;
import ch.stvbero.platform.core.error.DomainError;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Calendar subscription (F-18, CAL). */
@Path("/api")
@Tag(name = "Calendar")
public class CalendarResource {

    static final String TEXT_CALENDAR = "text/calendar";

    @Inject
    AuthContext auth;

    @Inject
    CalendarService calendar;

    @GET
    @Path("/me/calendar")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getMyCalendar",
            summary = "Personal address of the calendar feed; created on first call (CAL-01)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = CalendarSubscriptionDto.class)))
    @APIResponse(responseCode = "401", description = "Not logged in",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public CalendarSubscriptionDto mine() {
        return calendar.mine(auth.person().personId());
    }

    @POST
    @Path("/me/calendar/renew")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "renewMyCalendar",
            summary = "New address for the calendar feed; the old one answers 404 at once (CAL-07)")
    @APIResponse(responseCode = "200", description = "New address",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = CalendarSubscriptionDto.class)))
    @APIResponse(responseCode = "401", description = "Not logged in",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public CalendarSubscriptionDto renew() {
        return calendar.renew(auth.person().personId());
    }

    @PUT
    @Path("/me/calendar/filter")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "changeMyCalendarFilter",
            summary = "Which events the calendar feed delivers; the address stays the same (CAL-10 to CAL-14)")
    @APIResponse(responseCode = "200", description = "Changed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = CalendarSubscriptionDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "401", description = "Not logged in",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "422", description = "No event type chosen (CAL-10)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public CalendarSubscriptionDto changeFilter(@Valid @NotNull CalendarFilterInput input) {
        return calendar.changeFilter(auth.person().personId(), input);
    }

    @GET
    @Path("/calendar/{token}.ics")
    @Public
    @Produces(TEXT_CALENDAR)
    @Operation(operationId = "getCalendarFeed",
            summary = "iCalendar feed of a person, reachable with the token alone (CAL-02 to CAL-08)")
    @APIResponse(responseCode = "200", description = "iCalendar (RFC 5545)",
            content = @Content(mediaType = TEXT_CALENDAR, schema = @Schema(type = SchemaType.STRING)))
    @APIResponse(responseCode = "404", description = "Unknown or renewed address",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response feed(@PathParam("token") String token) {
        String body = calendar.feed(token).orElseThrow(
                () -> DomainError.notFound("Diese Kalenderadresse gibt es nicht (mehr)."));
        return Response.ok(body, TEXT_CALENDAR + "; charset=utf-8")
                .header("Content-Disposition", "inline; filename=\"stv-bero.ics\"")
                .header("Cache-Control", "private, no-cache")
                .header("X-Robots-Tag", "noindex")
                .build();
    }
}
