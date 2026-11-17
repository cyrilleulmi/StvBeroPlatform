package ch.stvbero.platform.core.error;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class DomainErrorMapper implements ExceptionMapper<DomainError> {

    @Override
    public Response toResponse(DomainError error) {
        return Response.status(error.status())
                .type(Problem.MEDIA_TYPE)
                .entity(error.toProblem())
                .build();
    }
}
