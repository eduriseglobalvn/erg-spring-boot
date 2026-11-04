package com.erg.ergspringboot.lcms.persistence.quiz.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.lms.LmsStatePolicy;
import org.junit.jupiter.api.Test;

class AttemptStateParityTest {
    @Test
    void analyticsUseTheLmsCompletedAttemptVocabulary() {
        assertThat(AttemptAnswerRowRepository.COMPLETED_ATTEMPT_STATES)
                .containsExactlyElementsOf(LmsStatePolicy.completedAttemptStates());
    }
}
