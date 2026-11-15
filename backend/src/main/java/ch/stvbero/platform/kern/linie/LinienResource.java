package ch.stvbero.platform.kern.linie;

import ch.stvbero.platform.kern.anmeldung.Anmeldung;
import ch.stvbero.platform.kern.fehler.Fehler;
import ch.stvbero.platform.kern.team.TeamDetailDto;
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

/** Linien und Goalies eines Teams in der aktuellen Saison (LIN). */
@Path("/api/teams/{teamId}")
@Tag(name = "Linien")
public class LinienResource {

    @Inject
    Anmeldung anmeldung;

    @Inject
    LinienService linien;

    @GET
    @Path("/gruppen")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "gruppenLesen", summary = "Linien und Goalies eines Teams mit Spielerinnen, plus «Ohne Linie»")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TeamDetailDto.Gruppeneinteilung.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public TeamDetailDto.Gruppeneinteilung lesen(@PathParam("teamId") UUID teamId) {
        return linien.lesen(anmeldung.person(), teamId);
    }

    @POST
    @Path("/gruppen")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "linieAnlegen", summary = "Linie anlegen (LIN-07, Coach). Goalies gibt es pro Team automatisch genau einmal.")
    @APIResponse(responseCode = "201", description = "Angelegt",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = GruppeDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public Response anlegen(@PathParam("teamId") UUID teamId, @Valid @NotNull LinieSchreiben eingabe) {
        return Response.status(201).entity(linien.anlegen(anmeldung.person(), teamId, eingabe)).build();
    }

    @PUT
    @Path("/gruppen/{gruppeId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "linieAendern", summary = "Linie umbenennen oder Reihenfolge ändern (Coach)")
    @APIResponse(responseCode = "200", description = "Geändert",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = GruppeDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public GruppeDto aendern(@PathParam("teamId") UUID teamId, @PathParam("gruppeId") UUID gruppeId,
                             @Valid @NotNull LinieSchreiben eingabe) {
        return linien.aendern(anmeldung.person(), teamId, gruppeId, eingabe);
    }

    @DELETE
    @Path("/gruppen/{gruppeId}")
    @Operation(operationId = "linieLoeschen", summary = "Linie löschen (Coach). Ihre Spielerinnen sind danach «ohne Linie». Goalies lassen sich nicht löschen (409).")
    @APIResponse(responseCode = "204", description = "Gelöscht")
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public void loeschen(@PathParam("teamId") UUID teamId, @PathParam("gruppeId") UUID gruppeId) {
        linien.loeschen(anmeldung.person(), teamId, gruppeId);
    }

    @PUT
    @Path("/zuweisungen/{personId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "zuweisungSetzen", summary = "Spielerin einer Gruppe zuweisen oder auf «ohne Linie» setzen (LIN-02, LIN-07, LIN-10)")
    @APIResponse(responseCode = "204", description = "Gesetzt")
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public void zuweisen(@PathParam("teamId") UUID teamId, @PathParam("personId") UUID personId,
                         @Valid @NotNull ZuweisungSchreiben eingabe) {
        linien.zuweisen(anmeldung.person(), teamId, personId, eingabe.gruppeId());
    }
}
