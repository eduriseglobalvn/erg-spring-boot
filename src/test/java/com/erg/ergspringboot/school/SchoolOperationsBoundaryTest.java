package com.erg.ergspringboot.school;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.controller.lcms.SchoolCommandController;
import org.junit.jupiter.api.Test;

class SchoolOperationsBoundaryTest {
    @Test
    void schoolCommandControllerDependsOnSchoolFacade() {
        assertThat(SchoolCommandController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(SchoolOperations.class));
    }
}
