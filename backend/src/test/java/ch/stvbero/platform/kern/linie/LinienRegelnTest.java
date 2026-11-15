package ch.stvbero.platform.kern.linie;

import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.person.PersonKurz;
import ch.stvbero.platform.kern.team.Funktion;
import ch.stvbero.platform.kern.team.GruppenArt;
import ch.stvbero.platform.kern.team.TeamDetailDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Unit-Tests der Linienregeln (LIN-02, LIN-04, LIN-05, LIN-06, LIN-07, LIN-09, LIN-10). */
class LinienRegelnTest {

    private static final UUID L1 = UUID.randomUUID();
    private static final UUID L2 = UUID.randomUUID();
    private static final UUID L3 = UUID.randomUUID();
    private static final UUID GOALIES = UUID.randomUUID();

    private static final LinienRegeln.GruppeInfo LINIE_1 = new LinienRegeln.GruppeInfo(L1, GruppenArt.LINIE, "Linie 1", 1);
    private static final LinienRegeln.GruppeInfo LINIE_2 = new LinienRegeln.GruppeInfo(L2, GruppenArt.LINIE, "Linie 2", 2);
    private static final LinienRegeln.GruppeInfo GOALIE_GRUPPE = new LinienRegeln.GruppeInfo(GOALIES, GruppenArt.GOALIES, "Goalies", null);

    private static final PersonKurz ANNA = new PersonKurz(UUID.randomUUID(), "Anna", "Muster");
    private static final PersonKurz MIA = new PersonKurz(UUID.randomUUID(), "Mia", "Frei");
    private static final PersonKurz JANA = new PersonKurz(UUID.randomUUID(), "Jana", "Wyss");
    private static final PersonKurz KIM = new PersonKurz(UUID.randomUUID(), "Kim", "Baumann");

    @Test
    void lin02NeueZuweisungErsetztDieBisherige() {
        assertEquals(Set.of(L2), LinienRegeln.zuweisen(List.of(L1), L2));
        assertEquals(Set.of(L1), LinienRegeln.zuweisen(List.of(L1), L1));
        assertEquals(Set.of(GOALIES), LinienRegeln.zuweisen(List.of(), GOALIES));
        assertEquals(1, LinienRegeln.HOECHSTENS_GRUPPEN_PRO_TEAM);
    }

    @Test
    void lin06OhneLinieEntferntJedeZuweisung() {
        assertEquals(Set.of(), LinienRegeln.zuweisen(List.of(L1), null));
    }

    @Test
    void lin04MehrereBisherigeWerdenAufDieGrenzeGekuerzt() {
        // Falls einmal mehrere Zuweisungen bestehen (Regel später gelockert), bleibt nur die neueste.
        assertEquals(Set.of(L3), LinienRegeln.zuweisen(List.of(L1, L2), L3));
    }

    @Test
    void lin10NurSpielerinnenSindZuweisbar() {
        assertDoesNotThrow(() -> LinienRegeln.pruefeZuweisbar(ANNA, Funktion.SPIELERIN));
        FachFehler f = assertThrows(FachFehler.class, () -> LinienRegeln.pruefeZuweisbar(ANNA, Funktion.COACH));
        assertEquals(409, f.status());
        assertEquals("LIN-10", f.regel());
        assertThrows(FachFehler.class, () -> LinienRegeln.pruefeZuweisbar(ANNA, null));
    }

    @Test
    void lin05GoaliesLassenSichNichtLoeschen() {
        FachFehler f = assertThrows(FachFehler.class, () -> LinienRegeln.pruefeLinie(GOALIE_GRUPPE, "löschen"));
        assertEquals(409, f.status());
        assertEquals("LIN-05", f.regel());
        assertDoesNotThrow(() -> LinienRegeln.pruefeLinie(LINIE_1, "löschen"));
    }

    @Test
    void lin07NurCoachOderAdminDarfBearbeiten() {
        assertDoesNotThrow(() -> LinienRegeln.pruefeDarfBearbeiten(false, Funktion.COACH));
        assertDoesNotThrow(() -> LinienRegeln.pruefeDarfBearbeiten(true, null));
        assertEquals(403, assertThrows(FachFehler.class,
                () -> LinienRegeln.pruefeDarfBearbeiten(false, Funktion.SPIELERIN)).status());
        assertEquals(403, assertThrows(FachFehler.class,
                () -> LinienRegeln.pruefeDarfBearbeiten(false, null)).status());
    }

    @Test
    void lesenNurMitgliederUndAdmins() {
        assertDoesNotThrow(() -> LinienRegeln.pruefeDarfLesen(false, Funktion.SPIELERIN));
        assertDoesNotThrow(() -> LinienRegeln.pruefeDarfLesen(true, null));
        assertEquals(403, assertThrows(FachFehler.class, () -> LinienRegeln.pruefeDarfLesen(false, null)).status());
    }

    @Test
    void lin09NameProTeamEindeutigOhneGrossKlein() {
        List<LinienRegeln.GruppeInfo> gruppen = List.of(LINIE_1, LINIE_2, GOALIE_GRUPPE);
        FachFehler f = assertThrows(FachFehler.class, () -> LinienRegeln.pruefeName(gruppen, " linie 2 ", null));
        assertEquals("LIN-09", f.regel());
        assertThrows(FachFehler.class, () -> LinienRegeln.pruefeName(gruppen, "GOALIES", null));
        assertDoesNotThrow(() -> LinienRegeln.pruefeName(gruppen, "Linie 3", null));
        // Der eigene Name ist nicht «vergeben»
        assertDoesNotThrow(() -> LinienRegeln.pruefeName(gruppen, "Linie 2", L2));
    }

    @Test
    void lin09VerschiebenNummeriertNeu() {
        List<UUID> linien = List.of(L1, L2, L3);
        assertEquals(List.of(L3, L1, L2), LinienRegeln.verschieben(linien, L3, 1));
        assertEquals(List.of(L2, L1, L3), LinienRegeln.verschieben(linien, L1, 2));
        assertEquals(List.of(L2, L3, L1), LinienRegeln.verschieben(linien, L1, 99));
        assertEquals(List.of(L1, L2, L3), LinienRegeln.verschieben(linien, L1, 0));
    }

    @Test
    void anzeigeLinienNachReihenfolgeDannGoaliesDannOhneLinie() {
        LinienRegeln.GruppeInfo vorne = new LinienRegeln.GruppeInfo(L3, GruppenArt.LINIE, "Zuerst", 1);
        LinienRegeln.GruppeInfo hinten = new LinienRegeln.GruppeInfo(L1, GruppenArt.LINIE, "Linie 1", 2);
        TeamDetailDto.Gruppeneinteilung e = LinienRegeln.einteilen(
                List.of(GOALIE_GRUPPE, hinten, vorne),
                List.of(ANNA, MIA, JANA, KIM),
                Map.of(ANNA.id(), Set.of(L1), MIA.id(), Set.of(L1), JANA.id(), Set.of(GOALIES)));

        assertEquals(List.of("Zuerst", "Linie 1", "Goalies"), e.gruppen().stream().map(TeamDetailDto.GruppeMitSpielerinnen::name).toList());
        // Leere Gruppen bleiben in der Einteilung (Linien-Screen zeigt sie)
        assertEquals(List.of(), e.gruppen().get(0).spielerinnen());
        // Innerhalb einer Gruppe nach Nachname
        assertEquals(List.of(MIA, ANNA), e.gruppen().get(1).spielerinnen());
        assertEquals(List.of(JANA), e.gruppen().get(2).spielerinnen());
        assertEquals(List.of(KIM), e.ohneLinie());
    }

    @Test
    void lin06ZuweisungZuUnbekannterGruppeZaehltAlsOhneLinie() {
        TeamDetailDto.Gruppeneinteilung e = LinienRegeln.einteilen(List.of(LINIE_1), List.of(ANNA),
                Map.of(ANNA.id(), Set.of(UUID.randomUUID())));
        assertEquals(List.of(ANNA), e.ohneLinie());
    }
}
