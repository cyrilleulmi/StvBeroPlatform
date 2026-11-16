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
 * Formally invalid requests (Bean Validation) become 400 with Problem Details and fields.
 *
 * <p>If the body is missing entirely (follow-up fix #31), Bean Validation reports the method parameter itself,
 * e.g. {@code input}. That is not a field of the API: the response then says in {@code detail} that the data is
 * missing and does not list the parameter under {@code fields}.
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
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    /**
     * Whether the violation concerns the body parameter itself (path {@code method.parameter}, a parameter
     * without a JAX-RS annotation such as {@code @QueryParam} or {@code @PathParam}).
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
        Method resourceMethod = method(violation.getRootBeanClass(), method.getName(),
                method.getParameterTypes().toArray(Class<?>[]::new));
        if (resourceMethod == null) {
            return false;
        }
        for (Annotation annotation : resourceMethod.getParameterAnnotations()[index]) {
            String pkg = annotation.annotationType().getPackageName();
            if (pkg.equals("jakarta.ws.rs") || pkg.startsWith("org.jboss.resteasy.reactive")) {
                return false;
            }
        }
        return true;
    }

    private static Method method(Class<?> type, String name, Class<?>[] types) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(name, types);
            } catch (NoSuchMethodException e) {
                // keep searching in the superclass (e.g. a CDI subclass)
            }
        }
        return null;
    }
}
