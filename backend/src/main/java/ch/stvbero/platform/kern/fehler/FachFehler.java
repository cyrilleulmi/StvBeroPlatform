package ch.stvbero.platform.kern.fehler;

import java.util.List;

/** Fehler, der als Problem Details an den Client geht. */
public class FachFehler extends RuntimeException {

    private final int status;
    private final String titel;
    private final String regel;
    private final List<Fehler.FeldFehler> felder;

    public FachFehler(int status, String titel, String detail, String regel) {
        this(status, titel, detail, regel, null);
    }

    private FachFehler(int status, String titel, String detail, String regel, List<Fehler.FeldFehler> felder) {
        super(detail);
        this.status = status;
        this.titel = titel;
        this.regel = regel;
        this.felder = felder;
    }

    /** 400 mit einem fehlerhaften Feld, z.B. Zeitraum «bis» vor «von». */
    public static FachFehler ungueltigesFeld(String feld, String meldung) {
        return new FachFehler(400, "Ungültige Anfrage", meldung, null, List.of(new Fehler.FeldFehler(feld, meldung)));
    }

    /** 409: widerspricht dem aktuellen Zustand. */
    public static FachFehler konflikt(String titel, String detail, String regel) {
        return new FachFehler(409, titel, detail, regel);
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
        return new Fehler(null, titel, status, getMessage(), regel, felder);
    }

    public int status() {
        return status;
    }

    public String regel() {
        return regel;
    }
}
