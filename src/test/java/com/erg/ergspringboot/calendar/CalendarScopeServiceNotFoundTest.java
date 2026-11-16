package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.calendar.persistence.CalendarBatchRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEventRepository;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CalendarScopeServiceNotFoundTest {

    private final CalendarEventRepository events = mock(CalendarEventRepository.class);
    private final CalendarBatchRepository batches = mock(CalendarBatchRepository.class);
    private final CalendarScopeService scope =
            new CalendarScopeService(mock(GraphQlAccessSupport.class), events, batches);

    @Test
    void missingEventAndBatchAreNotFoundNotIllegalArgument() {
        when(events.findByTenantIdAndId("t", "e")).thenReturn(Optional.empty());
        when(batches.findByTenantIdAndId("t", "b")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scope.requireEvent(null, "t", "e"))
                .isInstanceOfSatisfying(TeachingScheduleException.class,
                        error -> assertThat(error.getCode()).isEqualTo("NOT_FOUND"));
        assertThatThrownBy(() -> scope.requireBatch(null, "t", "b"))
                .isInstanceOfSatisfying(TeachingScheduleException.class,
                        error -> assertThat(error.getStatus().value()).isEqualTo(404));
    }
}
