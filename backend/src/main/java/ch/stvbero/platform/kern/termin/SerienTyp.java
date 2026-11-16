package ch.stvbero.platform.kern.termin;

/** Typen, die eine Serie erzeugen kann. Spieltage kommen einzeln (AUF) oder aus swiss unihockey (SUH). */
public enum SerienTyp {
    TRAINING, ANLASS;

    public TerminTyp terminTyp() {
        return TerminTyp.valueOf(name());
    }
}
