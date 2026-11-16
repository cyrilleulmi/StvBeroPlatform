package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.team.Funktion;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fachregeln zu Aufgebot und Spielen (AUF-01, AUF-02, AUF-15, AUF-22 bis AUF-28, SUH-06). */
class AufgebotRegelnTest {

    static final UUID ANNA = UUID.randomUUID();
    static final UUID KIM = UUID.randomUUID();
    static final UUID PETRA = UUID.randomUUID();

    @Test
    void auf02NurSpieltageHabenEinAufgebot() {
        FachFehler f = assertThrows(FachFehler.class, () -> AufgebotRegeln.pruefeSpieltag(TerminTyp.TRAINING));
        assertEquals(409, f.status());
        assertEquals("AUF-02", f.regel());
        assertThrows(FachFehler.class, () -> AufgebotRegeln.pruefeSpieltag(TerminTyp.ANLASS));
        assertDoesNotThrow(() -> AufgebotRegeln.pruefeSpieltag(TerminTyp.SPIELTAG));
    }

    @Test
    void rr03NurCoachesUndAdminsBearbeitenDasAufgebot() {
        assertTrue(AufgebotRegeln.darfBearbeiten(false, List.of(Funktion.COACH)));
        assertTrue(AufgebotRegeln.darfBearbeiten(true, List.of()));
        assertFalse(AufgebotRegeln.darfBearbeiten(false, List.of(Funktion.SPIELERIN)));
        FachFehler f = assertThrows(FachFehler.class, () -> AufgebotRegeln.pruefeDarfBearbeiten(false, List.of()));
        assertEquals(403, f.status());
    }

    @Test
    void auf22UndAuf28NurSpielerinnenDesTeamsSindAufbietbar() {
        Set<UUID> spielerinnen = Set.of(ANNA, KIM);
        assertDoesNotThrow(() -> AufgebotRegeln.pruefeAufbietbar(List.of(ANNA, KIM), spielerinnen));
        assertDoesNotThrow(() -> AufgebotRegeln.pruefeAufbietbar(List.of(), spielerinnen));
        FachFehler f = assertThrows(FachFehler.class, () -> AufgebotRegeln.pruefeAufbietbar(List.of(ANNA, PETRA), spielerinnen));
        assertEquals(422, f.status());
        assertEquals("AUF-28", f.regel());
        assertEquals("personIds", f.alsFehler().felder().get(0).feld());
    }

    @Test
    void auf23StatusUndVeroeffentlichen() {
        assertEquals(AufgebotStatus.KEINES, AufgebotRegeln.status(false, 0));
        assertEquals(AufgebotStatus.ENTWURF, AufgebotRegeln.status(false, 3));
        assertEquals(AufgebotStatus.VEROEFFENTLICHT, AufgebotRegeln.status(true, 3));
        assertEquals(AufgebotStatus.VEROEFFENTLICHT, AufgebotRegeln.status(true, 0));

        assertDoesNotThrow(() -> AufgebotRegeln.pruefeVeroeffentlichbar(AufgebotStatus.ENTWURF));
        FachFehler leer = assertThrows(FachFehler.class, () -> AufgebotRegeln.pruefeVeroeffentlichbar(AufgebotStatus.KEINES));
        assertEquals(409, leer.status());
        FachFehler zweimal = assertThrows(FachFehler.class,
                () -> AufgebotRegeln.pruefeVeroeffentlichbar(AufgebotStatus.VEROEFFENTLICHT));
        assertEquals(409, zweimal.status());
        assertEquals("AUF-23", zweimal.regel());
    }

    @Test
    void auf23EntwurfSehenNurCoaches() {
        assertEquals(AufgebotStatus.ENTWURF, AufgebotRegeln.sichtbarerStatus(AufgebotStatus.ENTWURF, true));
        assertEquals(AufgebotStatus.KEINES, AufgebotRegeln.sichtbarerStatus(AufgebotStatus.ENTWURF, false));
        assertEquals(AufgebotStatus.VEROEFFENTLICHT, AufgebotRegeln.sichtbarerStatus(AufgebotStatus.VEROEFFENTLICHT, false));
    }

    @Test
    void auf24MeinAufgebotErstNachDerVeroeffentlichung() {
        assertEquals(MeinAufgebot.NOCH_KEIN_AUFGEBOT, AufgebotRegeln.meinAufgebot(AufgebotStatus.KEINES, false));
        assertEquals(MeinAufgebot.NOCH_KEIN_AUFGEBOT, AufgebotRegeln.meinAufgebot(AufgebotStatus.ENTWURF, true));
        assertEquals(MeinAufgebot.AUFGEBOTEN, AufgebotRegeln.meinAufgebot(AufgebotStatus.VEROEFFENTLICHT, true));
        assertEquals(MeinAufgebot.NICHT_AUFGEBOTEN, AufgebotRegeln.meinAufgebot(AufgebotStatus.VEROEFFENTLICHT, false));
    }

    // ------------------------------------------------------------------ Spiele

    static final Instant SPIELTAG = Instant.parse("2026-11-22T08:30:00Z"); // So 22.11. 09:30 Zürich

    @Test
    void auf01SpieleLiegenAmTagDesSpieltags() {
        assertDoesNotThrow(() -> SpielRegeln.pruefeAmSpieltag(Instant.parse("2026-11-22T22:30:00Z"), SPIELTAG)); // 23:30
        FachFehler f = assertThrows(FachFehler.class,
                () -> SpielRegeln.pruefeAmSpieltag(Instant.parse("2026-11-22T23:30:00Z"), SPIELTAG)); // 00:30 am 23.11.
        assertEquals(400, f.status());
        assertEquals("anspielzeit", f.alsFehler().felder().get(0).feld());
    }

    @Test
    void suh06ImportierteFelderSindGeschuetztDasResultatNicht() {
        assertDoesNotThrow(() -> SpielRegeln.pruefeAenderbar(Quelle.SWISS_UNIHOCKEY, "UHC Musterdorf", SPIELTAG, true,
                "UHC Musterdorf", SPIELTAG, true));
        FachFehler f = assertThrows(FachFehler.class, () -> SpielRegeln.pruefeAenderbar(Quelle.SWISS_UNIHOCKEY,
                "UHC Musterdorf", SPIELTAG, true, "UHC Anders", SPIELTAG, true));
        assertEquals(409, f.status());
        assertEquals("SUH-06", f.regel());
        assertThrows(FachFehler.class, () -> SpielRegeln.pruefeAenderbar(Quelle.SWISS_UNIHOCKEY,
                "UHC Musterdorf", SPIELTAG, true, "UHC Musterdorf", SPIELTAG.plusSeconds(60), true));
        assertThrows(FachFehler.class, () -> SpielRegeln.pruefeAenderbar(Quelle.SWISS_UNIHOCKEY,
                "UHC Musterdorf", SPIELTAG, true, "UHC Musterdorf", SPIELTAG, false));
        assertDoesNotThrow(() -> SpielRegeln.pruefeAenderbar(Quelle.MANUELL, "A", SPIELTAG, true, "B",
                SPIELTAG.plusSeconds(60), false));
    }

    @Test
    void importierteSpieleNurLoeschbarWennSuSieNichtMehrLiefert() {
        assertDoesNotThrow(() -> SpielRegeln.pruefeLoeschbar(Quelle.MANUELL, false));
        assertDoesNotThrow(() -> SpielRegeln.pruefeLoeschbar(Quelle.SWISS_UNIHOCKEY, true));
        assertEquals(409, assertThrows(FachFehler.class,
                () -> SpielRegeln.pruefeLoeschbar(Quelle.SWISS_UNIHOCKEY, false)).status());
    }

    @Test
    void auf15ResultatAlsHeimZuGast() {
        assertTrue("5:3".matches(SpielRegeln.RESULTAT_MUSTER));
        assertTrue("10:12".matches(SpielRegeln.RESULTAT_MUSTER));
        assertFalse("5-3".matches(SpielRegeln.RESULTAT_MUSTER));
        assertFalse("123:1".matches(SpielRegeln.RESULTAT_MUSTER));
    }

    @Test
    void auf01SpieltagMitSpielenBleibtSpieltag() {
        FachFehler f = assertThrows(FachFehler.class,
                () -> TerminRegeln.pruefeTypWechsel(TerminTyp.SPIELTAG, TerminTyp.TRAINING, true));
        assertEquals(409, f.status());
        assertDoesNotThrow(() -> TerminRegeln.pruefeTypWechsel(TerminTyp.SPIELTAG, TerminTyp.TRAINING, false));
        assertDoesNotThrow(() -> TerminRegeln.pruefeTypWechsel(TerminTyp.TRAINING, TerminTyp.ANLASS, true));
    }

    @Test
    void abgesagterSpieltagNimmtKeineAenderungenAn() {
        assertEquals(409, assertThrows(FachFehler.class,
                () -> SpielRegeln.pruefeNichtAbgesagt(TerminStatus.ABGESAGT)).status());
        assertDoesNotThrow(() -> SpielRegeln.pruefeNichtAbgesagt(TerminStatus.GEPLANT));
    }
}
