package ch.stvbero.platform.attendance.event;

import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/** The people with a special permission; replaces the previous ones (RR-11). Empty: none. */
@Schema(name = "EventGrantsInput")
public record EventGrantsInput(
        @Schema(required = true) @NotNull(message = "Bitte wählen.") List<@NotNull UUID> personIds) {
}
