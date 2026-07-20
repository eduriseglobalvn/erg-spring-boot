package com.erg.ergspringboot.lcms;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.controller.lcms.graphql.LcmsReportGraphQlController;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import org.junit.jupiter.api.Test;

class LcmsReportGraphQlBoundaryTest {
    @Test
    void reportGraphQlControllerDependsOnLcmsFacadeAndAccessAdapter() {
        assertThat(LcmsReportGraphQlController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(LcmsQueryOperations.class, GraphQlAccessSupport.class));
    }
}
