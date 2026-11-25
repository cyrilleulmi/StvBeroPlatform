package ch.stvbero.platform.attendance.event;

/** Response of a person to an event. Without a response the status is PENDING (ANM). */
public enum ResponseStatus {
    PENDING, ATTENDING, DECLINED, MAYBE
}
