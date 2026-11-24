package ch.stvbero.platform.attendance.event;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Recorded attendance of a player at an event (PRS-03): «anwesend», «abwesend», «entschuldigt». */
@Schema(name = "AttendanceStatus")
public enum AttendanceStatus {
    PRESENT, ABSENT, EXCUSED
}
