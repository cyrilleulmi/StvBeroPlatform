package ch.stvbero.platform.attendance.absence;

/** Why a person is away (ABS-01). The UI shows «Ferien», «Verletzung», «Anderes». */
public enum AbsenceReason {
    VACATION("Ferien"), INJURY("Verletzung"), OTHER("Abwesend");

    private final String label;

    AbsenceReason(String label) {
        this.label = label;
    }

    /** German word used in the comment of the declined responses (ABS-03). */
    public String label() {
        return label;
    }
}
