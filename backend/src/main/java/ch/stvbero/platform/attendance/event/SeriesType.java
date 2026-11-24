package ch.stvbero.platform.attendance.event;

/** Types a series can create. Match days are created one by one (AUF) or come from swiss unihockey (SUH). */
public enum SeriesType {
    PRACTICE, OTHER;

    public EventType eventType() {
        return EventType.valueOf(name());
    }
}
