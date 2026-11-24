package ch.stvbero.platform.attendance.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reason for a cancellation (TER-06). */
public record CancellationDto(@NotBlank @Size(max = 200) String reason) {
}
