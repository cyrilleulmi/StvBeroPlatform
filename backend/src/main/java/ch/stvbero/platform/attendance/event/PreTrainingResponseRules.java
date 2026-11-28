package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.error.DomainError;

/**
 * Rules of the response to a pre-training (ANM-14 to ANM-16, E-119). A person who is «dabei» at the practice says
 * separately whether she is at the pre-training; only the pre-training without the practice is not possible.
 */
public final class PreTrainingResponseRules {

    public static final String FIELD = "preTraining";
    public static final String NONE = "Dieser Termin hat kein Vortraining.";
    public static final String NEEDS_PRACTICE = "Beim Vortraining dabei sein geht nur zusammen mit dem Training.";

    private PreTrainingResponseRules() {
    }

    /**
     * The response to the pre-training as everyone sees it (ANM-15): what the person said while she is «dabei» at
     * the practice ({@code PENDING} until she says so), otherwise the status of the practice.
     *
     * @param stored what is stored: {@code ATTENDING}, {@code DECLINED} or {@code null}
     * @return {@code null} for an event without a pre-training
     */
    public static ResponseStatus status(boolean hasPreTraining, ResponseStatus practice, ResponseStatus stored) {
        if (!hasPreTraining) {
            return null;
        }
        if (practice != ResponseStatus.ATTENDING) {
            return practice;
        }
        return stored == null ? ResponseStatus.PENDING : stored;
    }

    /**
     * What is stored for the pre-training after a response to the practice. Only «dabei» at the practice keeps or
     * takes an answer; every other response empties it, so that it is asked again (ANM-15).
     *
     * @param previousPractice the response to the practice so far, {@code PENDING} without one
     * @param previousStored   what was stored for the pre-training so far, {@code null} for nothing
     * @param input            what the request says about the pre-training, {@code null}: unchanged
     * @return {@code ATTENDING}, {@code DECLINED} or {@code null}
     */
    public static ResponseStatus stored(boolean hasPreTraining, ResponseStatus previousPractice,
                                        ResponseStatus previousStored, ResponseStatus newPractice,
                                        PreTrainingChoice input) {
        if (input != null && !hasPreTraining) {
            throw DomainError.invalidField(FIELD, NONE);
        }
        if (newPractice != ResponseStatus.ATTENDING) {
            if (input == PreTrainingChoice.ATTENDING) {
                throw DomainError.ruleViolation(FIELD, "Nur Vortraining geht nicht", NEEDS_PRACTICE, "ANM-15");
            }
            return null;
        }
        if (input != null) {
            return input.status();
        }
        return hasPreTraining && previousPractice == ResponseStatus.ATTENDING ? previousStored : null;
    }
}
