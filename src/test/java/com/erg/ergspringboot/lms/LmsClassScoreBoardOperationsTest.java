package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.lms.persistence.projection.ClassScoreBoardAttemptProjection;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class LmsClassScoreBoardOperationsTest {

    private static ClassScoreBoardAttemptProjection row(OffsetDateTime started, OffsetDateTime submitted) {
        return new ClassScoreBoardAttemptProjection("att", "stu", "asg", "Bài", "quiz", "Bài", "topic", "Chủ đề 1",
                800d, 1000d, 80d, "SUBMITTED", started, submitted);
    }

    @Test
    void theTimeTakenIsFromStartToHandIn() {
        OffsetDateTime start = OffsetDateTime.parse("2026-11-18T08:00:00+07:00");
        var attempt = LmsClassScoreBoardOperations.attempt(row(start, start.plusMinutes(12).plusSeconds(30)));

        assertThat(attempt.durationSeconds()).isEqualTo(750);
        assertThat(attempt.status()).isEqualTo("submitted");
    }

    @Test
    void anAttemptWithoutBothTimesHasNoDuration() {
        OffsetDateTime start = OffsetDateTime.parse("2026-11-18T08:00:00+07:00");

        assertThat(LmsClassScoreBoardOperations.attempt(row(null, start)).durationSeconds()).isNull();
        assertThat(LmsClassScoreBoardOperations.attempt(row(start, start.minusMinutes(1))).durationSeconds()).isNull();
    }

    @Test
    void aRosterLongerThanThePageIsFlagged() {
        var classrooms = org.mockito.Mockito.mock(com.erg.ergspringboot.lms.persistence.ClassroomRepository.class);
        var students = org.mockito.Mockito.mock(com.erg.ergspringboot.lms.persistence.StudentRepository.class);
        var scores = org.mockito.Mockito.mock(com.erg.ergspringboot.lms.persistence.ClassStudentScoreRepository.class);
        var info = org.mockito.Mockito.mock(com.erg.ergspringboot.lms.persistence.projection.ClassWorkspaceProjection.class);
        org.mockito.Mockito.when(classrooms.findClassWorkspace("t1", "c1", null)).thenReturn(java.util.Optional.of(info));
        org.mockito.Mockito.when(students.findClassRoster(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(),
                        org.springframework.data.domain.PageRequest.of(0, 200), 201));

        var board = new LmsClassScoreBoardOperations(classrooms, students, scores).scoreBoard("t1", "c1");

        assertThat(board.rosterTruncated()).isTrue();
        assertThat(board.truncated()).isFalse();
    }
}
