package ch.stvbero.platform.kern.anmeldung;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/sitzung")
@Tag(name = "Ich")
public class SitzungResource {

    @Inject
    Anmeldung anmeldung;

    @GET
    @Oeffentlich
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "sitzungLesen", summary = "Ob eine gültige Sitzung besteht")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SitzungDto.class)))
    public SitzungDto lesen() {
        return new SitzungDto(anmeldung.optional().isPresent());
    }
}
