package com.erg.ergspringboot.calendar.internal.readmodel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.calendar.persistence.CalendarBatchRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEventRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class CalendarScheduleBatchQueryUseCaseTest {

    /** The JPQL compares upper(status) with the argument, so a lower-case filter never matched any batch. */
    @Test
    void statusFilterIsSentUpperCase() {
        CalendarBatchRepository batches = mock(CalendarBatchRepository.class);
        when(batches.findTeachingScheduleBatchPage(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        new CalendarScheduleBatchQueryUseCase(batches, mock(CalendarEventRepository.class))
                .list("erg", null, "draft", 0, 20);
        verify(batches).findTeachingScheduleBatchPage(eq("erg"), eq(null), eq("DRAFT"), any(Pageable.class));
    }
}
