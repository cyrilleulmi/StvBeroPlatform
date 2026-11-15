package ch.stvbero.platform.kern.fehler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Comparator;
import java.util.List;

/** Formal ungültige Anfragen (Bean Validation) als 400 mit Problem Details und Feldern. */
@Provider
public class UngueltigMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException ausnahme) {
        List<Fehler.FeldFehler> felder = ausnahme.getConstraintViolations().stream()
                .map(v -> new Fehler.FeldFehler(feld(v), v.getMessage()))
                .sorted(Comparator.comparing(Fehler.FeldFehler::feld))
                .toList();
        Fehler fehler = new Fehler(null, "Ungültige Anfrage", 400, "Die Anfrage ist unvollständig oder ungültig.", null, felder);
        return Response.status(400).type(Fehler.MEDIENTYP).entity(fehler).build();
    }

    private static String feld(ConstraintViolation<?> verletzung) {
        String pfad = verletzung.getPropertyPath().toString();
        int punkt = pfad.lastIndexOf('.');
        return punkt >= 0 ? pfad.substring(punkt + 1) : pfad;
    }
}
