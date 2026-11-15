package ch.stvbero.platform.kern.team;

import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.person.PersonKurz;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Fachregeln zu Teams und Teamzugehörigkeiten. Ohne Datenbank, damit sie als Unit-Test prüfbar sind. */
public final class TeamRegeln {

    private TeamRegeln() {
    }

    /** Eine bestehende Zugehörigkeit im Team (in der Saison). */
    public record Mitglied(PersonKurz person, Funktion funktion) {
    }

    /** Coaches und Spielerinnen getrennt, je nach Nachname, dann Vorname sortiert. */
    public record Aufteilung(List<PersonKurz> coaches, List<PersonKurz> spielerinnen) {
    }

    /** Pro Person und Team genau eine Funktion (PTF-04): eine zweite Aufnahme wird abgelehnt. */
    public static void pruefeAufnahme(Collection<Mitglied> imTeam, UUID personId) {
        Optional<Mitglied> schon = imTeam.stream().filter(m -> m.person().id().equals(personId)).findFirst();
        if (schon.isPresent()) {
            Mitglied m = schon.get();
            throw FachFehler.konflikt("Schon im Team",
                    m.person().vorname() + " " + m.person().nachname() + " ist schon in diesem Team ("
                            + bezeichnung(m.funktion()) + "). Pro Team gibt es nur eine Funktion.", "PTF-04");
        }
    }

    /**
     * Coaches erscheinen nie bei den Spielerinnen (PTF-07). Nur die Funktion SPIELERIN zählt als
     * Spielerin; weitere Funktionen (PTF-06) stehen weder hier noch dort.
     */
    public static Aufteilung aufteilen(Collection<Mitglied> mitglieder) {
        List<PersonKurz> coaches = mitglieder.stream().filter(m -> m.funktion() == Funktion.COACH)
                .map(Mitglied::person).sorted(PersonKurz.REIHENFOLGE).toList();
        List<PersonKurz> spielerinnen = mitglieder.stream().filter(m -> m.funktion() == Funktion.SPIELERIN)
                .map(Mitglied::person).sorted(PersonKurz.REIHENFOLGE).toList();
        return new Aufteilung(coaches, spielerinnen);
    }

    static String bezeichnung(Funktion f) {
        return switch (f) {
            case SPIELERIN -> "Spielerin";
            case COACH -> "Coach";
        };
    }
}
