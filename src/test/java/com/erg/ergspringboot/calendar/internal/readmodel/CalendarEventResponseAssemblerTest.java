package com.erg.ergspringboot.calendar.internal.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.persistence.CalendarEvent;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarEventResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarTeacherAssignmentResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalendarEventResponseAssemblerTest {

    private final CalendarEventResponseAssembler assembler = new CalendarEventResponseAssembler();

    private static CalendarEvent eventAtUtc(String start, String end) {
        CalendarEvent event = new CalendarEvent();
        event.setId("e1");
        event.setSchoolId("s1");
        event.setAcademicClassId("c1");
        event.setStartAt(OffsetDateTime.parse(start));
        event.setEndAt(OffsetDateTime.parse(end));
        event.setStatus("PUBLISHED");
        return event;
    }

    @Test
    void dateAndTimesFollowVietnamZoneNotTheStoredOffset() {
        // 18:30Z on 13/11 is 01:30 on 14/11 in Vietnam; toLocalDate() on the UTC value used to give 13/11.
        CalendarEvent event = eventAtUtc("2026-11-13T18:30:00Z", "2026-11-13T19:15:00Z");

        CalendarEventResponse response = assembler.response(event, List.of(),
                new CalendarReferenceMaps(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()));

        assertThat(response.date()).isEqualTo("2026-11-14");
        assertThat(response.startTime()).isEqualTo("01:30");
        assertThat(response.endTime()).isEqualTo("02:15");
    }

    @Test
    void assistantViewerSeesMainTeacherAndMainViewerSeesAssistant() {
        CalendarEventResponse base = new CalendarEventResponse("e1", "b1", "t", null, null, null, null, null,
                "s1", "S", "c1", "9A4", null, null, null, null, null, null, 1, 1,
                List.of(new CalendarTeacherAssignmentResponse("u-main", "Q.Vương", "MAIN", null),
                        new CalendarTeacherAssignmentResponse("u-ta", "Lan Anh", "ASSISTANT", null)),
                "PUBLISHED", null, null);

        assertThat(base.myRole()).isEqualTo("MAIN");
        assertThat(base.assistantName()).isEqualTo("Lan Anh");

        CalendarEventResponse asAssistant = base.withViewer("u-ta");
        assertThat(asAssistant.myRole()).isEqualTo("ASSISTANT");
        assertThat(asAssistant.mainTeacherName()).isEqualTo("Q.Vương");
        assertThat(asAssistant.assistantName()).isNull();

        CalendarEventResponse asMain = base.withViewer("u-main");
        assertThat(asMain.myRole()).isEqualTo("MAIN");
        assertThat(asMain.mainTeacherName()).isNull();
        assertThat(asMain.assistantName()).isEqualTo("Lan Anh");
    }
}
