package com.erg.ergspringboot.lms.internal.quiz.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizPackageResponse;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizPackage;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizPackageRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersion;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The learner package must carry the teacher's player template to the client (settings.playerTemplate). */
class QuizRuntimePackageServiceTest {

    private final QuizPackageRepository packages = mock(QuizPackageRepository.class);
    private final QuizVersionRepository versions = mock(QuizVersionRepository.class);
    private final QuizRuntimePackageService service = new QuizRuntimePackageService(packages, versions);

    private QuizPackageResponse activePackageWith(Map<String, Object> runtimeQuizExtras) {
        Map<String, Object> runtimeQuiz = new LinkedHashMap<>(Map.of(
                "info", Map.of("title", "Quiz 1"),
                "sections", List.of()));
        runtimeQuiz.putAll(runtimeQuizExtras);
        QuizVersion version = new QuizVersion();
        version.setId("version-1");
        version.setVersionNumber(1);
        QuizPackage pkg = new QuizPackage();
        pkg.setGradingMode("server-authoritative");
        pkg.setPackageJson(Map.of("quiz", runtimeQuiz));
        when(versions.findByQuizIdAndTenantIdAndIsActiveTrue("quiz-1", "tenant-a")).thenReturn(Optional.of(version));
        when(packages.findByQuizVersionIdAndTenantId("version-1", "tenant-a")).thenReturn(Optional.of(pkg));
        return service.activePackage("tenant-a", "quiz-1").orElseThrow();
    }

    @Test
    void playerTemplateReachesTheClientInSettings() {
        Map<String, Object> template = Map.of("accentColor", "#0071E3");

        assertThat(activePackageWith(Map.of("playerTemplate", template)).quiz().settings())
                .containsEntry("playerTemplate", template);
    }

    @Test
    void noPlayerTemplateKeyWhenThePackageHasNone() {
        assertThat(activePackageWith(Map.of()).quiz().settings()).doesNotContainKey("playerTemplate");
    }
}
