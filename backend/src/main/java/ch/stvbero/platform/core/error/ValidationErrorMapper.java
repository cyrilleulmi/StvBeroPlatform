package ch.stvbero.platform.core.error;

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
public class ValidationErrorMapper implements ExceptionMapper<ConstraintViolationException> {

    static final String DETAIL = "Die Anfrage ist unvollständig oder ungültig.";
    static final String DETAIL_WITHOUT_BODY = "Die Anfrage enthält keine Daten. Bitte die Eingaben prüfen.";

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        boolean bodyMissing = false;
        List<Problem.FieldError> fields = new ArrayList<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            if (isBody(violation)) {
                bodyMissing = true;
            } else {
                fields.add(new Problem.FieldError(field(violation), violation.getMessage()));
            }
        }
        fields.sort(Comparator.comparing(Problem.FieldError::field));
        Problem error = new Problem(null, "Ungültige Anfrage", 400, bodyMissing ? DETAIL_WITHOUT_BODY : DETAIL, null,
                fields.isEmpty() ? null : List.copyOf(fields));
        return Response.status(400).type(Problem.MEDIA_TYPE).entity(error).build();
    }

    private static String field(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int point = path.lastIndexOf('.');
        return point >= 0 ? path.substring(point + 1) : path;
    }

    /**
     * Ob die Verletzung den Body-Parameter selbst betrifft (Pfad «methode.parameter», Parameter ohne
     * JAX-RS-Annotation wie {@code @QueryParam} oder {@code @PathParam}).
     */
    static boolean isBody(ConstraintViolation<?> violation) {
        List<Path.Node> nodes = new ArrayList<>();
        violation.getPropertyPath().forEach(nodes::add);
        if (nodes.size() != 2 || nodes.get(0).getKind() != ElementKind.METHOD
                || nodes.get(1).getKind() != ElementKind.PARAMETER) {
            return false;
        }
        Path.MethodNode method = nodes.get(0).as(Path.MethodNode.class);
        int index = nodes.get(1).as(Path.ParameterNode.class).getParameterIndex();
        Method m = method(violation.getRootBeanClass(), method.getName(),
                method.getParameterTypes().toArray(Class<?>[]::new));
        if (m == null) {
            return false;
        }
        for (Annotation annotation : m.getParameterAnnotations()[index]) {
            String pkg = annotation.annotationType().getPackageName();
            if (pkg.equals("jakarta.ws.rs") || pkg.startsWith("org.jboss.resteasy.reactive")) {
                return false;
            }
        }
        return true;
    }

    private static Method method(Class<?> type, String name, Class<?>[] types) {
        for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                return k.getDeclaredMethod(name, types);
            } catch (NoSuchMethodException e) {
                // in der Oberklasse weitersuchen (z.B. CDI-Unterklasse)
            }
        }
        return null;
    }
}
