package ch.stvbero.platform.kern.termin;

/** Antworten, die eine Person geben kann. «Offen» ist keine Antwort, sondern das Fehlen einer (ANM-03, ANM-06). */
public enum AntwortWahl {
    DABEI, NICHT_DABEI, VIELLEICHT;

    public AntwortStatus status() {
        return AntwortStatus.valueOf(name());
    }
}
