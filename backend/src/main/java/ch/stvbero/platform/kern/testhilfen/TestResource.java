package ch.stvbero.platform.kern.testhilfen;

import ch.stvbero.platform.kern.anmeldung.Oeffentlich;
import ch.stvbero.platform.kern.anmeldung.SitzungService;
import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.fehler.Fehler;
import ch.stvbero.platform.kern.person.Konto;
import ch.stvbero.platform.kern.uhr.Uhr;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Test-Hilfen aus docs/testbarkeit.md. Existieren nur in den Profilen dev und test: Im Profil prod
 * ist {@code stv.testhilfen=false}, die Ressource wird gar nicht registriert (404).
 */
@Path("/api/test")
@Tag(name = "Test")
@Oeffentlich
@IfBuildProperty(name = "stv.testhilfen", stringValue = "true")
public class TestResource {

    @Inject
    Seed seed;

    @Inject
    Uhr uhr;

    @Inject
    SitzungService sitzungen;

    public record UhrSetzen(@NotNull OffsetDateTime jetzt) {
    }

    public record UhrStand(OffsetDateTime jetzt, boolean fest) {
    }

    public record Anmelden(@NotBlank String schluessel) {
    }

    @POST
    @Path("/reset")
    @Operation(operationId = "testReset", summary = "Datenbank auf den Seed zurücksetzen, Uhr auf Standard, Simulator zurücksetzen")
    @APIResponse(responseCode = "204", description = "Zurückgesetzt")
    public void reset() {
        seed.zuruecksetzen();
    }

    @PUT
    @Path("/uhr")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "testUhrSetzen", summary = "Backend-Uhr auf einen festen Zeitpunkt setzen")
    @APIResponse(responseCode = "204", description = "Gesetzt")
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public void uhrSetzen(@Valid @NotNull UhrSetzen eingabe) {
        uhr.festsetzen(eingabe.jetzt().toInstant());
    }

    @GET
    @Path("/uhr")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "testUhrLesen", summary = "Aktuelle Backend-Zeit")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UhrStand.class)))
    public UhrStand uhrLesen() {
        return new UhrStand(uhr.jetztMitOffset(), uhr.istFest());
    }

    @DELETE
    @Path("/uhr")
    @Operation(operationId = "testUhrLoesen", summary = "Wieder die echte Zeit verwenden")
    @APIResponse(responseCode = "204", description = "Gelöst")
    public void uhrLoesen() {
        uhr.loesen();
    }

    @POST
    @Path("/anmelden")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "testAnmelden", summary = "Ohne Passwort als Seed-Person anmelden. Setzt das Sitzungs-Cookie.")
    @APIResponse(responseCode = "204", description = "Angemeldet")
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public Response anmelden(@Valid @NotNull Anmelden eingabe) {
        Konto konto = Konto.mitSchluessel(eingabe.schluessel())
                .orElseThrow(() -> FachFehler.nichtGefunden("Keine Person mit dem Schlüssel «" + eingabe.schluessel() + "»."));
        String token = sitzungen.eroeffnen(konto);
        return Response.noContent().cookie(sitzungen.cookie(token)).build();
    }

    @GET
    @Path("/personen")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "testPersonenLesen", summary = "Personen mit Schlüssel für die Testmodus-Anmeldung (/dev-login)")
    @APIResponse(responseCode = "200", description = "Nach Nachname, dann Vorname",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = TestPerson.class)))
    public List<TestPerson> personen() {
        return Konto.<Konto>find("schluessel is not null order by person.nachname, person.vorname").list().stream()
                .map(k -> new TestPerson(k.schluessel, k.person.id, k.person.vorname, k.person.nachname, k.person.admin))
                .toList();
    }
}
