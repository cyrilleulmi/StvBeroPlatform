package ch.stvbero.platform.kern.fehler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Formal ungültige Anfragen (Bean Validation) als 400 mit Problem Details und Feldern.
 *
 * <p>Fehlt der Body ganz (Nachbesserung #31), meldet Bean Validation den Methodenparameter selbst, z.B.
 * «eingabe». Das ist kein Feld der API: Die Antwort nennt dann im {@code detail}, dass die Daten fehlen, und
 * führt den Parameter nicht unter {@code felder}.
 */
@Provider
public class UngueltigMapper implements ExceptionMapper<ConstraintViolationException> {

    static final String DETAIL = "Die Anfrage ist unvollständig oder ungültig.";
    static final String DETAIL_OHNE_BODY = "Die Anfrage enthält keine Daten. Bitte die Eingaben prüfen.";

    @Override
    public Response toResponse(ConstraintViolationException ausnahme) {
        boolean bodyFehlt = false;
        List<Fehler.FeldFehler> felder = new ArrayList<>();
        for (ConstraintViolation<?> verletzung : ausnahme.getConstraintViolations()) {
            if (istBody(verletzung)) {
                bodyFehlt = true;
            } else {
                felder.add(new Fehler.FeldFehler(feld(verletzung), verletzung.getMessage()));
            }
        }
        felder.sort(Comparator.comparing(Fehler.FeldFehler::feld));
        Fehler fehler = new Fehler(null, "Ungültige Anfrage", 400, bodyFehlt ? DETAIL_OHNE_BODY : DETAIL, null,
                felder.isEmpty() ? null : List.copyOf(felder));
        return Response.status(400).type(Fehler.MEDIENTYP).entity(fehler).build();
    }

    private static String feld(ConstraintViolation<?> verletzung) {
        String pfad = verletzung.getPropertyPath().toString();
        int punkt = pfad.lastIndexOf('.');
        return punkt >= 0 ? pfad.substring(punkt + 1) : pfad;
    }

    /**
     * Ob die Verletzung den Body-Parameter selbst betrifft (Pfad «methode.parameter», Parameter ohne
     * JAX-RS-Annotation wie {@code @QueryParam} oder {@code @PathParam}).
     */
    static boolean istBody(ConstraintViolation<?> verletzung) {
        List<Path.Node> knoten = new ArrayList<>();
        verletzung.getPropertyPath().forEach(knoten::add);
        if (knoten.size() != 2 || knoten.get(0).getKind() != ElementKind.METHOD
                || knoten.get(1).getKind() != ElementKind.PARAMETER) {
            return false;
        }
        Path.MethodNode methode = knoten.get(0).as(Path.MethodNode.class);
        int index = knoten.get(1).as(Path.ParameterNode.class).getParameterIndex();
        Method m = methode(verletzung.getRootBeanClass(), methode.getName(),
                methode.getParameterTypes().toArray(Class<?>[]::new));
        if (m == null) {
            return false;
        }
        for (Annotation annotation : m.getParameterAnnotations()[index]) {
            String paket = annotation.annotationType().getPackageName();
            if (paket.equals("jakarta.ws.rs") || paket.startsWith("org.jboss.resteasy.reactive")) {
                return false;
            }
        }
        return true;
    }

    private static Method methode(Class<?> klasse, String name, Class<?>[] typen) {
        for (Class<?> k = klasse; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                return k.getDeclaredMethod(name, typen);
            } catch (NoSuchMethodException e) {
                // in der Oberklasse weitersuchen (z.B. CDI-Unterklasse)
            }
        }
        return null;
    }
}
