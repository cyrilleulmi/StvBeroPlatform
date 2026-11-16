package ch.stvbero.platform.core.event;

/** Typen, die eine Serie erzeugen kann. Spieltage kommen einzeln (AUF) oder aus swiss unihockey (SUH). */
public enum SeriesType {
    PRACTICE, OTHER;

    public EventType eventType() {
        return EventType.valueOf(name());
    }
}
