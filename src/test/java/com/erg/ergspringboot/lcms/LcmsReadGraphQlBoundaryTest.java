package com.erg.ergspringboot.lcms;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.controller.lcms.graphql.LcmsQuizWorkspaceGraphQlController;
import com.erg.ergspringboot.controller.lcms.graphql.LcmsWorkspaceGraphQlController;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import org.junit.jupiter.api.Test;

class LcmsReadGraphQlBoundaryTest {
    @Test
    void quizWorkspaceControllerDependsOnQueryFacadeAndAccessAdapter() {
        assertThat(LcmsQuizWorkspaceGraphQlController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(LcmsQueryOperations.class, GraphQlAccessSupport.class));
    }

    @Test
    void workspaceControllerDependsOnQueryFacadeAndAccessAdapter() {
        assertThat(LcmsWorkspaceGraphQlController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(LcmsQueryOperations.class, GraphQlAccessSupport.class));
    }
}
