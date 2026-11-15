package ch.stvbero.platform.kern.fehler;

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
public class UnlesbarMapper {

    static final String DETAIL = "Die Anfrage konnte nicht gelesen werden. Bitte die Eingaben prüfen.";
    static final String UNGUELTIGER_WERT = "Ungültiger Wert.";

    @ServerExceptionMapper
    public Response jackson(JsonProcessingException ausnahme) {
        return antwort(ausnahme);
    }

    /** Eigene Methode, weil Quarkus für diesen Typ einen eingebauten Mapper ohne Problem Details mitbringt. */
    @ServerExceptionMapper
    public Response typ(MismatchedInputException ausnahme) {
        return antwort(ausnahme);
    }

    /** Alle anderen WebApplicationExceptions behalten ihre eigene Antwort (z.B. 404, 405, 415). */
    @ServerExceptionMapper
    public Response webApplication(WebApplicationException ausnahme) {
        for (Throwable t = ausnahme.getCause(); t != null; t = t.getCause()) {
            if (t instanceof JsonProcessingException json) {
                return antwort(json);
            }
        }
        return ausnahme.getResponse();
    }

    static Response antwort(JsonProcessingException ausnahme) {
        String feld = feld(ausnahme);
        List<Fehler.FeldFehler> felder = feld == null ? null : List.of(new Fehler.FeldFehler(feld, UNGUELTIGER_WERT));
        Fehler fehler = new Fehler(null, "Ungültige Anfrage", 400, DETAIL, null, felder);
        return Response.status(400).type(Fehler.MEDIENTYP).entity(fehler).build();
    }

    /** Name des letzten Felds im JSON-Pfad, z.B. «von». Null, wenn kein Feld bestimmbar ist (Syntaxfehler). */
    private static String feld(JsonProcessingException ausnahme) {
        if (!(ausnahme instanceof JsonMappingException mapping)) {
            return null;
        }
        List<JsonMappingException.Reference> pfad = mapping.getPath();
        for (int i = pfad.size() - 1; i >= 0; i--) {
            String name = pfad.get(i).getFieldName();
            if (name != null) {
                return name;
            }
        }
        return null;
    }
}
