package com.erg.ergspringboot.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.calendar.CalendarBatchCancelled;
import com.erg.ergspringboot.calendar.CalendarEventChanged;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection.PublishedBatch;
import com.erg.ergspringboot.calendar.CalendarSlotsChanged;
import com.erg.ergspringboot.calendar.CalendarSlotsChanged.RemovedPerson;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CalendarScheduleChangeConsumerTest {

    private final CalendarPublishedBatchProjection calendar = mock(CalendarPublishedBatchProjection.class);
    private final NotificationCommandService notifications = mock(NotificationCommandService.class);
    private final CalendarScheduleChangeConsumer consumer = new CalendarScheduleChangeConsumer(calendar, notifications);
    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-10T08:00:00+07:00");

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<NotificationCommand> enqueued(int calls) {
        ArgumentCaptor<List<NotificationCommand>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(notifications, times(calls)).enqueueAll(captor.capture());
        return captor.getAllValues().stream().flatMap(List::stream).toList();
    }

    @Test
    void cancelledBatchNotifiesEveryoneAndARetryReusesTheSameKeys() {
        when(calendar.findCancelledBatch("tenant-1", "batch-1", now))
                .thenReturn(Optional.of(ScheduleNotificationFixtures.published()));
        CalendarBatchCancelled event = new CalendarBatchCancelled(
                "schedule-cancelled:batch-1", "tenant-1", "batch-1", "admin-1", now, 1, 3, 7L);

        consumer.on(event);
        consumer.on(event);

        List<NotificationCommand> sent = enqueued(2);
        assertThat(sent).extracting(NotificationCommand::idempotencyKey).containsExactlyInAnyOrder(
                "schedule-cancelled:batch-1:t1:7", "schedule-cancelled:batch-1:t2:7",
                "schedule-cancelled:batch-1:t1:7", "schedule-cancelled:batch-1:t2:7");
        assertThat(sent).extracting(NotificationCommand::eventType).containsOnly("SCHEDULE_CANCELLED");
    }

    @Test
    void draftBatchCancelAndMissingPeriodsAreSkipped() {
        when(calendar.findCancelledBatch("tenant-1", "batch-1", now)).thenReturn(Optional.empty());
        consumer.on(new CalendarBatchCancelled(
                "schedule-cancelled:batch-1", "tenant-1", "batch-1", "admin-1", now, 1, 0, 2L));
        when(calendar.findChangedEvent("tenant-1", "e1")).thenReturn(Optional.of(
                new PublishedBatch("batch-1", "school-1", "THCS A", "A", "PUBLISHED", List.of())));
        consumer.on(new CalendarEventChanged(
                "schedule-event-changed:e1:3", "tenant-1", "e1", "batch-1", "admin-1", now, 1, 3L));

        verifyNoInteractions(notifications);
    }

    @Test
    void slotsChangedTellsWhoIsOnThemAndWhoLostOneAndARetryReusesTheKeys() {
        when(calendar.findChangedEvents("tenant-1", "batch-1", List.of("e1", "e2"))).thenReturn(Optional.of(ScheduleNotificationFixtures.published()));
        CalendarSlotsChanged event = new CalendarSlotsChanged("schedule-slots-changed:c1", "tenant-1", "batch-1", "SWAP",
                List.of("e1", "e2"), List.of(new RemovedPerson("e2", "t9", "Lê Văn Chín", "MAIN")), "admin-1", now, 1, 5L);

        consumer.on(event);
        consumer.on(event);

        List<NotificationCommand> sent = enqueued(4);
        assertThat(sent).extracting(NotificationCommand::idempotencyKey).containsExactlyInAnyOrder(
                "schedule-slots-changed:c1:t1:5", "schedule-slots-changed:c1:t2:5", "schedule-slots-changed:c1:removed:t9:5",
                "schedule-slots-changed:c1:t1:5", "schedule-slots-changed:c1:t2:5", "schedule-slots-changed:c1:removed:t9:5");
        assertThat(sent).extracting(NotificationCommand::eventType).containsOnly("SCHEDULE_CHANGED");
        NotificationCommand removed = sent.stream().filter(c -> c.idempotencyKey().contains("removed")).findFirst().orElseThrow();
        assertThat(removed.title()).startsWith("Tiết dạy được chuyển đi");
    }

    @Test
    void slotsChangedWithNothingPublishedAnymoreSendsNothing() {
        when(calendar.findChangedEvents("tenant-1", "batch-1", List.of("e1"))).thenReturn(Optional.of(
                new PublishedBatch("batch-1", "school-1", "THCS A", "A", "PUBLISHED", List.of())));

        consumer.on(new CalendarSlotsChanged("schedule-slots-changed:c2", "tenant-1", "batch-1", "EDIT", List.of("e1"), List.of(),
                "admin-1", now, 1, 2L));

        verifyNoInteractions(notifications);
    }

    @Test
    void changedPeriodSendsScheduleChanged() {
        when(calendar.findChangedEvent("tenant-1", "e2")).thenReturn(Optional.of(ScheduleNotificationFixtures.published()));

        consumer.on(new CalendarEventChanged(
                "schedule-event-changed:e2:3", "tenant-1", "e2", "batch-1", "admin-1", now, 1, 3L));

        List<NotificationCommand> sent = enqueued(1);
        assertThat(sent).extracting(NotificationCommand::eventType).containsOnly("SCHEDULE_CHANGED");
        assertThat(sent).extracting(NotificationCommand::idempotencyKey)
                .containsExactlyInAnyOrder("schedule-event-changed:e2:t1:3", "schedule-event-changed:e2:t2:3");
    }
}
