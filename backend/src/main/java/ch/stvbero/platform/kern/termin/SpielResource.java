package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.anmeldung.Anmeldung;
import ch.stvbero.platform.kern.fehler.Fehler;
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
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/** Spiele und Aufgebot eines Spieltags (AUF). */
@Path("/api/termine/{terminId}")
@Tag(name = "Spieltage")
public class SpielResource {

    @Inject
    Anmeldung anmeldung;

    @Inject
    SpielService spiele;

    @Inject
    AufgebotService aufgebote;

    @POST
    @Path("/spiele")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "spielHinzufuegen", summary = "Spiel zu einem Spieltag hinzufügen (AUF-14, Coach)")
    @APIResponse(responseCode = "201", description = "Angelegt",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SpielDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public Response hinzufuegen(@PathParam("terminId") UUID terminId, @Valid @NotNull SpielSchreiben eingabe) {
        return Response.status(201).entity(spiele.hinzufuegen(anmeldung.person(), terminId, eingabe)).build();
    }

    @PUT
    @Path("/spiele/{spielId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "spielAendern", summary = "Spiel ändern, z.B. Resultat erfassen (AUF-15, Coach). Importierte Felder sind schreibgeschützt (409).")
    @APIResponse(responseCode = "200", description = "Geändert",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SpielDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public SpielDto aendern(@PathParam("terminId") UUID terminId, @PathParam("spielId") UUID spielId,
                            @Valid @NotNull SpielSchreiben eingabe) {
        return spiele.aendern(anmeldung.person(), terminId, spielId, eingabe);
    }

    @DELETE
    @Path("/spiele/{spielId}")
    @Operation(operationId = "spielLoeschen", summary = "Von Hand erfasstes Spiel löschen (Coach)")
    @APIResponse(responseCode = "204", description = "Gelöscht")
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public Response loeschen(@PathParam("terminId") UUID terminId, @PathParam("spielId") UUID spielId) {
        spiele.loeschen(anmeldung.person(), terminId, spielId);
        return Response.noContent().build();
    }

    @GET
    @Path("/aufgebot")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "aufgebotLesen", summary = "Aufgebot mit allen Spielerinnen des Teams, gruppiert, mit Antwortstatus (AUF-21, AUF-22, Coach)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AufgebotDto.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public AufgebotDto aufgebot(@PathParam("terminId") UUID terminId) {
        return aufgebote.lesen(anmeldung.person(), terminId);
    }

    @PUT
    @Path("/aufgebot")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "aufgebotSetzen", summary = "Auswahl speichern (Coach). Ist das Aufgebot veröffentlicht, werden Betroffene benachrichtigt (AUF-27).")
    @APIResponse(responseCode = "200", description = "Gespeichert",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AufgebotDto.class)))
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
    public AufgebotDto aufgebotSetzen(@PathParam("terminId") UUID terminId, @Valid @NotNull AufgebotSetzen eingabe) {
        return aufgebote.setzen(anmeldung.person(), terminId, eingabe);
    }

    @POST
    @Path("/aufgebot/veroeffentlichen")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "aufgebotVeroeffentlichen", summary = "Aufgebot veröffentlichen und alle Spielerinnen des Teams benachrichtigen (AUF-23, AUF-24, Coach)")
    @APIResponse(responseCode = "200", description = "Veröffentlicht",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AufgebotDto.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public AufgebotDto veroeffentlichen(@PathParam("terminId") UUID terminId) {
        return aufgebote.veroeffentlichen(anmeldung.person(), terminId);
    }
}
