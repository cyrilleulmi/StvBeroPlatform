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
    @Operation(operationId = "createSeries", summary = "Serie anlegen, erzeugt die Termine im Gültigkeitszeitraum (TER-05, Coach)")
    @APIResponse(responseCode = "201", description = "Angelegt",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SeriesDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response create(@Valid @NotNull SeriesInput input) {
        return Response.status(201).entity(series.create(auth.person(), input)).build();
    }

    @GET
    @Path("/{seriesId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getSeries", summary = "Serie mit Ausnahmen")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SeriesDto.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public SeriesDto get(@PathParam("seriesId") UUID seriesId) {
        return series.get(auth.person(), seriesId);
    }

    @PUT
    @Path("/{seriesId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateSeries", summary = "Serie ändern. Gilt für künftige Termine, gegebene Antworten bleiben (TER-08, Coach).")
    @APIResponse(responseCode = "200", description = "Geändert",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SeriesDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public SeriesDto update(@PathParam("seriesId") UUID seriesId, @Valid @NotNull SeriesInput input) {
        return series.update(auth.person(), seriesId, input);
    }

    @POST
    @Path("/{seriesId}/exceptions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "createSeriesException", summary = "Zeitraum von der Serie ausnehmen, z.B. Ferien (TER-07, Coach)")
    @APIResponse(responseCode = "201", description = "Angelegt",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SeriesDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response createException(@PathParam("seriesId") UUID seriesId, @Valid @NotNull SeriesExceptionDto input) {
        return Response.status(201).entity(series.createException(auth.person(), seriesId, input)).build();
    }
}
