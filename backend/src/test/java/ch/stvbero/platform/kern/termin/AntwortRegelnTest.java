package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.team.Funktion;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fachregeln zu Antworten (ANM-04, ANM-05, ANM-07, ANM-10, ANM-11, RR-06, TER-06). */
class AntwortRegelnTest {

    static final UUID TEAM_1 = UUID.randomUUID();
    static final UUID TEAM_2 = UUID.randomUUID();
    static final Instant FRIST = Instant.parse("2026-11-16T17:30:00Z");
    static final Instant VOR_FRIST = FRIST.minusSeconds(60);

    @Test
    void anm04VielleichtVerlangtKommentar() {
        FachFehler f = assertThrows(FachFehler.class, () -> AntwortRegeln.pruefeKommentar(AntwortStatus.VIELLEICHT, null));
        assertEquals(422, f.status());
        assertEquals("ANM-04", f.regel());
        assertEquals("kommentar", f.alsFehler().felder().get(0).feld());
        assertThrows(FachFehler.class, () -> AntwortRegeln.pruefeKommentar(AntwortStatus.VIELLEICHT, "   "));
        assertDoesNotThrow(() -> AntwortRegeln.pruefeKommentar(AntwortStatus.VIELLEICHT, "Komme später"));
    }

    @Test
    void anm05NichtDabeiOhneGrundErlaubt() {
        assertDoesNotThrow(() -> AntwortRegeln.pruefeKommentar(AntwortStatus.NICHT_DABEI, null));
        assertDoesNotThrow(() -> AntwortRegeln.pruefeKommentar(AntwortStatus.DABEI, null));
    }

    @Test
    void anm06OffenIstKeineAntwort() {
        assertEquals(400, assertThrows(FachFehler.class,
                () -> AntwortRegeln.pruefeKommentar(AntwortStatus.OFFEN, null)).status());
    }

    @Test
    void anm10WegVonDabeiNachFristIstKurzfristig() {
        assertTrue(AntwortRegeln.kurzfristig(AntwortStatus.DABEI, false, AntwortStatus.NICHT_DABEI, FRIST, FRIST));
        assertTrue(AntwortRegeln.kurzfristig(AntwortStatus.DABEI, false, AntwortStatus.VIELLEICHT, FRIST, FRIST.plusSeconds(3600)));
    }

    @Test
    void anm10VorDerFristNichtKurzfristig() {
        assertFalse(AntwortRegeln.kurzfristig(AntwortStatus.DABEI, false, AntwortStatus.NICHT_DABEI, FRIST, VOR_FRIST));
    }

    @Test
    void anm10OhneVorherDabeiNichtKurzfristig() {
        assertFalse(AntwortRegeln.kurzfristig(AntwortStatus.OFFEN, false, AntwortStatus.NICHT_DABEI, FRIST, FRIST));
        assertFalse(AntwortRegeln.kurzfristig(AntwortStatus.VIELLEICHT, false, AntwortStatus.NICHT_DABEI, FRIST, FRIST));
    }

    @Test
    void anm10MarkierungBleibtBisWiederDabei() {
        assertTrue(AntwortRegeln.kurzfristig(AntwortStatus.NICHT_DABEI, true, AntwortStatus.VIELLEICHT, FRIST, FRIST));
        assertTrue(AntwortRegeln.kurzfristig(AntwortStatus.NICHT_DABEI, true, AntwortStatus.NICHT_DABEI, FRIST, FRIST));
        assertFalse(AntwortRegeln.kurzfristig(AntwortStatus.NICHT_DABEI, true, AntwortStatus.DABEI, FRIST, FRIST));
    }

    @Test
    void ter06AbgesagtOderVorbeiNimmtKeineAntwort() {
        Instant jetzt = Instant.parse("2026-11-16T05:00:00Z");
        FachFehler abgesagt = assertThrows(FachFehler.class,
                () -> AntwortRegeln.pruefeOffen(TerminStatus.ABGESAGT, jetzt.plusSeconds(3600), jetzt));
        assertEquals(409, abgesagt.status());
        assertEquals(AntwortRegeln.ABGESAGT, abgesagt.getMessage());
        assertEquals(409, assertThrows(FachFehler.class,
                () -> AntwortRegeln.pruefeOffen(TerminStatus.GEPLANT, jetzt, jetzt)).status());
        assertDoesNotThrow(() -> AntwortRegeln.pruefeOffen(TerminStatus.GEPLANT, jetzt.plusSeconds(1), jetzt));
    }

    @Test
    void anm11CoachVertrittSpielerinnenDesEigenenTeams() {
        assertTrue(AntwortRegeln.darfStellvertreten(false, Map.of(TEAM_1, Funktion.COACH),
                Map.of(TEAM_1, Funktion.SPIELERIN)));
        // Lea: Spielerin in Team 1 und Team 2, die Coach von Team 2 darf auch
        assertTrue(AntwortRegeln.darfStellvertreten(false, Map.of(TEAM_2, Funktion.COACH),
                Map.of(TEAM_1, Funktion.SPIELERIN, TEAM_2, Funktion.SPIELERIN)));
    }

    @Test
    void rr06KeinStellvertretenFuerFremdeTeams() {
        assertFalse(AntwortRegeln.darfStellvertreten(false, Map.of(TEAM_2, Funktion.COACH),
                Map.of(TEAM_1, Funktion.SPIELERIN)));
        // Petra ist in Team 2 Spielerin: dort darf sie nicht für andere antworten
        assertFalse(AntwortRegeln.darfStellvertreten(false,
                Map.of(TEAM_1, Funktion.COACH, TEAM_2, Funktion.SPIELERIN), Map.of(TEAM_2, Funktion.SPIELERIN)));
        // Nicht für andere Coaches
        assertFalse(AntwortRegeln.darfStellvertreten(false, Map.of(TEAM_1, Funktion.COACH), Map.of(TEAM_1, Funktion.COACH)));
        // Spielerinnen gar nicht
        assertEquals(403, assertThrows(FachFehler.class, () -> AntwortRegeln.pruefeDarfStellvertreten(false,
                Map.of(TEAM_1, Funktion.SPIELERIN), Map.of(TEAM_1, Funktion.SPIELERIN))).status());
    }

    @Test
    void rr04AdminVertrittJedesMitglied() {
        assertTrue(AntwortRegeln.darfStellvertreten(true, Map.of(), Map.of(TEAM_1, Funktion.COACH)));
        assertFalse(AntwortRegeln.darfStellvertreten(true, Map.of(), Map.of()));
    }

    @Test
    void anm07KommentarNurFuerCoachesSelbstUndAdmins() {
        assertTrue(AntwortRegeln.kommentarSichtbar(false, false, Funktion.COACH));
        assertTrue(AntwortRegeln.kommentarSichtbar(false, true, Funktion.SPIELERIN));
        assertTrue(AntwortRegeln.kommentarSichtbar(true, false, null));
        assertFalse(AntwortRegeln.kommentarSichtbar(false, false, Funktion.SPIELERIN));
        assertFalse(AntwortRegeln.kommentarSichtbar(false, false, null));
    }

    @Test
    void anm01NurMitgliederAntwortenFuerSich() {
        assertEquals(403, assertThrows(FachFehler.class, () -> AntwortRegeln.pruefeDarfSelbstAntworten(false)).status());
        assertDoesNotThrow(() -> AntwortRegeln.pruefeDarfSelbstAntworten(true));
    }
}
