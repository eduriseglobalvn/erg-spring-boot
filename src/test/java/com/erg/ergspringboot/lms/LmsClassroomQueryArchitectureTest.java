package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LmsClassroomQueryArchitectureTest {
    @Test
    void classroomQueriesAreOwnedByInternalUseCase() throws Exception {
        assertThatCode(() -> Class.forName("com.erg.ergspringboot.lms.internal.classroom.LmsClassroomQueryUseCase"))
                .doesNotThrowAnyException();
        String legacy = Files.readString(Path.of("src/main/java/com/erg/ergspringboot/lms/internal/runtime/LmsRuntimeUseCase.java"));
        assertThat(legacy).contains("classrooms.listClassesBySchool(");
        assertThat(legacy).doesNotContain("findByTenantIdAndSchoolIdOrderByNameAsc(");
    }
}
