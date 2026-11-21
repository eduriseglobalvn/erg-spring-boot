package com.erg.ergspringboot.calendar.internal.readmodel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.calendar.persistence.CalendarEvent;
import com.erg.ergspringboot.calendar.persistence.CalendarEventRepository;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A malformed calendar date is a client error (400 / GraphQL BAD_REQUEST), never a 500. */
class CalendarWorkspaceDateValidationTest {

    private final CalendarWorkspaceQueryUseCase useCase =
            new CalendarWorkspaceQueryUseCase(null, null, null, null, null, null, null, null, null);

    @Test
    void lcmsRejectsAMalformedDate() {
        assertThatThrownBy(() -> useCase.lcms("erg", List.of(), "2026-13-45", "2026-11-30", "month",
                List.of(), List.of(), List.of(), List.of(), List.of(), null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void teacherRejectsAMalformedDate() {
        assertThatThrownBy(() -> useCase.teacher("erg", "usr-t", "13/11/2026", "2026-11-30", "week",
                List.of(), List.of(), List.of(), List.of(), List.of(), null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void studentRejectsAMissingDateBeforeLookingUpTheStudent() {
        assertThatThrownBy(() -> useCase.student("erg", "usr-s", null, "2026-11-30", "week",
                List.of(), List.of(), List.of(), null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void anOverfullRangeIsRefusedWithAStableCodeBeforeAnyExpansion() {
        List<CalendarEvent> tooMany = Collections.nCopies(CalendarWorkspaceQueryUseCase.MAX_WORKSPACE_EVENTS + 1, new CalendarEvent());
        CalendarEventRepository repo = org.mockito.Mockito.mock(CalendarEventRepository.class,
                invocation -> "findLcmsWorkspaceEvents".equals(invocation.getMethod().getName()) ? tooMany : null);
        CalendarWorkspaceQueryUseCase full = new CalendarWorkspaceQueryUseCase(repo, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> full.lcms("erg", List.of(), "2026-11-01", "2026-11-30", "month",
                List.of(), List.of(), List.of(), List.of(), List.of(), null))
                .isInstanceOf(TeachingScheduleException.class)
                .hasFieldOrPropertyWithValue("code", "CALENDAR_RANGE_TOO_LARGE");
    }
}
