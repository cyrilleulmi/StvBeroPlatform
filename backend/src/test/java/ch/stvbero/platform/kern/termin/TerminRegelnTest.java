package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.team.Funktion;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fachregeln zu Terminen und Serien ohne Datenbank (TER). */
class TerminRegelnTest {

    private static LocalDate d(int jahr, int monat, int tag) {
        return LocalDate.of(jahr, monat, tag);
    }

    @Test
    void ter05JederWochentagImZeitraum() {
        assertEquals(List.of(d(2026, 11, 17), d(2026, 11, 24), d(2026, 12, 1)),
                TerminRegeln.daten(DayOfWeek.TUESDAY, d(2026, 11, 16), d(2026, 12, 1), List.of()));
        // Beginn und Ende des Zeitraums zählen mit
        assertEquals(List.of(d(2026, 11, 17)),
                TerminRegeln.daten(DayOfWeek.TUESDAY, d(2026, 11, 17), d(2026, 11, 17), List.of()));
        assertEquals(List.of(), TerminRegeln.daten(DayOfWeek.MONDAY, d(2026, 11, 17), d(2026, 11, 22), List.of()));
    }

    @Test
    void ter07AusnahmenFehlen() {
        List<TerminRegeln.Zeitraum> ferien = List.of(new TerminRegeln.Zeitraum(d(2026, 12, 21), d(2027, 1, 3)));
        List<LocalDate> daten = TerminRegeln.daten(DayOfWeek.TUESDAY, d(2026, 12, 15), d(2027, 1, 12), ferien);
        assertEquals(List.of(d(2026, 12, 15), d(2027, 1, 5), d(2027, 1, 12)), daten);
        assertTrue(TerminRegeln.inAusnahme(d(2027, 1, 3), ferien));
        assertFalse(TerminRegeln.inAusnahme(d(2027, 1, 4), ferien));
    }

    @Test
    void ter07OhneAntwortenEntfernenMitAntwortenAbsagen() {
        assertEquals(TerminRegeln.Folge.ENTFERNEN, TerminRegeln.beiAusnahme(false));
        assertEquals(TerminRegeln.Folge.ABSAGEN, TerminRegeln.beiAusnahme(true));
    }

    @Test
    void ter05ZeitpunktMitSommerUndWinterzeit() {
        assertEquals(OffsetDateTime.parse("2026-10-20T18:30:00+02:00").toInstant(),
                TerminRegeln.zeitpunkt(d(2026, 10, 20), LocalTime.of(18, 30)));
        assertEquals(OffsetDateTime.parse("2026-11-17T18:30:00+01:00").toInstant(),
                TerminRegeln.zeitpunkt(d(2026, 11, 17), LocalTime.of(18, 30)));
    }

    @Test
    void ter09AntwortfristVorBeginn() {
        Instant beginn = OffsetDateTime.parse("2026-11-17T18:30:00+01:00").toInstant();
        assertEquals(OffsetDateTime.parse("2026-11-16T18:30:00+01:00").toInstant(), TerminRegeln.antwortfrist(beginn, 24));
        assertEquals(beginn, TerminRegeln.antwortfrist(beginn, 0));
    }

    @Test
    void ter10StandardfristVierundzwanzigStunden() {
        assertEquals(24, TerminRegeln.antwortfrist((Integer) null));
        assertEquals(48, TerminRegeln.antwortfrist(48));
    }

    @Test
    void ter11CoachEinesTeamsOderAdmin() {
        assertDoesNotThrow(() -> TerminRegeln.pruefeDarfErfassen(false, Set.of(Funktion.COACH)));
        assertDoesNotThrow(() -> TerminRegeln.pruefeDarfErfassen(false, Set.of(Funktion.COACH, Funktion.SPIELERIN)));
        assertDoesNotThrow(() -> TerminRegeln.pruefeDarfErfassen(true, Set.of()));
        FachFehler f = assertThrows(FachFehler.class, () -> TerminRegeln.pruefeDarfErfassen(false, Set.of(Funktion.SPIELERIN)));
        assertEquals(403, f.status());
        assertEquals(403, assertThrows(FachFehler.class, () -> TerminRegeln.pruefeDarfErfassen(false, Set.of())).status());
    }

    @Test
    void rr02LesenNurMitglieder() {
        assertDoesNotThrow(() -> TerminRegeln.pruefeDarfLesen(false, Set.of(Funktion.SPIELERIN)));
        assertDoesNotThrow(() -> TerminRegeln.pruefeDarfLesen(true, Set.of()));
        assertEquals(403, assertThrows(FachFehler.class, () -> TerminRegeln.pruefeDarfLesen(false, Set.of())).status());
    }

    @Test
    void ter06AbgesagtOderVorbeiNichtAenderbar() {
        Instant jetzt = Instant.parse("2026-11-16T05:00:00Z");
        assertDoesNotThrow(() -> TerminRegeln.pruefeAenderbar(TerminStatus.GEPLANT, jetzt.plusSeconds(60), jetzt));
        FachFehler abgesagt = assertThrows(FachFehler.class,
                () -> TerminRegeln.pruefeAenderbar(TerminStatus.ABGESAGT, jetzt.plusSeconds(60), jetzt));
        assertEquals(409, abgesagt.status());
        assertEquals("TER-06", abgesagt.regel());
        assertEquals(409, assertThrows(FachFehler.class,
                () -> TerminRegeln.pruefeAenderbar(TerminStatus.GEPLANT, jetzt, jetzt)).status());
    }

    @Test
    void ter06VerschobenNurBeiAndererBeginnzeit() {
        Instant a = Instant.parse("2026-11-19T18:00:00Z");
        assertFalse(TerminRegeln.verschoben(false, a, a));
        assertTrue(TerminRegeln.verschoben(false, a, a.plusSeconds(3600)));
        assertTrue(TerminRegeln.verschoben(true, a, a));
    }

    @Test
    void eingabenWerdenGeprueft() {
        Instant a = Instant.parse("2026-11-19T18:00:00Z");
        assertEquals(400, assertThrows(FachFehler.class, () -> TerminRegeln.pruefeZeit(a, a)).status());
        assertEquals(400, assertThrows(FachFehler.class, () -> TerminRegeln.pruefeTeams(TerminTyp.SPIELTAG, 2)).status());
        assertDoesNotThrow(() -> TerminRegeln.pruefeTeams(TerminTyp.TRAINING, 2));
        assertDoesNotThrow(() -> TerminRegeln.pruefeTeams(TerminTyp.SPIELTAG, 1));
        assertEquals(400, assertThrows(FachFehler.class, () -> TerminRegeln.pruefeNichtVorbei(a, a)).status());
        LocalTime acht = LocalTime.of(20, 0);
        assertEquals(400, assertThrows(FachFehler.class,
                () -> TerminRegeln.pruefeSerie(acht, acht, d(2026, 11, 1), d(2026, 12, 1))).status());
        assertEquals(400, assertThrows(FachFehler.class,
                () -> TerminRegeln.pruefeSerie(LocalTime.of(18, 0), acht, d(2026, 12, 1), d(2026, 11, 1))).status());
        assertEquals(400, assertThrows(FachFehler.class,
                () -> TerminRegeln.pruefeSerie(LocalTime.of(18, 0), acht, d(2026, 8, 1), d(2027, 8, 5))).status());
        assertDoesNotThrow(() -> TerminRegeln.pruefeSerie(LocalTime.of(18, 0), acht, d(2026, 8, 18), d(2027, 4, 27)));
        assertEquals(400, assertThrows(FachFehler.class,
                () -> TerminRegeln.pruefeAusnahme(d(2026, 12, 2), d(2026, 12, 1))).status());
    }

    @Test
    void ter08AbgleichNachSerienaenderung() {
        LocalDate a = d(2026, 11, 17);
        LocalDate b = d(2026, 11, 24);
        LocalDate c = d(2026, 12, 1);
        LocalDate e = d(2026, 12, 8);
        LocalDate f = d(2026, 12, 15);
        Map<LocalDate, TerminRegeln.Bestehend> bestehend = Map.of(
                a, new TerminRegeln.Bestehend(true, false),
                b, new TerminRegeln.Bestehend(false, false),   // abgesagt
                c, new TerminRegeln.Bestehend(true, true),     // einzeln geändert
                e, new TerminRegeln.Bestehend(true, false));
        TerminRegeln.Abgleich abgleich = TerminRegeln.abgleichen(bestehend, List.of(a, b, c, f));
        assertEquals(List.of(f), abgleich.erzeugen());
        assertEquals(List.of(a), abgleich.uebernehmen());
        assertEquals(List.of(e), abgleich.wegfallen());
    }

    @Test
    void zaehlerJedeSpielerinEinmal() {
        assertEquals(new ZaehlerDto(2, 1, 0, 3), TerminRegeln.zaehlen(List.of(AntwortStatus.DABEI, AntwortStatus.OFFEN,
                AntwortStatus.NICHT_DABEI, AntwortStatus.DABEI, AntwortStatus.OFFEN, AntwortStatus.OFFEN)));
    }
}
