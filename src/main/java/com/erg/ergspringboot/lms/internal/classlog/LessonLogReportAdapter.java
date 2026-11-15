package com.erg.ergspringboot.lms.internal.classlog;

import com.erg.ergspringboot.lms.LessonLogReportPort;
import com.erg.ergspringboot.lms.LessonScheduleReadPort.LessonSlot;
import com.erg.ergspringboot.lms.persistence.LessonLogEntry;
import com.erg.ergspringboot.lms.persistence.LessonLogEntryRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class LessonLogReportAdapter implements LessonLogReportPort {

    private final LessonLogReader reader;
    private final LessonLogEntryRepository entries;

    LessonLogReportAdapter(LessonLogReader reader, LessonLogEntryRepository entries) {
        this.reader = reader;
        this.entries = entries;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PeriodState> periodStates(String tenantId, Collection<LessonSlot> slots, Instant now) {
        return reader.rows(tenantId, List.copyOf(slots), now).stream()
                .map(r -> new PeriodState(r.period().eventId(), r.period().periodNo(), r.period().lessonTitle(),
                        r.period().happened(), "CONFIRMED".equals(r.period().status())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> reportedEvents(String tenantId, Collection<String> eventIds) {
        if (eventIds.isEmpty()) return Set.of();
        return entries.findByTenantIdAndEventIdIn(tenantId, eventIds).stream()
                .filter(e -> "CONFIRMED".equals(e.getStatus())).map(LessonLogEntry::getEventId).collect(Collectors.toSet());
    }
}
