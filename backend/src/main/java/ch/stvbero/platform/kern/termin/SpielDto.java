package ch.stvbero.platform.kern.termin;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Spiel eines Spieltags (AUF). Spiele kommen mit F-05; bis dahin ist die Liste leer. */
@Schema(name = "Spiel")
public record SpielDto(UUID id, String gegner, OffsetDateTime anspielzeit, boolean heim, String resultat,
                       Quelle quelle, Integer suSpielId, boolean beiSuNichtMehrVorhanden) {
}
