package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StudentAssignmentListResponse;
import com.erg.ergspringboot.config.CacheConfig;
import com.erg.ergspringboot.lms.internal.readmodel.LmsStudentReadModelUseCase;
import com.erg.ergspringboot.platform.cache.ErgTieredCacheManager;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;

class StudentAssignmentReadCacheContractTest {

    @Test
    void repeatedStudentAssignmentReadsUseATypedScopeAwareCache() throws Exception {
        Cacheable cacheable = LmsStudentReadModelUseCase.class
                .getMethod("listAssignments", String.class, UserAccessContext.class, int.class, int.class)
                .getAnnotation(Cacheable.class);

        assertThat(cacheable).isNotNull();
        assertThat(cacheable.cacheNames()).containsExactly("lmsStudentAssignments");
        assertThat(cacheable.key())
                .contains("#studentId")
                .contains("#access.scopeFingerprint()")
                .doesNotContain("#access.managedEducationUnitIds()")
                .doesNotContain("#access.managedClassIds()")
                .contains("#page")
                .contains("#size");

        ErgTieredCacheManager manager = (ErgTieredCacheManager) new CacheConfig().cacheManager();
        assertThat(manager.descriptor("lmsStudentAssignments").valueType().getRawClass())
                .isEqualTo(StudentAssignmentListResponse.class);

        String compose = Files.readString(Path.of("ops/docker/compose.benchmark.yml"));
        assertThat(compose).contains("lmsStudentAssignments");
    }
}
