package ch.stvbero.platform.kern.termin;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Anzahl Spielerinnen pro Antwortstatus. */
@Schema(name = "Zaehler")
public record ZaehlerDto(int dabei, int nichtDabei, int vielleicht, int offen) {
}
