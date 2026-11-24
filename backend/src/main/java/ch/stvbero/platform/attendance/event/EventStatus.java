package ch.stvbero.platform.attendance.event;

/** Stored state of an event. Whether it is over follows from its end and the backend clock. */
public enum EventStatus {
    PLANNED, CANCELLED
}
