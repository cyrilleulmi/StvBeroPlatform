package ch.stvbero.platform.core.event;

/** Responses a person can give. PENDING is not a response but the absence of one (ANM-03, ANM-06). */
public enum ResponseChoice {
    ATTENDING, DECLINED, MAYBE;

    public ResponseStatus status() {
        return ResponseStatus.valueOf(name());
    }
}
