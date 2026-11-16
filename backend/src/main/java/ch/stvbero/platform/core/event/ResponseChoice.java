package ch.stvbero.platform.core.event;

/** Antworten, die eine Person geben kann. «Offen» ist keine Antwort, sondern das Fehlen einer (ANM-03, ANM-06). */
public enum ResponseChoice {
    ATTENDING, DECLINED, MAYBE;

    public ResponseStatus status() {
        return ResponseStatus.valueOf(name());
    }
}
