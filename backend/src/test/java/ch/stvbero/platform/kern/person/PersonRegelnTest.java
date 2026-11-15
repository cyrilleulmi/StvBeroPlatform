package ch.stvbero.platform.kern.person;

import ch.stvbero.platform.kern.fehler.FachFehler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Unit-Tests der Personenregeln (RR-04). */
class PersonRegelnTest {

    @Test
    void rr04LetztemAdminKannDasRechtNichtEntzogenWerden() {
        FachFehler f = assertThrows(FachFehler.class, () -> PersonRegeln.pruefeAdminEntzug(true, false, 1));
        assertEquals(409, f.status());
        assertEquals("RR-04", f.regel());
    }

    @Test
    void rr04MitZweitemAdminGehtDerEntzug() {
        assertDoesNotThrow(() -> PersonRegeln.pruefeAdminEntzug(true, false, 2));
        assertDoesNotThrow(() -> PersonRegeln.pruefeAdminEntzug(true, true, 1));
        assertDoesNotThrow(() -> PersonRegeln.pruefeAdminEntzug(false, false, 1));
    }

    @Test
    void leereEingabenWerdenZuNull() {
        assertNull(PersonRegeln.bereinigen("   "));
        assertEquals("Anna", PersonRegeln.bereinigen(" Anna "));
    }
}
