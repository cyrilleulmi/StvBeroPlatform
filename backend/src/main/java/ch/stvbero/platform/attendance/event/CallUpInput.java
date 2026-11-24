package ch.stvbero.platform.attendance.event;

import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/** Selection of the call-up: the called-up players (AUF-21, AUF-22). */
@Schema(name = "CallUpInput")
public record CallUpInput(@NotNull List<@NotNull UUID> personIds) {
}
