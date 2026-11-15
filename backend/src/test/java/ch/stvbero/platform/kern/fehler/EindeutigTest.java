package ch.stvbero.platform.kern.fehler;

import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unique-Constraint-Verletzungen werden in der Ursachenkette erkannt (PTF-04, SAI-01). */
class EindeutigTest {

    @Test
    void erkenntDenConstraintInDerUrsachenkette() {
        var verletzung = new ConstraintViolationException("doppelt", new SQLException("23505"),
                "teamzugehoerigkeit_eine_funktion");
        var ausnahme = new PersistenceException("flush", verletzung);
        assertTrue(Eindeutig.verletzt(ausnahme, "teamzugehoerigkeit_eine_funktion"));
        assertFalse(Eindeutig.verletzt(ausnahme, "saison_name_key"));
        assertFalse(Eindeutig.verletzt(new PersistenceException("anderes"), "saison_name_key"));
    }
}
