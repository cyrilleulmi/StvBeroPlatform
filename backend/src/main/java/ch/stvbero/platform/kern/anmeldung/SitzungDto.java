package ch.stvbero.platform.kern.anmeldung;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Sitzung")
public record SitzungDto(boolean angemeldet) {
}
