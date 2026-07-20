package com.erg.ergspringboot.repository.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class CalendarEventTeacherRepositoryTest {

    @Test
    void findTeacherColorKeysInRangeOnlyReadsAssignmentsInsideVisibleCalendarRange() throws Exception {
        Method method = CalendarEventTeacherRepository.class.getMethod(
                "findTeacherColorKeysInRange",
                String.class,
                List.class,
                OffsetDateTime.class,
                OffsetDateTime.class,
                List.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("SELECT new com.erg.ergspringboot.repository.calendar.projection.CalendarTeacherColorProjection")
                .contains("FROM CalendarEventTeacher et JOIN CalendarEvent e ON e.id = et.eventId")
                .contains("e.tenantId = :tenantId")
                .contains("et.tenantId = :tenantId")
                .contains("e.schoolId IN :schoolIds")
                .contains("e.startAt < :to")
                .contains("e.endAt > :from")
                .contains("e.status IN :statuses")
                .doesNotContain("1970")
                .doesNotContain("2100");
    }
}
