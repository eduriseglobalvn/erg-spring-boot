package com.erg.ergspringboot.curriculum;

import com.erg.ergspringboot.curriculum.internal.command.CurriculumCommandUseCase;
import com.erg.ergspringboot.curriculum.internal.impact.CurriculumImpactQueryUseCase;
import com.erg.ergspringboot.curriculum.internal.query.CurriculumTreeQueryUseCase;
import com.erg.ergspringboot.curriculum.internal.validation.CurriculumChainValidationUseCase;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import com.erg.ergspringboot.repository.learningcontent.TopicContentItemRepository;
import com.erg.ergspringboot.repository.lms.AssignmentRepository;
import com.erg.ergspringboot.repository.lms.AttemptRepository;
import com.erg.ergspringboot.repository.lms.QuestionRepository;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;

/** Temporary repository construction seam for the deprecated horizontal adapter. */
@Deprecated
public final class CurriculumOperationsCompatibility {
    private CurriculumOperationsCompatibility() { }

    public static CurriculumOperations fromRepositories(
            CurriculumSubjectRepository subjects,
            CurriculumLevelRepository levels,
            CurriculumTopicRepository topics,
            TopicContentItemRepository contentItems,
            QuestionRepository questions,
            QuizRepository quizzes,
            QuizVersionRepository quizVersions,
            AssignmentRepository assignments,
            AttemptRepository attempts) {
        CurriculumImpactQueryUseCase impacts = new CurriculumImpactQueryUseCase(
                subjects, levels, topics, contentItems, questions, quizzes,
                quizVersions, assignments, attempts);
        return new CurriculumOperations(
                new CurriculumTreeQueryUseCase(subjects, levels, topics),
                impacts,
                new CurriculumChainValidationUseCase(subjects, levels, topics),
                new CurriculumCommandUseCase(subjects, levels, topics, impacts));
    }
}
