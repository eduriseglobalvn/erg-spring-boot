package com.erg.ergspringboot.service.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LmsNotificationEventPublisherTest {

    private static final String TENANT_ID = "erg";
    private static final String ASSIGNMENT_ID = "assign-1";
    private static final String ASSIGNMENT_TITLE = "Bai tap 1";
    private static final String CLASS_ID = "class-1";
    private static final String ATTEMPT_ID = "attempt-1";
    private static final String STUDENT_ID = "student-1";
    private static final String STUDENT_NAME = "Nguyen Van A";
    private static final String DUE_AT = "2026-06-20T23:59:59+07:00";
    private static final String CREATED_BY = "teacher-1";

    @Mock
    private NotificationCommandService notificationCommandService;

    @InjectMocks
    private LmsNotificationEventPublisher publisher;

    @Captor
    private ArgumentCaptor<NotificationCommand> commandCaptor;

    @Test
    void assignmentCreated_enqueuesCommandWithCorrectArgs() {
        List<String> studentUserIds = List.of("stu-1", "stu-2");
        publisher.assignmentCreated(TENANT_ID, ASSIGNMENT_ID, ASSIGNMENT_TITLE, CLASS_ID, studentUserIds, CREATED_BY);
        verify(notificationCommandService).enqueue(commandCaptor.capture());
        NotificationCommand cmd = commandCaptor.getValue();
        assertThat(cmd.tenantId()).isEqualTo(TENANT_ID);
        assertThat(cmd.eventType()).isEqualTo("ASSIGNMENT_CREATED");
        assertThat(cmd.sourceType()).isEqualTo("assignment");
        assertThat(cmd.sourceId()).isEqualTo(ASSIGNMENT_ID);
        assertThat(cmd.priority()).isEqualTo("normal");
        assertThat(cmd.portal()).isEqualTo("lms");
        assertThat(cmd.createdBy()).isEqualTo(CREATED_BY);
        assertThat(cmd.userIds()).isEqualTo(studentUserIds);
        assertThat(cmd.title()).contains("Bai tap moi");
        assertThat(cmd.data()).containsKeys("assignmentId", "classId");
    }

    @Test
    void assignmentCreated_doesNotEnqueueWhenStudentIdsIsEmpty() {
        publisher.assignmentCreated(TENANT_ID, ASSIGNMENT_ID, ASSIGNMENT_TITLE, CLASS_ID, List.of(), CREATED_BY);
        verify(notificationCommandService, never()).enqueue(any());
    }

    @Test
    void assignmentCreated_catchesException() {
        doThrow(new RuntimeException("simulated failure")).when(notificationCommandService).enqueue(any());
        publisher.assignmentCreated(TENANT_ID, ASSIGNMENT_ID, ASSIGNMENT_TITLE, CLASS_ID, List.of("stu-1"), CREATED_BY);
        verify(notificationCommandService).enqueue(any());
    }

    @Test
    void assignmentDueSoon_enqueuesCommandWithCorrectArgs() {
        publisher.assignmentDueSoon(TENANT_ID, ASSIGNMENT_ID, ASSIGNMENT_TITLE, DUE_AT, List.of("stu-1"));
        verify(notificationCommandService).enqueue(commandCaptor.capture());
        NotificationCommand cmd = commandCaptor.getValue();
        assertThat(cmd.eventType()).isEqualTo("ASSIGNMENT_DUE_SOON");
        assertThat(cmd.createdBy()).isEqualTo("system");
        assertThat(cmd.priority()).isEqualTo("normal");
        assertThat(cmd.title()).contains("Sap den han");
    }

    @Test
    void assignmentOverdue_enqueuesCommandWithCorrectArgs() {
        publisher.assignmentOverdue(TENANT_ID, ASSIGNMENT_ID, ASSIGNMENT_TITLE, DUE_AT, List.of("stu-1"));
        verify(notificationCommandService).enqueue(commandCaptor.capture());
        NotificationCommand cmd = commandCaptor.getValue();
        assertThat(cmd.eventType()).isEqualTo("ASSIGNMENT_OVERDUE");
        assertThat(cmd.priority()).isEqualTo("high");
        assertThat(cmd.title()).contains("Qua han");
    }

    @Test
    void attemptSubmitted_enqueuesCommandWithCorrectArgs() {
        publisher.attemptSubmitted(TENANT_ID, ASSIGNMENT_ID, ASSIGNMENT_TITLE, ATTEMPT_ID,
                STUDENT_ID, STUDENT_NAME, List.of("teacher-1"));
        verify(notificationCommandService).enqueue(commandCaptor.capture());
        NotificationCommand cmd = commandCaptor.getValue();
        assertThat(cmd.eventType()).isEqualTo("ATTEMPT_SUBMITTED");
        assertThat(cmd.sourceType()).isEqualTo("attempt");
        assertThat(cmd.sourceId()).isEqualTo(ATTEMPT_ID);
        assertThat(cmd.createdBy()).isEqualTo(STUDENT_ID);
        assertThat(cmd.title()).contains("da duoc nop");
    }

    @Test
    void supportNeededLowScore_enqueuesCommandWithCorrectArgs() {
        publisher.supportNeededLowScore(TENANT_ID, ASSIGNMENT_ID, ASSIGNMENT_TITLE, ATTEMPT_ID,
                STUDENT_ID, STUDENT_NAME, 30.0, 100.0, List.of("teacher-1"));
        verify(notificationCommandService).enqueue(commandCaptor.capture());
        NotificationCommand cmd = commandCaptor.getValue();
        assertThat(cmd.eventType()).isEqualTo("SUPPORT_NEEDED_LOW_SCORE");
        assertThat(cmd.priority()).isEqualTo("high");
        assertThat(cmd.data().get("score")).isEqualTo("30.0");
    }
}
