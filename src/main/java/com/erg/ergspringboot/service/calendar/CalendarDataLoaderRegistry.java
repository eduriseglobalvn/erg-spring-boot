package com.erg.ergspringboot.service.calendar;

import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers GraphQL DataLoader beans for batch-loading calendar entities.
 *
 * <p>DataLoader batches individual get-by-key requests into a single repository
 * call, preventing N+1 queries in GraphQL schema-mapped fields.</p>
 */
@Configuration
public class CalendarDataLoaderRegistry {

    private static final Logger log = LoggerFactory.getLogger(CalendarDataLoaderRegistry.class);

    private final CalendarEventTeacherRepository eventTeacherRepository;
    private final CalendarEventRepository eventRepository;

    public CalendarDataLoaderRegistry(
            CalendarEventTeacherRepository eventTeacherRepository,
            CalendarEventRepository eventRepository) {
        this.eventTeacherRepository = eventTeacherRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * DataLoader that batches {@code findByEventId} calls for
     * {@link CalendarEventTeacher} by a list of event IDs.
     *
     * <p>Each key is an event ID. The loader returns
     * {@code List<List<CalendarEventTeacher>>} where the outer list corresponds
     * to the order of the input keys (never null, always present).</p>
     */
    @Bean
    public DataLoader<String, List<CalendarEventTeacher>>
            calendarEventTeacherByEventIdLoader() {
        BatchLoader<String, List<CalendarEventTeacher>> batchLoader = keys -> {
            log.debug("Batch-loading CalendarEventTeacher for {} event IDs", keys.size());
            List<String> distinctKeys = keys.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (distinctKeys.isEmpty()) {
                return CompletableFuture.completedFuture(
                        keys.stream().map(k -> List.<CalendarEventTeacher>of()).toList());
            }
            List<CalendarEventTeacher> all =
                    eventTeacherRepository.findByEventIdIn(distinctKeys);
            Map<String, List<CalendarEventTeacher>> grouped = all.stream()
                    .collect(Collectors.groupingBy(
                            CalendarEventTeacher::getEventId));
            List<List<CalendarEventTeacher>> result = new ArrayList<>(keys.size());
            for (String key : keys) {
                result.add(grouped.getOrDefault(key, List.of()));
            }
            return CompletableFuture.completedFuture(result);
        };
        return DataLoaderFactory.newDataLoader(batchLoader);
    }

    /**
     * DataLoader that batches {@code findByBatchId} calls for
     * {@link CalendarEvent} by a list of batch IDs.
     *
     * <p>Each key is a batch ID. The loader returns
     * {@code List<List<CalendarEvent>>} where the outer list corresponds to the
     * order of the input keys (never null, always present).</p>
     */
    @Bean
    public DataLoader<String, List<CalendarEvent>>
            calendarEventByBatchIdLoader() {
        BatchLoader<String, List<CalendarEvent>> batchLoader = keys -> {
            log.debug("Batch-loading CalendarEvent for {} batch IDs", keys.size());
            List<String> distinctKeys = keys.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (distinctKeys.isEmpty()) {
                return CompletableFuture.completedFuture(
                        keys.stream().map(k -> List.<CalendarEvent>of()).toList());
            }
            List<CalendarEvent> all =
                    eventRepository.findByBatchIdIn(distinctKeys);
            Map<String, List<CalendarEvent>> grouped = all.stream()
                    .collect(Collectors.groupingBy(
                            CalendarEvent::getBatchId));
            List<List<CalendarEvent>> result = new ArrayList<>(keys.size());
            for (String key : keys) {
                result.add(grouped.getOrDefault(key, List.of()));
            }
            return CompletableFuture.completedFuture(result);
        };
        return DataLoaderFactory.newDataLoader(batchLoader);
    }
}
