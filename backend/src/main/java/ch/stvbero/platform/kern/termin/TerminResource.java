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

/** Termine (TER). */
@Path("/api/termine")
@Tag(name = "Termine")
public class TerminResource {

    @Inject
    Anmeldung anmeldung;

    @Inject
    TerminService termine;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "termineLesen", summary = "Termine der eigenen Teams mit eigener Antwort (TER-13)")
    @APIResponse(responseCode = "200", description = "Chronologisch sortiert (vergangene absteigend)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = TerminKurzDto.class)))
    @APIResponse(responseCode = "401", description = "Nicht angemeldet",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public List<TerminKurzDto> liste(@QueryParam("von") LocalDate von, @QueryParam("bis") LocalDate bis,
                                     @QueryParam("team") UUID team, @QueryParam("typ") TerminTyp typ,
                                     @QueryParam("ansicht") String ansicht) {
        return termine.liste(anmeldung.person(), von, bis, team, typ, ansicht);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "terminAnlegen", summary = "Einzeltermin, Spieltag oder Anlass erfassen (TER-11, AUF-16, Coach)")
    @APIResponse(responseCode = "201", description = "Angelegt",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TerminDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public Response anlegen(@Valid @NotNull TerminSchreiben eingabe) {
        return Response.status(201).entity(termine.anlegen(anmeldung.person(), eingabe)).build();
    }

    @GET
    @Path("/{terminId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "terminLesen", summary = "Termin mit eigener Antwort, bei Spieltag mit Spielen, Treffpunkt und eigenem Aufgebotsstatus")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TerminDto.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public TerminDto lesen(@PathParam("terminId") UUID terminId) {
        return termine.lesen(anmeldung.person(), terminId);
    }

    @PUT
    @Path("/{terminId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "terminAendern", summary = "Termin ändern oder verschieben (TER-06, TER-12, Coach)")
    @APIResponse(responseCode = "200", description = "Geändert",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TerminDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "422", description = "Fachliche Regel verletzt",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public TerminDto aendern(@PathParam("terminId") UUID terminId, @Valid @NotNull TerminSchreiben eingabe) {
        return termine.aendern(anmeldung.person(), terminId, eingabe);
    }

    @POST
    @Path("/{terminId}/absagen")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "terminAbsagen", summary = "Termin absagen (TER-06, TER-12, Coach). Danach sind keine Antworten mehr möglich.")
    @APIResponse(responseCode = "200", description = "Abgesagt",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TerminDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public TerminDto absagen(@PathParam("terminId") UUID terminId, @Valid @NotNull AbsagenDto eingabe) {
        return termine.absagen(anmeldung.person(), terminId, eingabe.grund());
    }
}
