package com.erg.ergspringboot.calendar.internal.bulk;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkCreateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkRowRequest;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalendarBulkEventCapTest {

    private static CalendarBulkRowRequest row(int classes) {
        return new CalendarBulkRowRequest("r", Collections.nCopies(classes, "c"), 1, "MORNING", 1, 1,
                null, null, null, null, null, null, List.of(), List.of(), null);
    }

    private static CalendarBulkCreateRequest request(int weeks, CalendarBulkRowRequest... rows) {
        return new CalendarBulkCreateRequest("t", null, "s", "2026", "2026-09-07", weeks, null, false, null, List.of(rows));
    }

    @Test
    void exactlyTheCapIsAccepted() {
        assertThatCode(() -> CalendarBulkCreateUseCase.requireWithinEventCap(request(50, row(20), row(20), row(20), row(20),
                row(20), row(20), row(20), row(20), row(20), row(20), row(20), row(20), row(20), row(20), row(20),
                row(20), row(20), row(20), row(20), row(20)))).doesNotThrowAnyException();
    }

    @Test
    void oneEventOverTheCapIsRefusedWithAStableCode() {
        assertThatThrownBy(() -> CalendarBulkCreateUseCase.requireWithinEventCap(request(52, row(20), row(20), row(20),
                row(20), row(20), row(20), row(20), row(20), row(20), row(20), row(20), row(20), row(20), row(20),
                row(20), row(20), row(20), row(20), row(20), row(20), row(1))))
                .isInstanceOf(TeachingScheduleException.class)
                .hasFieldOrPropertyWithValue("code", "CALENDAR_BULK_TOO_LARGE");
    }
}
