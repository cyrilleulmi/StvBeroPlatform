package ch.stvbero.platform.swissunihockey;

/** swiss unihockey is not reachable or delivers unexpected data (SUH-09). The sync then changes nothing. */
public class SuUnavailable extends RuntimeException {

    public SuUnavailable(String message) {
        super(message);
    }

    public SuUnavailable(String message, Throwable cause) {
        super(message, cause);
    }
}
