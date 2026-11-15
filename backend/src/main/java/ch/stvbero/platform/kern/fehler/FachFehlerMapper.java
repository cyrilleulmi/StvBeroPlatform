package ch.stvbero.platform.kern.fehler;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class FachFehlerMapper implements ExceptionMapper<FachFehler> {

    @Override
    public Response toResponse(FachFehler fehler) {
        return Response.status(fehler.status())
                .type(Fehler.MEDIENTYP)
                .entity(fehler.alsFehler())
                .build();
    }
}
