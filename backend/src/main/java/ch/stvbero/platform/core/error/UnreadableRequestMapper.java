package ch.stvbero.platform.core.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.List;

/**
 * Unreadable requests (invalid JSON, wrong type, wrong date format) become 400 with Problem Details instead of
 * an empty body (follow-up fix #16, docs/api/README.md, errors section).
 *
 * <p>Quarkus REST reports syntax errors as a {@link WebApplicationException} with a Jackson cause and
 * type errors as a {@link MismatchedInputException}. Both end up here.
 */
public class UnreadableRequestMapper {

    static final String DETAIL = "Die Anfrage konnte nicht gelesen werden. Bitte die Eingaben prüfen.";
    static final String INVALID_VALUE = "Ungültiger Wert.";

    @ServerExceptionMapper
    public Response jackson(JsonProcessingException exception) {
        return response(exception);
    }

    /** A separate method, because Quarkus ships a built-in mapper without Problem Details for this type. */
    @ServerExceptionMapper
    public Response type(MismatchedInputException exception) {
        return response(exception);
    }

    /** All other WebApplicationExceptions keep their own response (e.g. 404, 405, 415). */
    @ServerExceptionMapper
    public Response webApplication(WebApplicationException exception) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof JsonProcessingException json) {
                return response(json);
            }
        }
        return exception.getResponse();
    }

    static Response response(JsonProcessingException exception) {
        String field = field(exception);
        List<Problem.FieldError> fields = field == null ? null : List.of(new Problem.FieldError(field, INVALID_VALUE));
        Problem error = new Problem(null, "Ungültige Anfrage", 400, DETAIL, null, fields);
        return Response.status(400).type(Problem.MEDIA_TYPE).entity(error).build();
    }

    /** Name of the last field in the JSON path, e.g. {@code startDate}. Null if no field can be found (syntax error). */
    private static String field(JsonProcessingException exception) {
        if (!(exception instanceof JsonMappingException mapping)) {
            return null;
        }
        List<JsonMappingException.Reference> path = mapping.getPath();
        for (int i = path.size() - 1; i >= 0; i--) {
            String name = path.get(i).getFieldName();
            if (name != null) {
                return name;
            }
        }
        return null;
    }
}
