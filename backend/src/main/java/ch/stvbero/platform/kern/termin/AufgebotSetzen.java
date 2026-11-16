package ch.stvbero.platform.kern.termin;

import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/** Auswahl des Aufgebots: die aufgebotenen Spielerinnen (AUF-21, AUF-22). */
@Schema(name = "AufgebotSetzen")
public record AufgebotSetzen(@NotNull List<@NotNull UUID> personIds) {
}
