package com.erg.ergspringboot.controller.curriculum;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.curriculum.CurriculumOperations;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;

class CurriculumControllerContractTest {

    @Test
    void exposesBoundedSubjectLevelTopicTree() throws Exception {
        Method endpoint = CurriculumController.class.getDeclaredMethod(
                "tree", String.class, jakarta.servlet.http.HttpServletRequest.class);
        GetMapping mapping = endpoint.getAnnotation(GetMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/tree");
    }

    @Test
    void curriculumTreeUsesTenantScopedBusinessCache() throws Exception {
        Method method = CurriculumOperations.class.getDeclaredMethod(
                "listTree", String.class, com.erg.ergspringboot.platform.security.UserAccessContext.class);
        Cacheable cacheable = method.getAnnotation(Cacheable.class);

        assertThat(cacheable).isNotNull();
        assertThat(cacheable.cacheNames()).containsExactly("curriculumTree");
        assertThat(cacheable.key()).contains("tenantId");
    }
}
