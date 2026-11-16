package ch.stvbero.platform.core.team;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.person.PersonSummary;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Fachregeln zu Teams und Teamzugehörigkeiten. Ohne Datenbank, damit sie als Unit-Test prüfbar sind. */
public final class TeamRules {

    private TeamRules() {
    }

    /** Eine bestehende Zugehörigkeit im Team (in der Saison). */
    public record Member(PersonSummary person, TeamRole teamRole) {
    }

    /** Coaches und Spielerinnen getrennt, je nach Nachname, dann Vorname sortiert. */
    public record Split(List<PersonSummary> coaches, List<PersonSummary> players) {
    }

    /** Pro Person und Team genau eine Funktion (PTF-04): eine zweite Aufnahme wird abgelehnt. */
    public static void checkCanAdd(Collection<Member> inTeam, UUID personId) {
        Optional<Member> already = inTeam.stream().filter(m -> m.person().id().equals(personId)).findFirst();
        if (already.isPresent()) {
            Member m = already.get();
            throw DomainError.conflict("Schon im Team",
                    m.person().firstName() + " " + m.person().lastName() + " ist schon in diesem Team ("
                            + label(m.teamRole()) + "). Pro Team gibt es nur eine Funktion.", "PTF-04");
        }
    }

    /**
     * Coaches erscheinen nie bei den Spielerinnen (PTF-07). Nur die Funktion SPIELERIN zählt als
     * Spielerin; weitere Funktionen (PTF-06) stehen weder hier noch dort.
     */
    public static Split split(Collection<Member> members) {
        List<PersonSummary> coaches = members.stream().filter(m -> m.teamRole() == TeamRole.COACH)
                .map(Member::person).sorted(PersonSummary.ORDER).toList();
        List<PersonSummary> players = members.stream().filter(m -> m.teamRole() == TeamRole.PLAYER)
                .map(Member::person).sorted(PersonSummary.ORDER).toList();
        return new Split(coaches, players);
    }

    static String label(TeamRole f) {
        return switch (f) {
            case PLAYER -> "Spielerin";
            case COACH -> "Coach";
        };
    }
}
