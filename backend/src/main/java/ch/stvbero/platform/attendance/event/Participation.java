package ch.stvbero.platform.attendance.event;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * How a person takes part in an event (TER-15): {@code EXPECTED} if she belongs to a team that is expected,
 * {@code OPTIONAL} if all her teams of the event are invited without obligation («freiwillig»). Optional people
 * respond if they like, are not reminded and do not count as «offen».
 */
@Schema(name = "Participation")
public enum Participation {
    EXPECTED, OPTIONAL;

    /** Participation of a member of {@code memberTeams}; {@code null} if she is in no team of the event. */
    public static Participation of(Collection<UUID> memberTeams, Collection<UUID> optionalTeams) {
        if (memberTeams.isEmpty()) {
            return null;
        }
        return optionalTeams.containsAll(memberTeams) ? OPTIONAL : EXPECTED;
    }

    /**
     * The optional teams of an event (TER-15): each one a team of the event, and at least one team stays expected.
     * {@code null}: none.
     */
    public static List<UUID> check(Collection<UUID> teams, Collection<UUID> optionalTeams) {
        if (optionalTeams == null || optionalTeams.isEmpty()) {
            return List.of();
        }
        List<UUID> optional = optionalTeams.stream().distinct().toList();
        if (!teams.containsAll(optional)) {
            throw ch.stvbero.platform.core.error.DomainError.invalidField("optionalTeamIds",
                    "Ein freiwilliges Team gehört nicht zu diesem Termin.");
        }
        if (optional.size() >= new java.util.HashSet<>(teams).size()) {
            throw ch.stvbero.platform.core.error.DomainError.invalidField("optionalTeamIds",
                    "Mindestens ein Team muss erwartet sein.");
        }
        return optional;
    }
}
