package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.person.PersonKurz;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Übersicht eines Termins (ANM-20 bis ANM-32): Teams nach Name, darin Linien, Goalies und «Ohne Linie»,
 * dazu der Coach-Block und die Zähler. Kommentare nur für Berechtigte (ANM-07).
 */
@Schema(name = "Uebersicht")
public record UebersichtDto(UUID terminId, ZaehlerDto total, List<TeamBlock> teams, List<CoachEintrag> coaches) {

    @Schema(name = "UebersichtTeamName")
    public record TeamName(UUID id, String name) {
    }

    @Schema(name = "UebersichtTeam")
    public record TeamBlock(TeamName team, ZaehlerDto zaehler, List<Gruppe> gruppen) {
    }

    @Schema(name = "UebersichtGruppenArt")
    public enum Art {
        LINIE, GOALIES, OHNE_LINIE
    }

    @Schema(name = "UebersichtGruppe")
    public record Gruppe(UUID gruppeId, Art art, String name, List<Eintrag> eintraege) {
    }

    @Schema(name = "UebersichtEintrag")
    public record Eintrag(PersonKurz person, AntwortDto antwort) {
    }

    @Schema(name = "UebersichtCoach")
    public record CoachEintrag(PersonKurz person, TeamName team, AntwortDto antwort) {
    }
}
