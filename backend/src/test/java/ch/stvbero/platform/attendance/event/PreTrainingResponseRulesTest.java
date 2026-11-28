package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.error.DomainError;
import org.junit.jupiter.api.Test;

import static ch.stvbero.platform.attendance.event.ResponseStatus.ATTENDING;
import static ch.stvbero.platform.attendance.event.ResponseStatus.DECLINED;
import static ch.stvbero.platform.attendance.event.ResponseStatus.MAYBE;
import static ch.stvbero.platform.attendance.event.ResponseStatus.PENDING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Rules of the response to a pre-training (ANM-14, ANM-15, E-119). */
class PreTrainingResponseRulesTest {

    // ------------------------------------------------------------------ What everyone sees

    @Test
    void anm14WithDabeiAtThePracticeItIsWhatThePersonSaidOrPending() {
        assertEquals(PENDING, PreTrainingResponseRules.status(true, ATTENDING, null));
        assertEquals(ATTENDING, PreTrainingResponseRules.status(true, ATTENDING, ATTENDING));
        assertEquals(DECLINED, PreTrainingResponseRules.status(true, ATTENDING, DECLINED));
    }

    @Test
    void anm15WithoutDabeiAtThePracticeItFollowsThePractice() {
        assertEquals(DECLINED, PreTrainingResponseRules.status(true, DECLINED, null));
        assertEquals(MAYBE, PreTrainingResponseRules.status(true, MAYBE, null));
        assertEquals(PENDING, PreTrainingResponseRules.status(true, PENDING, null));
        // An old answer does not count once the person is no longer «dabei»
        assertEquals(DECLINED, PreTrainingResponseRules.status(true, DECLINED, ATTENDING));
        assertEquals(MAYBE, PreTrainingResponseRules.status(true, MAYBE, ATTENDING));
    }

    @Test
    void anEventWithoutAPreTrainingHasNoResponseToIt() {
        assertNull(PreTrainingResponseRules.status(false, ATTENDING, null));
        assertNull(PreTrainingResponseRules.status(false, ATTENDING, ATTENDING));
        assertNull(PreTrainingResponseRules.status(false, PENDING, null));
    }

    // ------------------------------------------------------------------ What is stored

    @Test
    void anm14DabeiAloneLeavesThePreTrainingOpenAndKeepsAnEarlierAnswer() {
        assertNull(PreTrainingResponseRules.stored(true, PENDING, null, ATTENDING, null));
        assertEquals(ATTENDING, PreTrainingResponseRules.stored(true, ATTENDING, ATTENDING, ATTENDING, null));
        assertEquals(DECLINED, PreTrainingResponseRules.stored(true, ATTENDING, DECLINED, ATTENDING, null));
    }

    @Test
    void anm14TheAnswerGoesWithDabei() {
        assertEquals(ATTENDING, PreTrainingResponseRules.stored(true, PENDING, null, ATTENDING, PreTrainingChoice.ATTENDING));
        assertEquals(DECLINED, PreTrainingResponseRules.stored(true, ATTENDING, ATTENDING, ATTENDING, PreTrainingChoice.DECLINED));
    }

    @Test
    void anm15AChangeAwayFromDabeiRemovesTheAnswerAndItIsAskedAgain() {
        assertNull(PreTrainingResponseRules.stored(true, ATTENDING, ATTENDING, DECLINED, null));
        assertNull(PreTrainingResponseRules.stored(true, ATTENDING, ATTENDING, MAYBE, null));
        // Back to «dabei»: the answer from before the change does not come back
        assertNull(PreTrainingResponseRules.stored(true, DECLINED, ATTENDING, ATTENDING, null));
        assertNull(PreTrainingResponseRules.stored(true, MAYBE, DECLINED, ATTENDING, null));
    }

    @Test
    void anm15OnlyThePreTrainingIsNotPossible() {
        for (ResponseStatus practice : new ResponseStatus[] {DECLINED, MAYBE}) {
            DomainError error = assertThrows(DomainError.class, () -> PreTrainingResponseRules.stored(true, PENDING,
                    null, practice, PreTrainingChoice.ATTENDING));
            assertEquals(422, error.status());
            assertEquals("ANM-15", error.rule());
            assertEquals(PreTrainingResponseRules.NEEDS_PRACTICE, error.getMessage());
        }
        // «nicht dabei» at both says nothing new and is accepted
        assertNull(PreTrainingResponseRules.stored(true, PENDING, null, DECLINED, PreTrainingChoice.DECLINED));
    }

    @Test
    void anEventWithoutAPreTrainingTakesNoAnswer() {
        DomainError error = assertThrows(DomainError.class, () -> PreTrainingResponseRules.stored(false, PENDING, null,
                ATTENDING, PreTrainingChoice.ATTENDING));
        assertEquals(400, error.status());
        assertNull(PreTrainingResponseRules.stored(false, PENDING, null, ATTENDING, null));
        // A stale value of an event that lost its pre-training is dropped
        assertNull(PreTrainingResponseRules.stored(false, ATTENDING, ATTENDING, ATTENDING, null));
    }
}
