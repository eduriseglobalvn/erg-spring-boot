package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.lcms.persistence.quiz.bank.Quiz;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizPackage;
import java.util.List;
import java.util.Map;

/**
 * The few fields starting an attempt (and projecting its progress) needs from a quiz and its published package, so neither the
 * package_json nor the quiz jsonb columns are loaded per start. Cached as {@code quizStartInfo}; evicted with quizRuntimePackage.
 */
public record QuizStartInfo(boolean quizFound, String quizTitle, String quizKind, String quizCurrentVersionId,
        int quizQuestionCount, boolean packageFound, String packageId, String contentHash, String quizVersionId,
        String quizVersion, String gradingMode, int packageQuestionCount) {

    public static QuizStartInfo of(Quiz quiz, QuizPackage pkg) {
        return new QuizStartInfo(quiz != null, quiz == null ? null : quiz.getTitle(), quiz == null ? null : quiz.getKind(),
                quiz == null ? null : quiz.getCurrentVersionId(),
                quiz == null || quiz.getQuestionIds() == null ? 0 : quiz.getQuestionIds().size(),
                pkg != null, pkg == null ? null : pkg.getId(), pkg == null ? null : pkg.getContentHash(),
                pkg == null ? null : pkg.getQuizVersionId(), pkg == null ? null : pkg.getQuizVersion(),
                pkg == null ? null : pkg.getGradingMode(), pkg == null ? 0 : questions(pkg.getPackageJson()));
    }

    private static int questions(Map<String, Object> json) {
        if (json == null || !(json.get("quiz") instanceof Map<?, ?> quiz) || !(quiz.get("sections") instanceof List<?> sections)) return 0;
        return sections.stream().filter(Map.class::isInstance)
                .mapToInt(section -> ((Map<?, ?>) section).get("questions") instanceof List<?> list ? list.size() : 0).sum();
    }
}
