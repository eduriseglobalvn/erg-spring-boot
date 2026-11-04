package com.erg.ergspringboot.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.curriculum.internal.CurriculumReviewAccess;
import com.erg.ergspringboot.curriculum.internal.command.CurriculumCommandUseCase;
import com.erg.ergspringboot.curriculum.internal.impact.CurriculumImpactQueryUseCase;
import com.erg.ergspringboot.curriculum.internal.query.CurriculumTreeQueryUseCase;
import com.erg.ergspringboot.curriculum.internal.validation.CurriculumChainValidationUseCase;
import com.erg.ergspringboot.model.curriculum.CurriculumLevel;
import com.erg.ergspringboot.model.curriculum.CurriculumSubject;
import com.erg.ergspringboot.model.curriculum.CurriculumTopic;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import com.erg.ergspringboot.learningcontent.persistence.TopicContentItemRepository;
import com.erg.ergspringboot.lms.persistence.AssignmentRepository;
import com.erg.ergspringboot.lms.persistence.AttemptRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuestionRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import com.erg.ergspringboot.curriculum.CurriculumException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurriculumOperationsTest {

    @Mock
    private CurriculumSubjectRepository subjectRepository;

    @Mock
    private CurriculumLevelRepository levelRepository;

    @Mock
    private CurriculumTopicRepository topicRepository;

    @Mock
    private TopicContentItemRepository topicContentItemRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuizVersionRepository quizVersionRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AttemptRepository attemptRepository;

    private CurriculumOperations service;

    @BeforeEach
    void setUp() {
        CurriculumReviewAccess review = CurriculumReviewAccess.denyAll();
        CurriculumImpactQueryUseCase impacts = new CurriculumImpactQueryUseCase(
                subjectRepository, levelRepository, topicRepository, topicContentItemRepository,
                questionRepository, quizRepository, quizVersionRepository, assignmentRepository,
                attemptRepository);
        service = new CurriculumOperations(
                new CurriculumTreeQueryUseCase(subjectRepository, levelRepository, topicRepository, review),
                impacts,
                new CurriculumChainValidationUseCase(subjectRepository, levelRepository, topicRepository, review),
                new CurriculumCommandUseCase(subjectRepository, levelRepository, topicRepository, impacts, review));
    }

    @Test
    void validateTopicChainRejectsTopicOutsideLevel() {
        CurriculumSubject subject = subject("subject-1", "active");
        CurriculumLevel level = level("level-1", "subject-1", "active");
        CurriculumTopic topic = topic("topic-1", "other-level", "active");

        when(subjectRepository.findByTenantIdAndId("tenant-a", "subject-1")).thenReturn(Optional.of(subject));
        when(levelRepository.findByTenantIdAndId("tenant-a", "level-1")).thenReturn(Optional.of(level));
        when(topicRepository.findByTenantIdAndId("tenant-a", "topic-1")).thenReturn(Optional.of(topic));

        assertThatThrownBy(() -> service.validateTopicChain("subject-1", "level-1", "topic-1", access()))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("topic does not belong to level");
    }

    @Test
    void archiveTopicMarksOnlyThatTopicArchived() {
        CurriculumLevel level = level("level-1", "subject-1", "active");
        CurriculumTopic topic = topic("topic-1", "level-1", "active");

        when(topicRepository.findByTenantIdAndId("tenant-a", "topic-1")).thenReturn(Optional.of(topic));
        when(levelRepository.findByTenantIdAndId("tenant-a", "level-1")).thenReturn(Optional.of(level));
        when(topicRepository.save(topic)).thenReturn(topic);

        var response = service.archiveTopic("topic-1", access());

        assertThat(response.status()).isEqualTo("archived");
        assertThat(topic.getUpdatedBy()).isEqualTo("user-a");
        verify(topicRepository).save(topic);
    }

    @Test
    void hardDeleteLevelRejectsDraftLevelWithChildTopics() {
        CurriculumLevel level = level("level-1", "subject-1", "draft");

        when(levelRepository.findByTenantIdAndId("tenant-a", "level-1")).thenReturn(Optional.of(level));
        when(topicRepository.countByTenantIdAndLevelId("tenant-a", "level-1")).thenReturn(1L);

        assertThatThrownBy(() -> service.hardDeleteLevel("level-1", access()))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("impact report has no dependencies");
        verify(levelRepository, never()).delete(level);
    }

    @Test
    void hardDeleteTopicAllowsEmptyDraftTopic() {
        CurriculumTopic topic = topic("topic-1", "level-1", "draft");

        when(topicRepository.findByTenantIdAndId("tenant-a", "topic-1")).thenReturn(Optional.of(topic));

        var response = service.hardDeleteTopic("topic-1", access());

        assertThat(response.deleted()).isTrue();
        assertThat(response.nodeType()).isEqualTo("topic");
        verify(topicRepository).delete(topic);
    }

    @Test
    void topicImpactUsesRealDependencyCounts() {
        CurriculumTopic topic = topic("topic-1", "level-1", "draft");

        when(topicRepository.findByTenantIdAndId("tenant-a", "topic-1")).thenReturn(Optional.of(topic));
        when(topicContentItemRepository.countByTenantIdAndTopicId("tenant-a", "topic-1")).thenReturn(2L);
        when(questionRepository.countImpactByTenantAndTopic("tenant-a", "topic-1")).thenReturn(3L);
        when(quizRepository.countImpactByTenantAndTopic("tenant-a", "topic-1")).thenReturn(4L);
        when(quizVersionRepository.countPublishedImpactByTenantAndTopic("tenant-a", "topic-1")).thenReturn(5L);
        when(assignmentRepository.countImpactByTenantAndTopic("tenant-a", "topic-1")).thenReturn(6L);
        when(attemptRepository.countImpactByTenantAndTopic("tenant-a", "topic-1")).thenReturn(7L);

        var impact = service.topicImpact("topic-1", access());

        assertThat(impact.canHardDelete()).isFalse();
        assertThat(impact.impact().contentItems()).isEqualTo(2L);
        assertThat(impact.impact().questions()).isEqualTo(3L);
        assertThat(impact.impact().quizzes()).isEqualTo(4L);
        assertThat(impact.impact().publishedQuizVersions()).isEqualTo(5L);
        assertThat(impact.impact().assignments()).isEqualTo(6L);
        assertThat(impact.impact().studentAttempts()).isEqualTo(7L);
        assertThat(impact.todo()).doesNotContain("TODO");
    }

    @Test
    void subjectImpactAggregatesContentPlacementsFromChildTopics() {
        CurriculumSubject subject = subject("subject-1", "draft");
        CurriculumLevel level = level("level-1", "subject-1", "draft");
        CurriculumTopic topic = topic("topic-1", "level-1", "draft");

        when(subjectRepository.findByTenantIdAndId("tenant-a", "subject-1")).thenReturn(Optional.of(subject));
        when(levelRepository.countByTenantIdAndSubjectId("tenant-a", "subject-1")).thenReturn(1L);
        when(levelRepository.findByTenantIdAndSubjectIdInOrderBySortOrderAscNameAsc("tenant-a", List.of("subject-1")))
                .thenReturn(List.of(level));
        when(topicRepository.findByTenantIdAndLevelIdInOrderBySortOrderAscNameAsc("tenant-a", List.of("level-1")))
                .thenReturn(List.of(topic));
        when(topicContentItemRepository.countByTenantIdAndTopicIdIn("tenant-a", List.of("topic-1"))).thenReturn(2L);
        when(questionRepository.countImpactByTenantAndSubject("tenant-a", "subject-1")).thenReturn(3L);
        when(quizRepository.countImpactByTenantAndSubject("tenant-a", "subject-1")).thenReturn(4L);

        var impact = service.subjectImpact("subject-1", access());

        assertThat(impact.impact().childLevels()).isEqualTo(1L);
        assertThat(impact.impact().childTopics()).isEqualTo(1L);
        assertThat(impact.impact().contentItems()).isEqualTo(2L);
        assertThat(impact.impact().questions()).isEqualTo(3L);
        assertThat(impact.impact().quizzes()).isEqualTo(4L);
        assertThat(impact.canHardDelete()).isFalse();
    }

    private UserAccessContext access() {
        return new UserAccessContext("user-a", "tenant-a", List.of("*"), List.of("*"), null, null);
    }

    private CurriculumSubject subject(String id, String status) {
        CurriculumSubject subject = new CurriculumSubject();
        subject.setId(id);
        subject.setTenantId("tenant-a");
        subject.setCode(id);
        subject.setName(id);
        subject.setStatus(status);
        subject.setSortOrder(0);
        return subject;
    }

    private CurriculumLevel level(String id, String subjectId, String status) {
        CurriculumLevel level = new CurriculumLevel();
        level.setId(id);
        level.setTenantId("tenant-a");
        level.setSubjectId(subjectId);
        level.setCode(id);
        level.setName(id);
        level.setStatus(status);
        level.setSortOrder(0);
        return level;
    }

    private CurriculumTopic topic(String id, String levelId, String status) {
        CurriculumTopic topic = new CurriculumTopic();
        topic.setId(id);
        topic.setTenantId("tenant-a");
        topic.setLevelId(levelId);
        topic.setCode(id);
        topic.setName(id);
        topic.setStatus(status);
        topic.setSortOrder(0);
        return topic;
    }
}
