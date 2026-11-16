package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.anmeldung.Anmeldung;
import ch.stvbero.platform.kern.fehler.Fehler;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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

/** Antworten und Übersicht eines Termins (ANM). */
@Path("/api/termine/{terminId}")
@Tag(name = "Antworten")
public class AntwortResource {

    @Inject
    Anmeldung anmeldung;

    @Inject
    AntwortService antworten;

    @GET
    @Path("/uebersicht")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "uebersichtLesen", summary = "Antworten nach Team und Gruppe, Coach-Block, Zähler (ANM-20 bis ANM-32)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UebersichtDto.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public UebersichtDto uebersicht(@PathParam("terminId") UUID terminId) {
        return antworten.uebersicht(anmeldung.person(), terminId);
    }

    @PUT
    @Path("/antworten/ich")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "eigeneAntwortSetzen", summary = "Eigene Antwort setzen oder ändern (ANM-01 bis ANM-10)")
    @APIResponse(responseCode = "200", description = "Gespeichert",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AntwortDto.class)))
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
    public AntwortDto eigeneSetzen(@PathParam("terminId") UUID terminId, @Valid @NotNull AntwortSetzen eingabe) {
        return antworten.setzen(anmeldung.person(), terminId, null, eingabe);
    }

    @PUT
    @Path("/antworten/{personId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "antwortStellvertretendSetzen", summary = "Antwort stellvertretend setzen (ANM-11, ANM-12, RR-06)")
    @APIResponse(responseCode = "200", description = "Gespeichert",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AntwortDto.class)))
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
    public AntwortDto stellvertretendSetzen(@PathParam("terminId") UUID terminId, @PathParam("personId") UUID personId,
                                            @Valid @NotNull AntwortSetzen eingabe) {
        return antworten.setzen(anmeldung.person(), terminId, personId, eingabe);
    }
}
