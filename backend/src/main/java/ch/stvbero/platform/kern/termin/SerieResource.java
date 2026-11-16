package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.anmeldung.Anmeldung;
import ch.stvbero.platform.kern.fehler.Fehler;
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

/** Serien (TER-05, TER-07, TER-08). */
@Path("/api/serien")
@Tag(name = "Termine")
public class SerieResource {

    @Inject
    Anmeldung anmeldung;

    @Inject
    SerieService serien;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "serieAnlegen", summary = "Serie anlegen, erzeugt die Termine im Gültigkeitszeitraum (TER-05, Coach)")
    @APIResponse(responseCode = "201", description = "Angelegt",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SerieDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public Response anlegen(@Valid @NotNull SerieSchreiben eingabe) {
        return Response.status(201).entity(serien.anlegen(anmeldung.person(), eingabe)).build();
    }

    @GET
    @Path("/{serieId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "serieLesen", summary = "Serie mit Ausnahmen")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SerieDto.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public SerieDto lesen(@PathParam("serieId") UUID serieId) {
        return serien.lesen(anmeldung.person(), serieId);
    }

    @PUT
    @Path("/{serieId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "serieAendern", summary = "Serie ändern. Gilt für künftige Termine, gegebene Antworten bleiben (TER-08, Coach).")
    @APIResponse(responseCode = "200", description = "Geändert",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SerieDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public SerieDto aendern(@PathParam("serieId") UUID serieId, @Valid @NotNull SerieSchreiben eingabe) {
        return serien.aendern(anmeldung.person(), serieId, eingabe);
    }

    @POST
    @Path("/{serieId}/ausnahmen")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "ausnahmeAnlegen", summary = "Zeitraum von der Serie ausnehmen, z.B. Ferien (TER-07, Coach)")
    @APIResponse(responseCode = "201", description = "Angelegt",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SerieDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public Response ausnahmeAnlegen(@PathParam("serieId") UUID serieId, @Valid @NotNull AusnahmeDto eingabe) {
        return Response.status(201).entity(serien.ausnahmeAnlegen(anmeldung.person(), serieId, eingabe)).build();
    }
}
