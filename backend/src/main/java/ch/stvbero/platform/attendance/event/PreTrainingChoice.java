package ch.stvbero.platform.attendance.event;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** What a person can say to the pre-training of a practice (ANM-14): «dabei» or «nicht dabei», nothing else. */
@Schema(name = "PreTrainingChoice")
public enum PreTrainingChoice {
    ATTENDING, DECLINED;

    public ResponseStatus status() {
        return ResponseStatus.valueOf(name());
    }
}
