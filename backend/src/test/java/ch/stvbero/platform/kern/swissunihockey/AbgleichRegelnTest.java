package ch.stvbero.platform.kern.swissunihockey;

import ch.stvbero.platform.kern.uhr.Uhr;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fachregeln des Abgleichs (SUH-04, SUH-05, SUH-07, BEN-06), ohne Datenbank. */
class AbgleichRegelnTest {

    private static Instant am(int monat, int tag, int stunde, int minute) {
        return LocalDateTime.of(2026, monat, tag, stunde, minute).atZone(Uhr.ZONE).toInstant();
    }

    private static SuSpiel spiel(int id, Instant zeit, String heim, String gast, String halle) {
        return new SuSpiel(id, zeit, heim, gast, halle, null, null, null, null, null);
    }

    @Test
    void eigenesTeamKommtInJedemSpielVor() {
        List<SuSpiel> spiele = List.of(
                spiel(1, am(11, 22, 9, 30), "STV Beromünster I", "UHC Musterdorf", "Halle"),
                spiel(2, am(11, 22, 11, 45), "Unihockey Beispielstadt", "STV Beromünster I", "Halle"));
        assertEquals("stv beromünster i", AbgleichRegeln.eigenesTeam(spiele, "Team 1"));

        AbgleichRegeln.Seite heim = AbgleichRegeln.seite(spiele.get(0), "stv beromünster i");
        assertEquals("UHC Musterdorf", heim.gegner());
        assertTrue(heim.heim());
        AbgleichRegeln.Seite auswaerts = AbgleichRegeln.seite(spiele.get(1), "stv beromünster i");
        assertEquals("Unihockey Beispielstadt", auswaerts.gegner());
        assertFalse(auswaerts.heim());
    }

    @Test
    void beiNurEinemSpielEntscheidetDerNameInDerApp() {
        List<SuSpiel> eines = List.of(spiel(1, am(11, 22, 11, 45), "Unihockey Beispielstadt", "Team 1", "Halle"));
        assertEquals("team 1", AbgleichRegeln.eigenesTeam(eines, "Team 1"));
        assertFalse(AbgleichRegeln.seite(eines.get(0), "team 1").heim());
        assertNull(AbgleichRegeln.eigenesTeam(List.of(), "Team 1"));
    }

    @Test
    void suh04GleicherTagUndGleicheHalleErgebenEinenSpieltag() {
        Map<AbgleichRegeln.Schluessel, List<SuSpiel>> gruppen = AbgleichRegeln.gruppieren(List.of(
                spiel(3, am(11, 22, 14, 0), "A", "B", "Turnhalle Weid"),
                spiel(2, am(11, 22, 11, 45), "A", "C", "Sporthalle  Seefeld"),
                spiel(1, am(11, 22, 9, 30), "A", "D", "sporthalle seefeld"),
                spiel(4, am(11, 29, 9, 30), "A", "D", "Sporthalle Seefeld")));

        assertEquals(3, gruppen.size());
        List<List<SuSpiel>> werte = List.copyOf(gruppen.values());
        assertEquals(List.of(1, 2), werte.get(0).stream().map(SuSpiel::suSpielId).toList(),
                "Gleiche Halle trotz Gross-/Kleinschreibung und Leerzeichen, nach Anspielzeit");
        assertEquals(List.of(3), werte.get(1).stream().map(SuSpiel::suSpielId).toList(), "Andere Halle");
        assertEquals(List.of(4), werte.get(2).stream().map(SuSpiel::suSpielId).toList(), "Anderer Tag");
        assertEquals(new AbgleichRegeln.Schluessel(LocalDate.of(2026, 11, 22), "sporthalle seefeld"),
                gruppen.keySet().iterator().next());
    }

    @Test
    void zeitraumVonErsterAnspielzeitBisLetzteMitSpieldauer() {
        AbgleichRegeln.Zeitraum z = AbgleichRegeln.zeitraum(List.of(am(11, 22, 11, 45), am(11, 22, 9, 30)));
        assertEquals(am(11, 22, 9, 30), z.beginn());
        assertEquals(am(11, 22, 13, 0), z.ende(), "Wie Termin 04 im Seed");
    }

    @Test
    void nurSpieleDerSaison() {
        assertTrue(AbgleichRegeln.inSaison(am(11, 22, 9, 30), LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30)));
        assertFalse(AbgleichRegeln.inSaison(am(6, 20, 9, 30), LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30)));
    }

    @Test
    void suh07MapsLink() {
        SuSpiel mitLink = new SuSpiel(1, am(11, 22, 9, 30), "A", "B", "Halle", "Ort", "https://maps.example/x", 47.0,
                8.0, null);
        assertEquals("https://maps.example/x", AbgleichRegeln.mapsUrl(mitLink));
        SuSpiel mitKoordinaten = new SuSpiel(1, am(11, 22, 9, 30), "A", "B", "Halle", "Ort", null, 47.2141, 8.1662,
                null);
        assertEquals("https://www.google.com/maps/search/?api=1&query=47.2141%2C8.1662",
                AbgleichRegeln.mapsUrl(mitKoordinaten));
        SuSpiel ohne = new SuSpiel(1, am(11, 22, 9, 30), "A", "B", "Sporthalle Seefeld", "Musterdorf", null, null,
                null, null);
        assertEquals("https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf",
                AbgleichRegeln.mapsUrl(ohne));
    }

    @Test
    void suh05WesentlicheAenderungen() {
        AbgleichRegeln.Stand alt = new AbgleichRegeln.Stand("Unihockey Beispielstadt", am(11, 22, 11, 45), false,
                "Sporthalle Seefeld");
        assertFalse(AbgleichRegeln.wesentlichGeaendert(alt, alt));
        assertFalse(AbgleichRegeln.wesentlichGeaendert(alt, new AbgleichRegeln.Stand("Unihockey Beispielstadt",
                am(11, 22, 11, 45), false, "sporthalle  seefeld")), "Schreibweise der Halle zählt nicht");

        AbgleichRegeln.Stand spaeter = new AbgleichRegeln.Stand("Unihockey Beispielstadt", am(11, 22, 12, 15), false,
                "Sporthalle Seefeld");
        assertTrue(AbgleichRegeln.wesentlichGeaendert(alt, spaeter));
        assertEquals("Spiel gegen Unihockey Beispielstadt neu um 12:15", AbgleichRegeln.aenderung(alt, spaeter));

        AbgleichRegeln.Stand andererTag = new AbgleichRegeln.Stand("UHC Drei", am(11, 21, 16, 0), true,
                "Turnhalle Weid");
        assertEquals("Spiel um 11:45 neu am Sa 21.11. 16:00, neu in Turnhalle Weid, neu gegen UHC Drei, "
                + "neu als Heimspiel", AbgleichRegeln.aenderung(alt, andererTag));
    }

    @Test
    void ben06Text() {
        assertEquals("Spieltag So 22.11. wurde geändert: Spiel gegen Unihockey Beispielstadt neu um 12:15.",
                AbgleichRegeln.textSpielGeaendert("Spieltag", am(11, 22, 9, 30),
                        List.of("Spiel gegen Unihockey Beispielstadt neu um 12:15")));
    }
}
