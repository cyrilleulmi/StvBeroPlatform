package ch.stvbero.platform.kern.fehler;

/** Fehler, der als Problem Details an den Client geht. */
public class FachFehler extends RuntimeException {

    private final int status;
    private final String titel;
    private final String regel;

    public FachFehler(int status, String titel, String detail, String regel) {
        super(detail);
        this.status = status;
        this.titel = titel;
        this.regel = regel;
    }

    public static FachFehler nichtAngemeldet() {
        return new FachFehler(401, "Nicht angemeldet", "Bitte melde dich an.", null);
    }

    public static FachFehler keineBerechtigung() {
        return new FachFehler(403, "Keine Berechtigung", "Dafür hast du keine Berechtigung.", "RR-07");
    }

    public static FachFehler nichtGefunden(String detail) {
        return new FachFehler(404, "Nicht gefunden", detail, null);
    }

    public Fehler alsFehler() {
        return new Fehler(null, titel, status, getMessage(), regel, null);
    }

    public int status() {
        return status;
    }
}
