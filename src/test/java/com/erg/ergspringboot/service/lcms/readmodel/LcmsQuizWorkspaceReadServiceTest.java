package com.erg.ergspringboot.service.lcms.readmodel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.quiz.version.QuizVersion;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import com.erg.ergspringboot.repository.quiz.authoring.QuizDraftRepository;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class LcmsQuizWorkspaceReadServiceTest {
    @Mock private QuizRepository quizRepository;
    @Mock private QuizDraftRepository quizDraftRepository;
    @Mock private QuizVersionRepository quizVersionRepository;
    @Mock private CurriculumSubjectRepository subjectRepository;
    @Mock private CurriculumLevelRepository levelRepository;
    @Mock private CurriculumTopicRepository topicRepository;

    @Test
    void rejectsUnboundedQuizVersionHistory() {
        List<QuizVersion> versions = IntStream.range(0, 101).mapToObj(index -> {
            QuizVersion version = new QuizVersion();
            version.setId("version-" + index);
            return version;
        }).toList();
        when(quizVersionRepository.findByQuizIdAndTenantIdOrderByVersionNumberDesc(
                eq("quiz-1"), eq("tenant-a"), any(Pageable.class)))
                .thenReturn(versions);
        LcmsQuizWorkspaceReadService service = new LcmsQuizWorkspaceReadService(
                quizRepository, quizDraftRepository, quizVersionRepository,
                subjectRepository, levelRepository, topicRepository, new ObjectMapper());

        assertThatThrownBy(() -> service.quizVersions("tenant-a", "quiz-1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("100")
                .hasMessageContaining("versions");
    }
}
