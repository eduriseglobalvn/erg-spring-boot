package com.erg.ergspringboot.controller.elearning.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class StudentDashboardMultiClassContractTest {

    @Test
    void dashboardContractExposesExplicitClassSelectionAndMemberships() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/graphql/schema.graphqls"));

        assertThat(schema).contains("input StudentDashboardInput {", "classId: String");
        assertThat(schema).contains("selectedClassId: String", "classes: [StudentDashboardClassMembership!]!");
        assertThat(componentNames(StudentDashboardResponse.class))
                .contains("selectedClassId", "classes");
    }

    private java.util.List<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
    }
}
