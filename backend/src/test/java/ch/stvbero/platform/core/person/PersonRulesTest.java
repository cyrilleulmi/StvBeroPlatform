package ch.stvbero.platform.core.person;

import ch.stvbero.platform.core.error.DomainError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Unit-Tests der Personenregeln (RR-04). */
class PersonRulesTest {

    @Test
    void rr04LastAdminCanThePermissionNotRevokedBecome() {
        DomainError f = assertThrows(DomainError.class, () -> PersonRules.checkAdminRevocation(true, false, 1));
        assertEquals(409, f.status());
        assertEquals("RR-04", f.rule());
    }

    @Test
    void rr04WithSecondAdminWorksTheRevocation() {
        assertDoesNotThrow(() -> PersonRules.checkAdminRevocation(true, false, 2));
        assertDoesNotThrow(() -> PersonRules.checkAdminRevocation(true, true, 1));
        assertDoesNotThrow(() -> PersonRules.checkAdminRevocation(false, false, 1));
    }

    @Test
    void emptyInputsBecomeToNull() {
        assertNull(PersonRules.clean("   "));
        assertEquals("Anna", PersonRules.clean(" Anna "));
    }
}
