package ch.stvbero.platform.core.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.List;

/**
 * Nicht lesbare Anfragen (ungültiges JSON, falscher Typ, falsches Datumsformat) als 400 mit Problem Details statt
 * eines leeren Bodys (Nachbesserung #16, docs/api/README.md «Fehler»).
 *
 * <p>Quarkus REST meldet Syntaxfehler als {@link WebApplicationException} mit einer Jackson-Ursache und
 * Typfehler als {@link MismatchedInputException}. Beides landet hier.
 */
public class UnreadableRequestMapper {

    static final String DETAIL = "Die Anfrage konnte nicht gelesen werden. Bitte die Eingaben prüfen.";
    static final String INVALID_VALUE = "Ungültiger Wert.";

    @ServerExceptionMapper
    public Response jackson(JsonProcessingException exception) {
        return response(exception);
    }

    /** Eigene Methode, weil Quarkus für diesen Typ einen eingebauten Mapper ohne Problem Details mitbringt. */
    @ServerExceptionMapper
    public Response type(MismatchedInputException exception) {
        return response(exception);
    }

    /** Alle anderen WebApplicationExceptions behalten ihre eigene Antwort (z.B. 404, 405, 415). */
    @ServerExceptionMapper
    public Response webApplication(WebApplicationException exception) {
        for (Throwable t = exception.getCause(); t != null; t = t.getCause()) {
            if (t instanceof JsonProcessingException json) {
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

    /** Name des letzten Felds im JSON-Pfad, z.B. «von». Null, wenn kein Feld bestimmbar ist (Syntaxfehler). */
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
