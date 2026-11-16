package ch.stvbero.platform.core.event;

/** Antwort einer Person auf einen Termin. Ohne Antwort ist der Status OFFEN (ANM). */
public enum ResponseStatus {
    PENDING, ATTENDING, DECLINED, MAYBE
}
