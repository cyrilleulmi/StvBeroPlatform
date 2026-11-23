package ch.stvbero.platform.core.event;

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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/** Series (TER-05, TER-07, TER-08). */
@Path("/api/series")
@Tag(name = "Events")
public class SeriesResource {

    @Inject
    AuthContext auth;

    @Inject
    SeriesService series;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "createSeries", summary = "Create a series; creates the events in the validity period (TER-05, coach)")
    @APIResponse(responseCode = "201", description = "Created",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SeriesDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response create(@Valid @NotNull SeriesInput input) {
        return Response.status(201).entity(series.create(auth.person(), input)).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listTeamSeries",
            summary = "The series of a team in a season with their upcoming events (TER-18)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.ARRAY,
                            implementation = SeriesPlanDto.Overview.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public java.util.List<SeriesPlanDto.Overview> ofTeam(@QueryParam("team") UUID team, @QueryParam("season") UUID season) {
        if (team == null) {
            throw ch.stvbero.platform.core.error.DomainError.invalidField("team", "Bitte ein Team angeben.");
        }
        return series.ofTeam(auth.person(), team, season);
    }

    @POST
    @Path("/{seriesId}/preview")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "previewSeriesChange",
            summary = "What a change of the series would do to its upcoming events; changes nothing (TER-19, coach)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SeriesPlanDto.Preview.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public SeriesPlanDto.Preview preview(@PathParam("seriesId") UUID seriesId, @Valid @NotNull SeriesInput input) {
        return series.preview(auth.person(), seriesId, input);
    }

    @POST
    @Path("/{seriesId}/end/preview")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "previewSeriesEnd",
            summary = "Which upcoming events ending the series would remove or cancel; changes nothing (TER-20, coach)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SeriesPlanDto.Preview.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public SeriesPlanDto.Preview previewEnd(@PathParam("seriesId") UUID seriesId,
                                            @Valid @NotNull SeriesPlanDto.EndInput input) {
        return series.previewEnd(auth.person(), seriesId, input.from());
    }

    @POST
    @Path("/{seriesId}/end")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "endSeries",
            summary = "End a series from a day on: its upcoming events from then on are removed, or cancelled if "
                    + "someone has responded (TER-20, coach)")
    @APIResponse(responseCode = "200", description = "The series after the change",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SeriesDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Archived season (SAI-02)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public SeriesDto end(@PathParam("seriesId") UUID seriesId, @Valid @NotNull SeriesPlanDto.EndInput input) {
        return series.end(auth.person(), seriesId, input.from());
    }

    @GET
    @Path("/{seriesId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getSeries", summary = "Series with its series exceptions")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SeriesDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public SeriesDto get(@PathParam("seriesId") UUID seriesId) {
        return series.get(auth.person(), seriesId);
    }

    @PUT
    @Path("/{seriesId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateSeries", summary = "Update a series. Applies to future events; given responses are kept (TER-08, coach).")
    @APIResponse(responseCode = "200", description = "Updated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SeriesDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public SeriesDto update(@PathParam("seriesId") UUID seriesId, @Valid @NotNull SeriesInput input) {
        return series.update(auth.person(), seriesId, input);
    }

    @POST
    @Path("/{seriesId}/exceptions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "createSeriesException", summary = "Exclude a period from the series, e.g. holidays (TER-07, coach)")
    @APIResponse(responseCode = "201", description = "Created",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SeriesDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response createException(@PathParam("seriesId") UUID seriesId, @Valid @NotNull SeriesExceptionDto input) {
        return Response.status(201).entity(series.createException(auth.person(), seriesId, input)).build();
    }
}
