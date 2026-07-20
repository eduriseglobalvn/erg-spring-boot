package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.model.calendar.CalendarBatch;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.repository.calendar.CalendarBatchRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
import com.erg.ergspringboot.service.calendar.CalendarCommandService;
import com.erg.ergspringboot.service.calendar.CalendarReadService;
import com.erg.ergspringboot.service.notifications.NotificationCommandService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

@ApplicationModuleTest(
        module = "calendar",
        mode = ApplicationModuleTest.BootstrapMode.STANDALONE,
        verifyAutomatically = false)
@ActiveProfiles("test")
@Sql(scripts = "classpath:db/migration/postgres/V73__spring_modulith_event_publication.sql")
class CalendarPublishedScenarioTest {

    static {
        System.setProperty("spring.modulith.detection-strategy", "explicitly-annotated");
    }

    @MockitoBean CalendarReadService reads;
    @MockitoBean CalendarCommandService commands;
    @MockitoBean CalendarBatchRepository batches;
    @MockitoBean CalendarEventRepository events;
    @MockitoBean CalendarEventTeacherRepository assignments;
    @MockitoBean NotificationCommandService notifications;

    @Test
    void publishedEventEventuallyEnqueuesOneIdempotentNotification(Scenario scenario) {
        CalendarBatch batch = new CalendarBatch();
        batch.setId("batch-1");
        batch.setTenantId("tenant-1");
        batch.setSchoolId("school-1");
        CalendarEvent calendarEvent = new CalendarEvent();
        calendarEvent.setId("event-1");
        CalendarEventTeacher teacher = new CalendarEventTeacher();
        teacher.setTeacherUserId("teacher-1");
        when(batches.findByTenantIdAndId("tenant-1", "batch-1")).thenReturn(Optional.of(batch));
        when(events.findByTenantIdAndBatchId("tenant-1", "batch-1")).thenReturn(List.of(calendarEvent));
        when(assignments.findByTenantIdAndEventIdIn("tenant-1", List.of("event-1"))).thenReturn(List.of(teacher));
        CalendarBatchPublished event = new CalendarBatchPublished(
                "schedule-published:batch-1", "tenant-1", "batch-1", "user-1", OffsetDateTime.now(), 1);

        scenario.publish(event)
                .andWaitAtMost(Duration.ofSeconds(5))
                .andWaitForStateChange(() -> mockingDetails(notifications).getInvocations().size(), count -> count == 1)
                .andVerify(count -> assertThat(count).isEqualTo(1));
    }
}
