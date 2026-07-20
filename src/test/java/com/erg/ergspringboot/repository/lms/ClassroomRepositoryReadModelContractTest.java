package com.erg.ergspringboot.repository.lms;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

class ClassroomRepositoryReadModelContractTest {

    @Test
    void classWorkspaceUsesProjectionWithScopedCountsInsteadOfClassroomEntities() throws Exception {
        Method method = ClassroomRepository.class.getMethod(
                "findSchoolClassWorkspaces",
                String.class,
                String.class,
                String.class,
                Pageable.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(method.getGenericReturnType().getTypeName())
                .contains("org.springframework.data.domain.Page")
                .contains("ClassWorkspaceProjection");
        assertThat(query.value())
                .contains("ClassWorkspaceProjection")
                .contains("classroom.tenantId = :tenantId")
                .contains("classroom.schoolId = :schoolId")
                .contains("lower(classroom.status) = :status")
                .contains("student.tenantId = :tenantId")
                .contains("student.academicClassId = classroom.id")
                .contains("assignment.tenantId = :tenantId")
                .contains("assignment.academicClassId = classroom.id")
                .doesNotContain("select classroom")
                .doesNotContain("join fetch")
                .doesNotContain("classroom.students")
                .doesNotContain("classroom.assignments");
        assertThat(query.countQuery())
                .contains("count(classroom.id)")
                .contains("classroom.tenantId = :tenantId")
                .contains("classroom.schoolId = :schoolId");
    }

    @Test
    void classWorkspaceDetailUsesProjectionAndTenantClassSchoolBounds() throws Exception {
        Method method = ClassroomRepository.class.getMethod(
                "findClassWorkspace",
                String.class,
                String.class,
                String.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(method.getGenericReturnType().getTypeName())
                .contains("java.util.Optional")
                .contains("ClassWorkspaceProjection");
        assertThat(query.value())
                .contains("ClassWorkspaceProjection")
                .contains("classroom.tenantId = :tenantId")
                .contains("classroom.id = :classId")
                .contains("(:schoolId is null or classroom.schoolId = :schoolId)")
                .contains("student.tenantId = :tenantId")
                .contains("student.academicClassId = classroom.id")
                .contains("assignment.tenantId = :tenantId")
                .contains("assignment.academicClassId = classroom.id")
                .doesNotContain("select classroom")
                .doesNotContain("join fetch")
                .doesNotContain("classroom.students")
                .doesNotContain("classroom.assignments");
    }

    @Test
    void teacherHomeworkClassOptionsUseProjectionAndTeacherScope() throws Exception {
        Method method = ClassroomRepository.class.getMethod(
                "findTeacherClassOptions",
                String.class,
                String.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(method.getGenericReturnType().getTypeName())
                .contains("java.util.List")
                .contains("ClassOptionProjection");
        assertThat(query.value())
                .contains("ClassOptionProjection")
                .contains("classroom.tenantId = :tenantId")
                .contains("classroom.homeroomTeacherId = :teacherId")
                .contains("assignment.assignedBy = :teacherId")
                .doesNotContain("select classroom")
                .doesNotContain("join fetch")
                .doesNotContain("classroom.students")
                .doesNotContain("classroom.assignments");
    }
}
