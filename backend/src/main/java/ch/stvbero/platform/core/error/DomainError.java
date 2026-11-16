package ch.stvbero.platform.core.error;

import java.util.List;

/** An error that goes to the client as Problem Details. */
public class DomainError extends RuntimeException {

    private final int status;
    private final String title;
    private final String rule;
    private final List<Problem.FieldError> fields;

    public DomainError(int status, String title, String detail, String rule) {
        this(status, title, detail, rule, null);
    }

    private DomainError(int status, String title, String detail, String rule, List<Problem.FieldError> fields) {
        super(detail);
        this.status = status;
        this.title = title;
        this.rule = rule;
        this.fields = fields;
    }

    /** 400 with one invalid field, e.g. a period whose end is before its start. */
    public static DomainError invalidField(String field, String message) {
        return new DomainError(400, "Ungültige Anfrage", message, null, List.of(new Problem.FieldError(field, message)));
    }

    /** 422: a domain rule is violated, with the field where the message is shown (e.g. ANM-04). */
    public static DomainError ruleViolation(String field, String title, String message, String rule) {
        return new DomainError(422, title, message, rule, List.of(new Problem.FieldError(field, message)));
    }

    /** 409: contradicts the current state. */
    public static DomainError conflict(String title, String detail, String rule) {
        return new DomainError(409, title, detail, rule);
    }

    public static DomainError notSignedIn() {
        return new DomainError(401, "Nicht angemeldet", "Bitte melde dich an.", null);
    }

    public static DomainError forbidden() {
        return new DomainError(403, "Keine Berechtigung", "Dafür hast du keine Berechtigung.", "RR-07");
    }

    public static DomainError notFound(String detail) {
        return new DomainError(404, "Nicht gefunden", detail, null);
    }

    public Problem toProblem() {
        return new Problem(null, title, status, getMessage(), rule, fields);
    }

    public int status() {
        return status;
    }

    public String rule() {
        return rule;
    }
}
