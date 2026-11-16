package ch.stvbero.platform.core.error;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;

import java.util.List;
import java.util.function.Supplier;

/**
 * Schreibt ausstehende Änderungen sofort in die Datenbank. Verletzt das einen Unique-Constraint, wird daraus ein
 * {@link FachFehler} (409) statt eines 500. Das passiert, wenn zwei gleiche Anfragen gleichzeitig ankommen
 * (z.B. Doppelklick) und beide die fachliche Prüfung bestehen, bevor die erste gespeichert ist.
 */
public final class Unique {

    private Unique() {
    }

    public static void save(EntityManager em, String constraint, Supplier<DomainError> error) {
        save(em, List.of(constraint), error);
    }

    /** Wie oben, für mehrere Constraints, die dieselbe Regel schützen. */
    public static void save(EntityManager em, List<String> constraints, Supplier<DomainError> error) {
        try {
            em.flush();
        } catch (PersistenceException exception) {
            if (constraints.stream().anyMatch(c -> violated(exception, c))) {
                throw error.get();
            }
            throw exception;
        }
    }

    static boolean violated(Throwable exception, String constraint) {
        for (Throwable t = exception; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException c && c.getConstraintName() != null
                    && c.getConstraintName().equalsIgnoreCase(constraint)) {
                return true;
            }
        }
        return false;
    }
}
