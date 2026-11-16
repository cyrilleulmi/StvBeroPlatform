package ch.stvbero.platform.core.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/** RFC 9457 Problem Details. {@code detail} ist ein deutscher Satz zum direkten Anzeigen. */
@Schema(name = "Problem")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Problem(String type, String title, int status, String detail, String rule, List<FieldError> fields) {

    public static final String MEDIA_TYPE = "application/problem+json";

    public record FieldError(String field, String message) {
    }
}
