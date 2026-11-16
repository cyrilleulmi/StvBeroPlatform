package ch.stvbero.platform.kern.benachrichtigung;

import ch.stvbero.platform.kern.anmeldung.Anmeldung;
import ch.stvbero.platform.kern.fehler.Fehler;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

/** Eigene Benachrichtigungen in der App (BEN, E-56). */
@Path("/api/benachrichtigungen")
@Tag(name = "Benachrichtigungen")
public class BenachrichtigungResource {

    @Inject
    Anmeldung anmeldung;

    @Inject
    Benachrichtigungen benachrichtigungen;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "benachrichtigungenLesen", summary = "Eigene Benachrichtigungen, neueste zuerst (BEN-12)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(type = SchemaType.ARRAY, implementation = BenachrichtigungDto.class)))
    @APIResponse(responseCode = "401", description = "Nicht angemeldet",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public List<BenachrichtigungDto> lesen(@QueryParam("ungelesen") Boolean ungelesen) {
        return benachrichtigungen.eigene(anmeldung.person(), Boolean.TRUE.equals(ungelesen));
    }

    @POST
    @Path("/{benachrichtigungId}/gelesen")
    @Operation(operationId = "benachrichtigungGelesen", summary = "Als gelesen markieren")
    @APIResponse(responseCode = "204", description = "Markiert")
    @APIResponse(responseCode = "401", description = "Nicht angemeldet",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public void gelesen(@PathParam("benachrichtigungId") UUID id) {
        benachrichtigungen.gelesen(anmeldung.person(), id);
    }
}
