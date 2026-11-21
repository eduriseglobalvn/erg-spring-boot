package com.erg.ergspringboot.calendar.internal.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.calendar.CalendarBatchCancelled;
import com.erg.ergspringboot.calendar.CalendarBatchPublished;
import com.erg.ergspringboot.calendar.CalendarSlotsChanged;
import com.erg.ergspringboot.calendar.CalendarSlotsChanged.RemovedPerson;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacherRepository;
import com.erg.ergspringboot.platform.security.PrincipalCache;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalendarTeacherScopeEvictorTest {

    private final CalendarEventTeacherRepository links = mock(CalendarEventTeacherRepository.class);
    private final PrincipalCache principals = mock(PrincipalCache.class);
    private final CalendarTeacherScopeEvictor evictor = new CalendarTeacherScopeEvictor(links, principals);

    @Test
    void publishingABatchEvictsEveryTeacherOnIt() {
        when(links.findTeacherIdsByBatch("t1", "b1")).thenReturn(List.of("u1", "u2"));

        evictor.onPublished(new CalendarBatchPublished("e", "t1", "b1", "admin", OffsetDateTime.now(), 1));

        verify(principals).evictUser("u1");
        verify(principals).evictUser("u2");
        verifyNoMoreInteractions(principals);
    }

    @Test
    void cancellingABatchEvictsItsTeachers() {
        when(links.findTeacherIdsByBatch("t1", "b1")).thenReturn(List.of("u1"));

        evictor.onCancelled(new CalendarBatchCancelled("e", "t1", "b1", "admin", OffsetDateTime.now(), 1, 3, 2L));

        verify(principals).evictUser("u1");
    }

    @Test
    void aSlotChangeEvictsTheCurrentTeachersAndThePersonWhoLostThePeriod() {
        when(links.findTeacherIdsByEventIds("t1", List.of("ev1"))).thenReturn(List.of("newTeacher"));

        evictor.onSlotsChanged(new CalendarSlotsChanged("e", "t1", "b1", "SWAP", List.of("ev1"),
                List.of(new RemovedPerson("ev1", "oldTeacher", "Old", "MAIN")), "admin", OffsetDateTime.now(), 1, 1L));

        verify(principals).evictUser("newTeacher");
        verify(principals).evictUser("oldTeacher");
    }

    @Test
    void aFailingLookupNeverFailsTheTimetableWrite() {
        when(links.findTeacherIdsByBatch("t1", "b1")).thenThrow(new IllegalStateException("db"));

        evictor.onPublished(new CalendarBatchPublished("e", "t1", "b1", "admin", OffsetDateTime.now(), 1));

        verify(principals, never()).evictUser(org.mockito.ArgumentMatchers.anyString());
    }
}
