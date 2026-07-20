package com.erg.ergspringboot.service.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.model.curriculum.CurriculumLevel;
import com.erg.ergspringboot.model.curriculum.CurriculumSubject;
import com.erg.ergspringboot.model.curriculum.CurriculumTopic;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import com.erg.ergspringboot.repository.learningcontent.TopicContentItemRepository;
import com.erg.ergspringboot.repository.lms.AssignmentRepository;
import com.erg.ergspringboot.repository.lms.AttemptRepository;
import com.erg.ergspringboot.repository.lms.QuestionRepository;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurriculumServiceTest {

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

    private CurriculumService service;

    @BeforeEach
    void setUp() {
        service = new CurriculumService(
                subjectRepository,
                levelRepository,
                topicRepository,
                topicContentItemRepository,
                questionRepository,
                quizRepository,
                quizVersionRepository,
                assignmentRepository,
                attemptRepository
        );
    }

    @Test
    void validateTopicChainRejectsTopicOutsideLevel() {
        CurriculumSubject subject = subject("subject-1", CurriculumService.ACTIVE);
        CurriculumLevel level = level("level-1", "subject-1", CurriculumService.ACTIVE);
        CurriculumTopic topic = topic("topic-1", "other-level", CurriculumService.ACTIVE);

        when(subjectRepository.findByTenantIdAndId("tenant-a", "subject-1")).thenReturn(Optional.of(subject));
        when(levelRepository.findByTenantIdAndId("tenant-a", "level-1")).thenReturn(Optional.of(level));
        when(topicRepository.findByTenantIdAndId("tenant-a", "topic-1")).thenReturn(Optional.of(topic));

        assertThatThrownBy(() -> service.validateTopicChain("subject-1", "level-1", "topic-1", access()))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("topic does not belong to level");
    }

    @Test
    void archiveTopicMarksOnlyThatTopicArchived() {
        CurriculumLevel level = level("level-1", "subject-1", CurriculumService.ACTIVE);
        CurriculumTopic topic = topic("topic-1", "level-1", CurriculumService.ACTIVE);

        when(topicRepository.findByTenantIdAndId("tenant-a", "topic-1")).thenReturn(Optional.of(topic));
        when(levelRepository.findByTenantIdAndId("tenant-a", "level-1")).thenReturn(Optional.of(level));
        when(topicRepository.save(topic)).thenReturn(topic);

        var response = service.archiveTopic("topic-1", access());

        assertThat(response.status()).isEqualTo(CurriculumService.ARCHIVED);
        assertThat(topic.getUpdatedBy()).isEqualTo("user-a");
        verify(topicRepository).save(topic);
    }

    @Test
    void hardDeleteLevelRejectsDraftLevelWithChildTopics() {
        CurriculumLevel level = level("level-1", "subject-1", CurriculumService.DRAFT);

        when(levelRepository.findByTenantIdAndId("tenant-a", "level-1")).thenReturn(Optional.of(level));
        when(topicRepository.countByTenantIdAndLevelId("tenant-a", "level-1")).thenReturn(1L);

        assertThatThrownBy(() -> service.hardDeleteLevel("level-1", access()))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("impact report has no dependencies");
        verify(levelRepository, never()).delete(level);
    }

    @Test
    void hardDeleteTopicAllowsEmptyDraftTopic() {
        CurriculumTopic topic = topic("topic-1", "level-1", CurriculumService.DRAFT);

        when(topicRepository.findByTenantIdAndId("tenant-a", "topic-1")).thenReturn(Optional.of(topic));

        var response = service.hardDeleteTopic("topic-1", access());

        assertThat(response.deleted()).isTrue();
        assertThat(response.nodeType()).isEqualTo("topic");
        verify(topicRepository).delete(topic);
    }

    @Test
    void topicImpactUsesRealDependencyCounts() {
        CurriculumTopic topic = topic("topic-1", "level-1", CurriculumService.DRAFT);

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
        CurriculumSubject subject = subject("subject-1", CurriculumService.DRAFT);
        CurriculumLevel level = level("level-1", "subject-1", CurriculumService.DRAFT);
        CurriculumTopic topic = topic("topic-1", "level-1", CurriculumService.DRAFT);

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
