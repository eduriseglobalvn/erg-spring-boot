package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.controller.lms.LmsAssignmentController;
import org.junit.jupiter.api.Test;

class LmsAssignmentBoundaryTest {
    @Test
    void assignmentControllerDependsOnlyOnLmsFacade() {
        assertThat(LmsAssignmentController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(LmsOperations.class));
    }
}
