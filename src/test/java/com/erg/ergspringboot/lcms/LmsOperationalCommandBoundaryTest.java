package com.erg.ergspringboot.lcms;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.controller.lcms.LmsOperationalCommandController;
import com.erg.ergspringboot.controller.lcms.graphql.LcmsSchoolOperationsMutationGraphQlController;
import com.erg.ergspringboot.lcms.authorization.LcmsClassManage;
import com.erg.ergspringboot.lcms.authorization.LcmsEnrollmentManage;
import com.erg.ergspringboot.lcms.authorization.LcmsStudentManage;
import com.erg.ergspringboot.lcms.authorization.LcmsSubjectManage;
import com.erg.ergspringboot.lms.LmsOperationalOperations;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

class LmsOperationalCommandBoundaryTest {

    @Test
    void restCommandsUseCanonicalLcmsBaseAndPublicFacade() {
        assertThat(LmsOperationalCommandController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/v1/lcms");
        assertThat(LmsOperationalCommandController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(LmsOperationalOperations.class));
    }

    @Test
    void everyOperationalRestMutationHasTypedAuthorization() {
        Stream<Method> methods = Arrays.stream(LmsOperationalCommandController.class.getDeclaredMethods())
                .filter(method -> method.getName().matches("(create|update|archive|import|enroll|bulkEnroll).*"));
        assertThat(methods).allSatisfy(method -> assertThat(
                method.isAnnotationPresent(LcmsClassManage.class)
                        || method.isAnnotationPresent(LcmsStudentManage.class)
                        || method.isAnnotationPresent(LcmsSubjectManage.class)
                        || method.isAnnotationPresent(LcmsEnrollmentManage.class))
                .as("missing typed authorization on %s", method.getName())
                .isTrue());
    }

    @Test
    void classStudentAndSubjectUpdatesAcceptPutAndPatchWithConflictVersion() throws Exception {
        for (String methodName : new String[]{"updateClass", "updateStudent", "updateSubject"}) {
            Method method = Arrays.stream(LmsOperationalCommandController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
            RequestMapping mapping = method.getAnnotation(RequestMapping.class);
            assertThat(mapping.method()).contains(RequestMethod.PUT, RequestMethod.PATCH);
            assertThat(Arrays.stream(method.getParameterTypes()).anyMatch(type -> type.getSimpleName().startsWith("Update")))
                    .isTrue();
        }
    }

    @Test
    void graphqlMutationAdapterDependsOnTheSameOperationalFacade() {
        assertThat(LcmsSchoolOperationsMutationGraphQlController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(LmsOperationalOperations.class,
                                com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport.class));
        assertThat(Arrays.stream(LcmsSchoolOperationsMutationGraphQlController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(MutationMapping.class))
                .count()).isEqualTo(12);
    }
}
