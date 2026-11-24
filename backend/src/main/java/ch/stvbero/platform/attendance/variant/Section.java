package ch.stvbero.platform.attendance.variant;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Sections of the event page that a variant shows and orders (VAR-07). */
@Schema(name = "EventSection")
public enum Section {
    /** «Anmeldungen»: overview of the responses by team and line. */
    RESPONSES,
    /** «Spiele»: matches with results and table ranks. */
    MATCHES,
    /** «Aufgebot»: only where the variant makes a call-up (VAR-06). */
    CALL_UP,
    /** «Zeitplan» (TML). */
    TIMELINE,
    /** «Fahrerliste» (DRV). */
    DRIVER_LIST,
    /** «Aufstellungen»: special line-ups (LIN-16). */
    LINEUPS,
    /** «Präsenz»: only where the attendance check is on (PRS-02). */
    ATTENDANCE,
    /** «Details»: always shown. */
    DETAILS
}
