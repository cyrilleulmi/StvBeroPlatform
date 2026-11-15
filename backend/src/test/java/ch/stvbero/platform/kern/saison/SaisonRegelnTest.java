package ch.stvbero.platform.kern.saison;

import ch.stvbero.platform.kern.fehler.FachFehler;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit-Tests der Saisonregeln (SAI-01, SAI-02, SAI-04). */
class SaisonRegelnTest {

    private static final LocalDate VON_2025 = LocalDate.of(2025, 7, 1);
    private static final LocalDate VON_2026 = LocalDate.of(2026, 7, 1);
    private static final LocalDate VON_2027 = LocalDate.of(2027, 7, 1);

    private static final List<SaisonRegeln.Zeitraum> BESTEHENDE = List.of(
            new SaisonRegeln.Zeitraum("2025/26", VON_2025, LocalDate.of(2026, 6, 30)),
            new SaisonRegeln.Zeitraum("2026/27", VON_2026, LocalDate.of(2027, 6, 30)));

    @Test
    void sai02SaisonVorDerAktuellenIstArchiv() {
        assertTrue(SaisonRegeln.istArchiv(VON_2025, false, VON_2026));
    }

    @Test
    void sai02AktuelleUndKuenftigeSaisonSindKeinArchiv() {
        assertFalse(SaisonRegeln.istArchiv(VON_2026, true, VON_2026));
        assertFalse(SaisonRegeln.istArchiv(VON_2027, false, VON_2026));
        assertFalse(SaisonRegeln.istArchiv(VON_2025, false, null));
    }

    @Test
    void sai02ArchivIstNichtBeschreibbar() {
        FachFehler f = assertThrows(FachFehler.class, () -> SaisonRegeln.pruefeBeschreibbar(VON_2025, false, VON_2026));
        assertEquals(409, f.status());
        assertEquals("SAI-02", f.regel());
        assertDoesNotThrow(() -> SaisonRegeln.pruefeBeschreibbar(VON_2027, false, VON_2026));
    }

    @Test
    void neueSaisonNachDerLetztenIstGueltig() {
        assertDoesNotThrow(() -> SaisonRegeln.pruefeNeu(
                new SaisonRegeln.Zeitraum("2027/28", VON_2027, LocalDate.of(2028, 6, 30)), BESTEHENDE));
    }

    @Test
    void endeVorBeginnIstUngueltig() {
        FachFehler f = assertThrows(FachFehler.class, () -> SaisonRegeln.pruefeNeu(
                new SaisonRegeln.Zeitraum("2027/28", VON_2027, VON_2027.minusDays(1)), BESTEHENDE));
        assertEquals(400, f.status());
        assertEquals("bis", f.alsFehler().felder().get(0).feld());
    }

    @Test
    void nameMussEindeutigSein() {
        FachFehler f = assertThrows(FachFehler.class, () -> SaisonRegeln.pruefeNeu(
                new SaisonRegeln.Zeitraum(" 2026/27 ", VON_2027, LocalDate.of(2028, 6, 30)), BESTEHENDE));
        assertEquals(409, f.status());
    }

    @Test
    void zeitraumDarfSichNichtUeberschneiden() {
        FachFehler f = assertThrows(FachFehler.class, () -> SaisonRegeln.pruefeNeu(
                new SaisonRegeln.Zeitraum("Zwischen", LocalDate.of(2027, 6, 30), LocalDate.of(2028, 6, 30)), BESTEHENDE));
        assertEquals(409, f.status());
        assertTrue(f.getMessage().contains("2026/27"));
    }
}
