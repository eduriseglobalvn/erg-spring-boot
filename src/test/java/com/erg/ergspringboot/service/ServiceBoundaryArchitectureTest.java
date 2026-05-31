package com.erg.ergspringboot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.service.hoclieu.HocLieuService;
import com.erg.ergspringboot.service.hoclieu.HocLieuAssetService;
import com.erg.ergspringboot.service.hoclieu.HocLieuProgressService;
import com.erg.ergspringboot.service.hoclieu.HocLieuResourceService;
import com.erg.ergspringboot.service.hoclieu.HocLieuTaxonomyService;
import com.erg.ergspringboot.service.lms.LmsAssignmentService;
import com.erg.ergspringboot.service.lms.LmsAttemptService;
import com.erg.ergspringboot.service.lms.LmsClassroomService;
import com.erg.ergspringboot.service.lms.LmsQuizService;
import com.erg.ergspringboot.service.lms.LmsRuntimeService;
import com.erg.ergspringboot.service.lms.LmsStudentService;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ServiceBoundaryArchitectureTest {

    @Test
    void hocLieuFacadeUsesModelOrientedServicesInsteadOfRepositories() {
        Set<Class<?>> expectedCollaborators = Set.of(
                HocLieuTaxonomyService.class,
                HocLieuResourceService.class,
                HocLieuAssetService.class,
                HocLieuProgressService.class
        );

        assertThat(HocLieuService.class.getDeclaredFields())
                .extracting(Field::getType)
                .containsAll(expectedCollaborators);

        assertThat(HocLieuService.class.getDeclaredFields())
                .extracting(field -> field.getType().getName())
                .noneMatch(typeName -> typeName.startsWith("com.erg.ergspringboot.repository."));
    }

    @Test
    void lmsRuntimeServiceDoesNotUseInMemoryStore() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/erg/ergspringboot/service/lms/LmsRuntimeService.java"));

        assertThat(source)
                .doesNotContain("ConcurrentHashMap")
                .doesNotContain("new HashMap<");
    }

    @Test
    void lmsRuntimeFacadeUsesModelOrientedServicesInsteadOfRepositories() {
        Set<Class<?>> expectedCollaborators = Set.of(
                LmsClassroomService.class,
                LmsAssignmentService.class,
                LmsQuizService.class,
                LmsAttemptService.class,
                LmsStudentService.class
        );

        assertThat(LmsRuntimeService.class.getDeclaredFields())
                .extracting(Field::getType)
                .containsAll(expectedCollaborators);

        assertThat(LmsRuntimeService.class.getDeclaredFields())
                .extracting(field -> field.getType().getName())
                .noneMatch(typeName -> typeName.startsWith("com.erg.ergspringboot.repository."));
    }

    @Test
    void learningDomainServicesUseSinglePostgresTransactionManager() throws Exception {
        String hocLieuSource = Files.readString(Path.of("src/main/java/com/erg/ergspringboot/service/hoclieu/HocLieuService.java"));
        String lmsSource = Files.readString(Path.of("src/main/java/com/erg/ergspringboot/service/lms/LmsRuntimeService.java"));

        assertThat(hocLieuSource)
                .doesNotContain("@Transactional(readOnly = true)")
                .contains("postgresTransactionManager")
                .doesNotContain("learningTransactionManager");
        assertThat(lmsSource)
                .doesNotContain("@Transactional(readOnly = true)")
                .contains("postgresTransactionManager")
                .doesNotContain("learningTransactionManager");
    }
}
