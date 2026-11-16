package ch.stvbero.platform.kern.swissunihockey;

/** swiss unihockey ist nicht erreichbar oder liefert unerwartete Daten (SUH-09). Der Abgleich ändert dann nichts. */
public class SuNichtErreichbar extends RuntimeException {

    public SuNichtErreichbar(String meldung) {
        super(meldung);
    }

    public SuNichtErreichbar(String meldung, Throwable ursache) {
        super(meldung, ursache);
    }
}
