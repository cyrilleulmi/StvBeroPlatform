package ch.stvbero.platform.core.swissunihockey;

/** swiss unihockey ist nicht erreichbar oder liefert unerwartete Daten (SUH-09). Der Abgleich ändert dann nichts. */
public class SuUnavailable extends RuntimeException {

    public SuUnavailable(String message) {
        super(message);
    }

    public SuUnavailable(String message, Throwable cause) {
        super(message, cause);
    }
}
