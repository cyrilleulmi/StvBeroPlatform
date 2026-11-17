package ch.stvbero.platform.core.auth;

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

@Path("/api/session")
@Tag(name = "Me")
public class SessionResource {

    @Inject
    AuthContext auth;

    @GET
    @Public
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getSession", summary = "Whether a valid session exists")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SessionDto.class)))
    public SessionDto get() {
        return new SessionDto(auth.optional().isPresent());
    }
}
