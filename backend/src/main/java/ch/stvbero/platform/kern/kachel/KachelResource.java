package ch.stvbero.platform.kern.kachel;

import ch.stvbero.platform.kern.anmeldung.Anmeldung;
import ch.stvbero.platform.kern.fehler.Fehler;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/kacheln")
@Tag(name = "Ich")
public class KachelResource {

    @Inject
    KachelRegister register;

    @Inject
    Anmeldung anmeldung;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "kachelnLesen", summary = "Kacheln, die die angemeldete Person sehen darf (NF-11, NF-12)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = Kachel.class)))
    @APIResponse(responseCode = "401", description = "Nicht angemeldet",
            content = @Content(mediaType = Fehler.MEDIENTYP, schema = @Schema(implementation = Fehler.class)))
    public List<Kachel> lesen() {
        return register.sichtbarFuer(anmeldung.person());
    }
}
