package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarEventResponse;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
import java.util.List;

/** Owns the Calendar event-detail query and aggregate assembly. */
public final class CalendarEventDetailQueryUseCase {

    private final CalendarEventRepository events;
    private final CalendarEventTeacherRepository teachers;
    private final CalendarReferenceMapsQueryUseCase references;
    private final CalendarEventResponseAssembler responses;

    public CalendarEventDetailQueryUseCase(
            CalendarEventRepository events,
            CalendarEventTeacherRepository teachers,
            CalendarReferenceMapsQueryUseCase references,
            CalendarEventResponseAssembler responses) {
        this.events = events;
        this.teachers = teachers;
        this.references = references;
        this.responses = responses;
    }

    public CalendarEventResponse detail(String tenantId, String eventId) {
        CalendarEvent event = events.findByTenantIdAndId(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar event not found."));
        List<CalendarEventTeacher> assignments =
                teachers.findByTenantIdAndEventIdIn(tenantId, List.of(eventId));
        return responses.response(
                event,
                assignments,
                references.load(tenantId, List.of(event.getSchoolId()), List.of(event), assignments));
    }
}
