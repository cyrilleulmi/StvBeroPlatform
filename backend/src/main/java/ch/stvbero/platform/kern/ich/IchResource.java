package ch.stvbero.platform.kern.ich;

import ch.stvbero.platform.kern.anmeldung.Angemeldet;
import ch.stvbero.platform.kern.anmeldung.Anmeldung;
import ch.stvbero.platform.kern.anmeldung.Oeffentlich;
import ch.stvbero.platform.kern.anmeldung.SitzungService;
import ch.stvbero.platform.kern.fehler.Fehler;
import ch.stvbero.platform.kern.person.Person;
import ch.stvbero.platform.kern.saison.Saison;
import ch.stvbero.platform.kern.saison.SaisonDto;
import ch.stvbero.platform.kern.team.TeamService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import ch.stvbero.platform.kern.uhr.Uhr;

@Path("/api")
@Tag(name = "Ich")
public class IchResource {

    @Inject
    Anmeldung anmeldung;

    @Inject
    SitzungService sitzungen;

    @Inject
    TeamService teams;

    @Inject
    Uhr uhr;

    @GET
    @Path("/ich")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "ichLesen", summary = "Angemeldete Person mit Teamzugehörigkeiten der aktuellen Saison")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = IchDto.class)))
    @APIResponse(responseCode = "401", description = "Nicht angemeldet",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public IchDto lesen() {
        Angemeldet angemeldet = anmeldung.person();
        Person person = Person.findById(angemeldet.personId());
        Saison saison = Saison.aktuelleOderFehler();
        return new IchDto(person.id, person.vorname, person.nachname, person.email, person.admin,
                SaisonDto.von(saison, saison), teams.meine(person.id, saison), uhr.jetztMitOffset());
    }

    @POST
    @Path("/abmelden")
    @Oeffentlich
    @Operation(operationId = "abmelden", summary = "Sitzung beenden und Sitzungs-Cookie löschen")
    @APIResponse(responseCode = "204", description = "Abgemeldet")
    public Response abmelden() {
        anmeldung.token().ifPresent(sitzungen::beenden);
        return Response.noContent().cookie(sitzungen.cookieLoeschen()).build();
    }
}
