package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.controller.lms.graphql.LmsClassWorkspaceGraphQlController;
import com.erg.ergspringboot.controller.lms.graphql.LmsHomeworkWorkspaceGraphQlController;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import org.junit.jupiter.api.Test;

class LmsQueryBoundaryTest {
    @Test
    void classWorkspaceControllerDependsOnQueryFacadeAndAccessAdapter() {
        assertThat(LmsClassWorkspaceGraphQlController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(LmsQueryOperations.class, GraphQlAccessSupport.class));
    }

    @Test
    void homeworkWorkspaceControllerDependsOnQueryFacadeAndAccessAdapter() {
        assertThat(LmsHomeworkWorkspaceGraphQlController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(LmsQueryOperations.class, GraphQlAccessSupport.class));
    }
}
