package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.elearning.internal.readmodel.StudentDashboardReadService;
import com.erg.ergspringboot.lms.internal.readmodel.LmsAssignmentProgressQueryUseCase;
import com.erg.ergspringboot.lms.internal.readmodel.LmsTeacherHomeworkReadUseCase;
import com.erg.ergspringboot.reporting.quiz.QuizReportService;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;

/**
 * The scope functions in {@link LmsCacheDescriptorContributor} read a fixed position of each {@code @Cacheable} key.
 * Reordering a key must fail here, or a scoped invalidation would silently miss the entries it should drop.
 */
class LmsCacheScopeKeyContractTest {

    @Test
    void scopePositionsMatchTheCacheableKeys() {
        assertThat(keyParts(LmsTeacherHomeworkReadUseCase.class, "lmsHomeworkWorkspace")).allSatisfy(parts -> assertThat(parts.get(1)).isEqualTo("#teacherId"));
        assertThat(keyParts(LmsAssignmentProgressQueryUseCase.class, "lmsAssignmentProgress")).allSatisfy(parts -> assertThat(parts.get(1)).isEqualTo("#assignmentId"));
        assertThat(keyParts(QuizReportService.class, "quizAssignmentReport")).allSatisfy(parts -> assertThat(parts.get(1)).isEqualTo("#assignmentId"));
        assertThat(keyParts(QuizReportService.class, "quizQuestionAnalytics")).allSatisfy(parts -> assertThat(parts.get(1)).isIn("#quizId", "#quizVersionId"));
        assertThat(keyParts(StudentDashboardReadService.class, "studentDashboard")).allSatisfy(parts -> {
            assertThat(parts.get(0)).isEqualTo("#scope.userId()");
            assertThat(parts.get(3)).isEqualTo("#scope.requestedStudentUserId()");
        });
    }

    @Test
    void keyPartReadsTheListElement() {
        assertThat(LmsCacheDescriptorContributor.keyPart(1).apply(List.of("t1", "asg-1", "active"))).isEqualTo("asg-1");
        assertThat(LmsCacheDescriptorContributor.keyPart(5).apply(List.of("t1"))).isNull();
        assertThat(LmsCacheDescriptorContributor.keyPart(0).apply("not-a-list")).isNull();
    }

    private static List<List<String>> keyParts(Class<?> type, String cacheName) {
        List<List<String>> keys = Arrays.stream(type.getDeclaredMethods())
                .map(method -> method.getAnnotation(Cacheable.class))
                .filter(cacheable -> cacheable != null && Arrays.asList(cacheable.cacheNames()).contains(cacheName))
                .map(cacheable -> Arrays.stream(cacheable.key().replaceAll("^\\{|}$", "").split(","))
                        .map(String::trim).toList())
                .toList();
        assertThat(keys).as(cacheName + " @Cacheable keys on " + type.getSimpleName()).isNotEmpty();
        return keys;
    }
}
