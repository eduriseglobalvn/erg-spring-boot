package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.service.notifications.NotificationCommandService;
import com.erg.ergspringboot.service.notifications.NotificationDeviceService;
import com.erg.ergspringboot.service.notifications.NotificationOutboxWorker;
import com.erg.ergspringboot.service.notifications.NotificationService;
import com.erg.ergspringboot.repository.notifications.NotificationOutboxRepository;
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
        module = "notifications",
        mode = ApplicationModuleTest.BootstrapMode.STANDALONE,
        verifyAutomatically = false)
@ActiveProfiles("test")
@Sql(scripts = "classpath:db/migration/V73__spring_modulith_event_publication.sql")
class CalendarPublishedScenarioTest {

    static {
        System.setProperty("spring.modulith.detection-strategy", "explicitly-annotated");
    }

    @MockitoBean CalendarPublishedBatchProjection calendar;
    @MockitoBean NotificationCommandService notifications;
    // The scenario isolates the notifications event consumer. Public REST
    // facades are still compatibility adapters over the legacy services, so
    // their collaborators are mocked instead of widening the standalone
    // module's component scan.
    @MockitoBean NotificationDeviceService notificationDevices;
    @MockitoBean NotificationService notificationService;
    @MockitoBean NotificationOutboxWorker notificationWorker;
    @MockitoBean NotificationOutboxRepository notificationOutbox;

    @Test
    void publishedEventEventuallyEnqueuesOneIdempotentNotification(Scenario scenario) {
        when(calendar.findPublishedBatch("tenant-1", "batch-1"))
                .thenReturn(Optional.of(new CalendarPublishedBatchProjection.PublishedBatch(
                        "batch-1", "school-1", 1, List.of("teacher-1"))));
        CalendarBatchPublished event = new CalendarBatchPublished(
                "schedule-published:batch-1", "tenant-1", "batch-1", "user-1", OffsetDateTime.now(), 1);

        scenario.publish(event)
                .andWaitAtMost(Duration.ofSeconds(5))
                .andWaitForStateChange(() -> mockingDetails(notifications).getInvocations().size(), count -> count == 1)
                .andVerify(count -> assertThat(count).isEqualTo(1));
    }
}
