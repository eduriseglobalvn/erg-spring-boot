package com.erg.ergspringboot.service.elearning.engagement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.service.elearning.engagement.StudentEngagementService.CreateDiscussionCommand;
import com.erg.ergspringboot.service.elearning.engagement.StudentEngagementService.MarkAnnouncementReadCommand;
import com.erg.ergspringboot.service.elearning.engagement.StudentEngagementService.SetReactionCommand;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class StudentEngagementServiceTest {

    @Mock
    private StudentRepository students;
    @Mock
    private JdbcTemplate jdbc;

    private StudentEngagementService service;

    @BeforeEach
    void setUp() {
        service = new StudentEngagementService(students, jdbc);
    }

    @Test
    void createDiscussionRequiresIdempotencyKeyBeforeWriting() {
        when(students.findFirstByTenantIdAndAuthUserId("tenant-a", "user-1")).thenReturn(Optional.of(student()));

        assertThatThrownBy(() -> service.createDiscussion(new CreateDiscussionCommand(
                "tenant-a", "user-1", "", "Title", "Content", null, List.of()
        ))).isInstanceOf(BadRequestException.class);

        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void reactionCannotTargetAThreadOutsideAuthenticatedStudentClass() {
        when(students.findFirstByTenantIdAndAuthUserId("tenant-a", "user-1")).thenReturn(Optional.of(student()));
        when(jdbc.queryForObject(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(Integer.class),
                org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq("post-other-class"),
                org.mockito.ArgumentMatchers.eq("class-1")
        )).thenReturn(0);

        assertThatThrownBy(() -> service.setReaction(new SetReactionCommand(
                "tenant-a", "user-1", "POST", "post-other-class", "like"
        ))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void announcementReadCannotCreateReceiptForUntargetedAnnouncement() {
        when(students.findFirstByTenantIdAndAuthUserId("tenant-a", "user-1")).thenReturn(Optional.of(student()));
        when(jdbc.queryForObject(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(Integer.class),
                org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq("announcement-1"),
                org.mockito.ArgumentMatchers.eq("class-1"),
                org.mockito.ArgumentMatchers.eq("student-1")
        )).thenReturn(0);

        assertThatThrownBy(() -> service.markAnnouncementRead(new MarkAnnouncementReadCommand(
                "tenant-a", "user-1", "announcement-1"
        ))).isInstanceOf(ResourceNotFoundException.class);
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    private Student student() {
        Student student = new Student();
        student.setId("student-1");
        student.setTenantId("tenant-a");
        student.setAuthUserId("user-1");
        student.setAcademicClassId("class-1");
        return student;
    }
}
