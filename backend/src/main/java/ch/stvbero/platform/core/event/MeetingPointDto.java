package ch.stvbero.platform.core.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;

/** Time and place where the team meets before a match day (AUF-12). */
@Schema(name = "MeetingPoint")
public record MeetingPointDto(@NotNull OffsetDateTime time, @NotBlank @Size(max = 120) String location) {
}
