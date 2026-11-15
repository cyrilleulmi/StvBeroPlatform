package ch.stvbero.platform.kern.team;

import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.person.PersonKurz;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit-Tests der Teamregeln (PTF-04, PTF-07). */
class TeamRegelnTest {

    private static final PersonKurz PETRA = new PersonKurz(UUID.randomUUID(), "Petra", "Keller");
    private static final PersonKurz ANNA = new PersonKurz(UUID.randomUUID(), "Anna", "Muster");
    private static final PersonKurz SARA = new PersonKurz(UUID.randomUUID(), "Sara", "Brun");
    private static final PersonKurz MARC = new PersonKurz(UUID.randomUUID(), "Marc", "Huber");

    @Test
    void ptf04ZweiteAufnahmeImSelbenTeamWirdAbgelehnt() {
        List<TeamRegeln.Mitglied> team = List.of(new TeamRegeln.Mitglied(PETRA, Funktion.COACH));

        FachFehler f = assertThrows(FachFehler.class, () -> TeamRegeln.pruefeAufnahme(team, PETRA.id()));

        assertEquals(409, f.status());
        assertEquals("PTF-04", f.regel());
        assertTrue(f.getMessage().contains("Petra Keller ist schon in diesem Team (Coach)"));
    }

    @Test
    void ptf04AndereFunktionInAnderemTeamIstErlaubt() {
        List<TeamRegeln.Mitglied> anderesTeam = List.of(new TeamRegeln.Mitglied(ANNA, Funktion.SPIELERIN));
        assertDoesNotThrow(() -> TeamRegeln.pruefeAufnahme(anderesTeam, PETRA.id()));
    }

    @Test
    void ptf07CoachesErscheinenNieBeiDenSpielerinnen() {
        TeamRegeln.Aufteilung a = TeamRegeln.aufteilen(List.of(
                new TeamRegeln.Mitglied(PETRA, Funktion.COACH),
                new TeamRegeln.Mitglied(MARC, Funktion.COACH),
                new TeamRegeln.Mitglied(ANNA, Funktion.SPIELERIN),
                new TeamRegeln.Mitglied(SARA, Funktion.SPIELERIN)));

        assertEquals(List.of(MARC, PETRA), a.coaches());
        assertEquals(List.of(SARA, ANNA), a.spielerinnen(), "nach Nachname sortiert");
    }
}
