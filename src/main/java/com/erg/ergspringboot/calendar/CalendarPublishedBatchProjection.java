package com.erg.ergspringboot.calendar;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Public, persistence-free projection consumed by downstream modules after a schedule changes. */
public interface CalendarPublishedBatchProjection {

    /** The batch with its PUBLISHED periods; {@code status} lets the consumer skip a batch cancelled meanwhile. */
    Optional<PublishedBatch> findPublishedBatch(String tenantId, String batchId);

    /**
     * The future periods the cancel at {@code cancelledAt} took away. Empty when the batch was never published
     * (teachers never saw a draft, so there is nobody to tell).
     */
    Optional<PublishedBatch> findCancelledBatch(String tenantId, String batchId, OffsetDateTime cancelledAt);

    /** The given periods (cancelled by a day off), grouped by batch; periods of other tenants or not CANCELLED are left out. */
    List<PublishedBatch> findCancelledEvents(String tenantId, java.util.Collection<String> calendarEventIds);

    /** One edited period wrapped in its batch; no slot when the period is no longer PUBLISHED. */
    Optional<PublishedBatch> findChangedEvent(String tenantId, String calendarEventId);

    /** {@code role} is MAIN or ASSISTANT. */
    record Person(String userId, String name, String role) {
    }

    record Slot(String eventId, String classId, String className, OffsetDateTime startAt, int periodStart,
            int periodCount, String roomName, List<Person> people) {
        public Slot {
            people = people == null ? List.of() : List.copyOf(people);
        }
    }

    record PublishedBatch(String batchId, String schoolId, String schoolName, String schoolShortName,
            String status, List<Slot> slots) {
        public PublishedBatch {
            slots = slots == null ? List.of() : List.copyOf(slots);
        }
    }
}
