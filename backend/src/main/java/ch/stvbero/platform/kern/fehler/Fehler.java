package ch.stvbero.platform.kern.fehler;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/** RFC 9457 Problem Details. {@code detail} ist ein deutscher Satz zum direkten Anzeigen. */
@Schema(name = "Fehler")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Fehler(String type, String title, int status, String detail, String regel, List<FeldFehler> felder) {

    public static final String MEDIENTYP = "application/problem+json";

    public record FeldFehler(String feld, String meldung) {
    }
}
