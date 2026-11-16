package ch.stvbero.platform.kern.swissunihockey;

import ch.stvbero.platform.kern.anmeldung.Anmeldung;
import ch.stvbero.platform.kern.fehler.Fehler;
import ch.stvbero.platform.kern.team.TeamDto;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/** Anbindung swiss unihockey (SUH-02, SUH-03). Nur Admins (07-rollen-rechte.md). */
@Path("/api/teams/{teamId}/swiss-unihockey")
@Tag(name = "SwissUnihockey")
public class SwissUnihockeyResource {

    @Inject
    Anmeldung anmeldung;

    @Inject
    Abgleich abgleich;

    public record Verknuepfung(
            @Schema(type = SchemaType.INTEGER, nullable = true, description = "`null` löst die Verknüpfung")
            Integer suTeamId) {
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "suVerknuepfen",
            summary = "Team mit swiss unihockey verknüpfen oder Verknüpfung lösen (SUH-02, Admin)")
    @APIResponse(responseCode = "200", description = "Gespeichert",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TeamDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Team-ID schon mit einem anderen Team verknüpft",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public TeamDto verknuepfen(@PathParam("teamId") UUID teamId, @Valid @NotNull Verknuepfung eingabe) {
        return abgleich.verknuepfen(anmeldung.person(), teamId, eingabe.suTeamId());
    }

    @POST
    @Path("/abgleich")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "suAbgleichen",
            summary = "Abgleich sofort ausführen statt auf den täglichen Lauf zu warten (SUH-03, Admin)")
    @APIResponse(responseCode = "200", description = "Ergebnis des Abgleichs",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = AbgleichErgebnisDto.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Nicht verknüpft oder nicht in der aktuellen Saison",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "502", description = "swiss unihockey nicht erreichbar oder unerwartete Antwort (SUH-09)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public AbgleichErgebnisDto abgleichen(@PathParam("teamId") UUID teamId) {
        return abgleich.abgleichen(anmeldung.person(), teamId);
    }
}
