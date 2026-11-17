package ch.stvbero.platform.core.error;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;

import java.util.List;
import java.util.function.Supplier;

/**
 * Writes pending changes to the database right away. If that violates a unique constraint, the result is a
 * {@link DomainError} (409) instead of a 500. This happens when two identical requests arrive at the same time
 * (e.g. a double click) and both pass the domain checks before the first one is saved.
 */
public final class Unique {

    private Unique() {
    }

    public static void save(EntityManager em, String constraint, Supplier<DomainError> error) {
        save(em, List.of(constraint), error);
    }

    /** As above, for several constraints that protect the same rule. */
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
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null
                    && violation.getConstraintName().equalsIgnoreCase(constraint)) {
                return true;
            }
        }
        return false;
    }
}
