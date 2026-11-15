package ch.stvbero.platform.kern.person;

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

import java.util.List;
import java.util.UUID;

@Path("/api/personen")
@Tag(name = "Personen")
@Produces(MediaType.APPLICATION_JSON)
public class PersonResource {

    @Inject
    Anmeldung anmeldung;

    @Inject
    PersonService personen;

    @GET
    @Operation(operationId = "personenLesen", summary = "Alle Personen (Admin)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = PersonDto.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public List<PersonDto> liste(@QueryParam("suche") String suche) {
        return personen.liste(anmeldung.person(), suche);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "personAnlegen", summary = "Person anlegen (Admin)")
    @APIResponse(responseCode = "201", description = "Angelegt",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PersonDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public Response anlegen(@Valid @NotNull PersonSchreiben eingabe) {
        return Response.status(201).entity(personen.anlegen(anmeldung.person(), eingabe)).build();
    }

    @GET
    @Path("/{personId}")
    @Operation(operationId = "personLesen", summary = "Person mit allen Teamzugehörigkeiten (Admin)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PersonDetailDto.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public PersonDetailDto lesen(@PathParam("personId") UUID personId) {
        return personen.lesen(anmeldung.person(), personId);
    }

    @PUT
    @Path("/{personId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "personAendern", summary = "Person ändern (Admin)")
    @APIResponse(responseCode = "200", description = "Geändert",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PersonDto.class)))
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "403", description = "Keine Berechtigung (RR-07)",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public PersonDto aendern(@PathParam("personId") UUID personId, @Valid @NotNull PersonSchreiben eingabe) {
        return personen.aendern(anmeldung.person(), personId, eingabe);
    }
}
