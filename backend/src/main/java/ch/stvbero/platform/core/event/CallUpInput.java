package ch.stvbero.platform.core.event;

import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/** Auswahl des Aufgebots: die aufgebotenen Spielerinnen (AUF-21, AUF-22). */
@Schema(name = "CallUpInput")
public record CallUpInput(@NotNull List<@NotNull UUID> personIds) {
}
