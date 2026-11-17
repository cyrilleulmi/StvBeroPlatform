package ch.stvbero.platform.core.error;

import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unique constraint violations are detected in the cause chain (PTF-04, SAI-01). */
class UniqueTest {

    @Test
    void detectsTheConstraintInTheCauseChain() {
        var violation = new ConstraintViolationException("doppelt", new SQLException("23505"),
                "team_membership_one_role");
        var exception = new PersistenceException("flush", violation);
        assertTrue(Unique.violated(exception, "team_membership_one_role"));
        assertFalse(Unique.violated(exception, "season_name_key"));
        assertFalse(Unique.violated(new PersistenceException("other"), "season_name_key"));
    }
}
