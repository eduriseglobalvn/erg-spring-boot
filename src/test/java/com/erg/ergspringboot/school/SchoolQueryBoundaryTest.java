package com.erg.ergspringboot.school;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.controller.lcms.graphql.LcmsSchoolOperationsGraphQlController;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import org.junit.jupiter.api.Test;

class SchoolQueryBoundaryTest {
    @Test
    void schoolGraphQlControllerDependsOnQueryFacadeAndAccessAdapter() {
        assertThat(LcmsSchoolOperationsGraphQlController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(SchoolQueryOperations.class, GraphQlAccessSupport.class));
    }
}
