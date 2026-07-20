package com.erg.ergspringboot.service.quiz.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.curriculum.CurriculumLevel;
import com.erg.ergspringboot.model.curriculum.CurriculumSubject;
import com.erg.ergspringboot.model.curriculum.CurriculumTopic;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LmsAssessmentCurriculumBridgeTest {

    @Mock
    private CurriculumSubjectRepository subjectRepository;

    @Mock
    private CurriculumLevelRepository levelRepository;

    @Mock
    private CurriculumTopicRepository topicRepository;

    @Test
    void validateQuestionPlacementAcceptsTopicInsideLevelInsideSubjectForTenant() {
        LmsAssessmentCurriculumBridge bridge = bridge();
        when(subjectRepository.findByTenantIdAndId("tenant-a", "subject-1")).thenReturn(Optional.of(subject("tenant-a", "subject-1", "IC3")));
        when(levelRepository.findByTenantIdAndId("tenant-a", "level-1")).thenReturn(Optional.of(level("tenant-a", "level-1", "subject-1", "Level 1")));
        when(topicRepository.findByTenantIdAndId("tenant-a", "topic-1")).thenReturn(Optional.of(topic("tenant-a", "topic-1", "level-1", "Hardware")));

        bridge.validateQuestionPlacement("tenant-a", "subject-1", "level-1", "topic-1");
    }

    @Test
    void validateQuestionPlacementRejectsTopicOutsideLevel() {
        LmsAssessmentCurriculumBridge bridge = bridge();
        when(subjectRepository.findByTenantIdAndId("tenant-a", "subject-1")).thenReturn(Optional.of(subject("tenant-a", "subject-1", "IC3")));
        when(levelRepository.findByTenantIdAndId("tenant-a", "level-1")).thenReturn(Optional.of(level("tenant-a", "level-1", "subject-1", "Level 1")));
        when(topicRepository.findByTenantIdAndId("tenant-a", "topic-1")).thenReturn(Optional.of(topic("tenant-a", "topic-1", "other-level", "Hardware")));

        assertThatThrownBy(() -> bridge.validateQuestionPlacement("tenant-a", "subject-1", "level-1", "topic-1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("topic does not belong to level");
    }

    @Test
    void validateQuestionPlacementRejectsLevelOutsideSubject() {
        LmsAssessmentCurriculumBridge bridge = bridge();
        when(subjectRepository.findByTenantIdAndId("tenant-a", "subject-1")).thenReturn(Optional.of(subject("tenant-a", "subject-1", "IC3")));
        when(levelRepository.findByTenantIdAndId("tenant-a", "level-1")).thenReturn(Optional.of(level("tenant-a", "level-1", "other-subject", "Level 1")));

        assertThatThrownBy(() -> bridge.validateQuestionPlacement("tenant-a", "subject-1", "level-1", "topic-1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("level does not belong to subject");
    }

    @Test
    void validateQuestionPlacementRejectsCrossTenantSubject() {
        LmsAssessmentCurriculumBridge bridge = bridge();
        when(subjectRepository.findByTenantIdAndId("tenant-a", "subject-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bridge.validateQuestionPlacement("tenant-a", "subject-1", "level-1", "topic-1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("subject not found in tenant");
    }

    @Test
    void snapshotForQuizUsesLegacyLmsRefsUntilCanonicalCurriculumIsReady() {
        LmsAssessmentCurriculumBridge bridge = bridge();
        Quiz quiz = new Quiz();
        quiz.setSubjectId("subject-1");
        quiz.setLevelId("level-1");
        quiz.setCategoryId("topic-1");
        when(subjectRepository.findByTenantIdAndId("tenant-a", "subject-1")).thenReturn(Optional.of(subject("tenant-a", "subject-1", "IC3")));
        when(levelRepository.findByTenantIdAndId("tenant-a", "level-1")).thenReturn(Optional.of(level("tenant-a", "level-1", "subject-1", "Level 1")));
        when(topicRepository.findByTenantIdAndId("tenant-a", "topic-1")).thenReturn(Optional.of(topic("tenant-a", "topic-1", "level-1", "Hardware")));

        Map<String, Object> snapshot = bridge.snapshotForQuiz("tenant-a", quiz);

        assertThat(snapshot).containsEntry("bridge", "canonical-curriculum");
        assertThat(snapshot).containsEntry("canonicalPending", false);
        assertThat(snapshot.get("subject")).isEqualTo(Map.of(
                "id", "subject-1",
                "label", "IC3",
                "sourceTable", "curriculum_subjects"
        ));
    }

    private LmsAssessmentCurriculumBridge bridge() {
        return new LmsAssessmentCurriculumBridge(subjectRepository, levelRepository, topicRepository);
    }

    private CurriculumSubject subject(String tenantId, String id, String name) {
        CurriculumSubject subject = new CurriculumSubject();
        subject.setTenantId(tenantId);
        subject.setId(id);
        subject.setName(name);
        return subject;
    }

    private CurriculumLevel level(String tenantId, String id, String subjectId, String name) {
        CurriculumLevel level = new CurriculumLevel();
        level.setTenantId(tenantId);
        level.setId(id);
        level.setSubjectId(subjectId);
        level.setName(name);
        return level;
    }

    private CurriculumTopic topic(String tenantId, String id, String levelId, String name) {
        CurriculumTopic topic = new CurriculumTopic();
        topic.setTenantId(tenantId);
        topic.setId(id);
        topic.setLevelId(levelId);
        topic.setName(name);
        return topic;
    }
}
