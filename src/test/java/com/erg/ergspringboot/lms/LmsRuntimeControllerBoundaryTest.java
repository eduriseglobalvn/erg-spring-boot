package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.controller.lms.LmsController;
import org.junit.jupiter.api.Test;

class LmsRuntimeControllerBoundaryTest {
    @Test
    void runtimeControllerDependsOnlyOnLmsFacade() {
        assertThat(LmsController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(LmsOperations.class));
    }
}
