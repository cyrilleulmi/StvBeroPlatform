package ch.stvbero.platform.core.error;

import java.util.List;

/** Fehler, der als Problem Details an den Client geht. */
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

    /** 400 mit einem fehlerhaften Feld, z.B. Zeitraum «bis» vor «von». */
    public static DomainError invalidField(String field, String message) {
        return new DomainError(400, "Ungültige Anfrage", message, null, List.of(new Problem.FieldError(field, message)));
    }

    /** 422: fachliche Regel verletzt, mit dem Feld, bei dem die Meldung erscheint (z.B. ANM-04). */
    public static DomainError ruleViolation(String field, String title, String message, String rule) {
        return new DomainError(422, title, message, rule, List.of(new Problem.FieldError(field, message)));
    }

    /** 409: widerspricht dem aktuellen Zustand. */
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
