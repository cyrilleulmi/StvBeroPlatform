package ch.stvbero.platform.attendance.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/** Attendance to save (PRS-03, PRS-04): status per player. */
@Schema(name = "AttendanceInput")
public record AttendanceInput(@NotNull List<@NotNull @Valid Entry> entries) {

    @Schema(name = "AttendanceInputEntry")
    public record Entry(@NotNull UUID personId, @NotNull AttendanceStatus status) {
    }
}
