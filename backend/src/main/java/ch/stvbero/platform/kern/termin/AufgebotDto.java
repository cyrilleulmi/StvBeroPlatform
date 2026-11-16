package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.person.PersonKurz;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Aufgebot eines Spieltags für die Coach: alle Spielerinnen des Teams, gruppiert wie in der Übersicht, mit
 * Antwortstatus und Auswahl (AUF-21, AUF-22). Coaches stehen nicht darin (AUF-28).
 */
@Schema(name = "Aufgebot")
public record AufgebotDto(UUID terminId, AufgebotStatus status, OffsetDateTime veroeffentlichtAm,
                          int anzahlAufgeboten, List<Gruppe> gruppen) {

    @Schema(name = "AufgebotGruppe")
    public record Gruppe(UUID gruppeId, UebersichtDto.Art art, String name, List<Eintrag> eintraege) {
    }

    @Schema(name = "AufgebotEintrag")
    public record Eintrag(PersonKurz person, AntwortDto antwort, boolean aufgeboten) {
    }
}
