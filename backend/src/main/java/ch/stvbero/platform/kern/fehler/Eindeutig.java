package ch.stvbero.platform.kern.fehler;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;

import java.util.function.Supplier;

/**
 * Schreibt ausstehende Änderungen sofort in die Datenbank. Verletzt das einen Unique-Constraint, wird daraus ein
 * {@link FachFehler} (409) statt eines 500. Das passiert, wenn zwei gleiche Anfragen gleichzeitig ankommen
 * (z.B. Doppelklick) und beide die fachliche Prüfung bestehen, bevor die erste gespeichert ist.
 */
public final class Eindeutig {

    private Eindeutig() {
    }

    public static void speichern(EntityManager em, String constraint, Supplier<FachFehler> fehler) {
        try {
            em.flush();
        } catch (PersistenceException ausnahme) {
            if (verletzt(ausnahme, constraint)) {
                throw fehler.get();
            }
            throw ausnahme;
        }
    }

    static boolean verletzt(Throwable ausnahme, String constraint) {
        for (Throwable t = ausnahme; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException c && c.getConstraintName() != null
                    && c.getConstraintName().equalsIgnoreCase(constraint)) {
                return true;
            }
        }
        return false;
    }
}
