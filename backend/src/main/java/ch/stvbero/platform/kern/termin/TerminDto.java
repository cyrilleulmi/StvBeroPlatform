package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.team.TeamDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Termin mit allen Angaben (TER-04). Spiele, Aufgebot und eigener Aufgebotsstatus gibt es nur bei
 * Spieltagen; sie kommen mit F-05 (bis dahin: keine Spiele, kein Aufgebot).
 */
@Schema(name = "Termin")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TerminDto(UUID id, TerminTyp typ, String titel, OffsetDateTime beginn, OffsetDateTime ende,
                        TerminStatus status, String absagegrund, boolean verschoben, List<TeamDto> teams,
                        OrtDto ort, OffsetDateTime antwortfrist, AntwortDto meineAntwort, ZaehlerDto zaehler,
                        UUID serieId, String hinweise, int antwortfristStunden, TreffpunktDto treffpunkt,
                        List<SpielDto> spiele, AufgebotStatus aufgebotStatus, MeinAufgebot meinAufgebot,
                        boolean darfBearbeiten) {
}
