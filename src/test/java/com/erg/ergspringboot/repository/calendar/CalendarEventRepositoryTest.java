package com.erg.ergspringboot.repository.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

class CalendarEventRepositoryTest {

    @Test
    void findTeacherCalendarEventsJoinsCalendarEventTeacher() throws Exception {
        Method method = CalendarEventRepository.class.getMethod(
                "findTeacherCalendarEvents",
                String.class,
                String.class,
                OffsetDateTime.class,
                OffsetDateTime.class,
                List.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("SELECT e FROM CalendarEvent e")
                .contains("JOIN CalendarEventTeacher et ON e.id = et.eventId")
                .contains("e.tenantId = :tenantId")
                .contains("et.teacherUserId = :teacherUserId")
                .contains("e.startAt < :to")
                .contains("e.endAt > :from")
                .contains("e.status IN :statuses")
                .contains("ORDER BY e.startAt")
                .doesNotContain("JOIN FETCH")
                .doesNotContain("select et");
    }

    @Test
    void findTeacherTimeConflictsDetectsOverlapExcludingCancelled() throws Exception {
        Method method = CalendarEventRepository.class.getMethod(
                "findTeacherTimeConflicts",
                String.class,
                String.class,
                OffsetDateTime.class,
                OffsetDateTime.class
        );
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("SELECT e FROM CalendarEvent e")
                .contains("JOIN CalendarEventTeacher et ON e.id = et.eventId")
                .contains("e.tenantId = :tenantId")
                .contains("et.teacherUserId = :teacherUserId")
                .contains("e.startAt < :endAt")
                .contains("e.endAt > :startAt")
                .contains("e.status <> 'CANCELLED'")
                .doesNotContain("e.status NOT IN")
                .doesNotContain("e.status in")
                .doesNotContain(":status");
    }

    @Test
    void countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNotIsDerivedQuery() throws Exception {
        Method method = CalendarEventRepository.class.getMethod(
                "countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot",
                String.class,
                String.class,
                OffsetDateTime.class,
                OffsetDateTime.class,
                String.class
        );
        Query query = method.getAnnotation(Query.class);

        // Derived query: no @Query annotation, uses Spring Data method naming convention
        assertThat(query).isNull();
        assertThat(method.getReturnType()).isEqualTo(long.class);
    }

    @Test
    void countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNotIsDerivedQuery()
            throws Exception {
        Method method = CalendarEventRepository.class.getMethod(
                "countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot",
                String.class,
                String.class,
                String.class,
                OffsetDateTime.class,
                OffsetDateTime.class,
                String.class
        );
        Query query = method.getAnnotation(Query.class);

        // Derived query with IgnoreCase: no @Query annotation, uses Spring Data derived method convention
        assertThat(query).isNull();
        assertThat(method.getReturnType()).isEqualTo(long.class);
    }

    @Test
    void updateStatusBatchIsModifyingQuery() throws Exception {
        Method method = CalendarEventRepository.class.getMethod(
                "updateStatusBatch",
                String.class,
                List.class,
                String.class,
                String.class,
                OffsetDateTime.class
        );
        Query query = method.getAnnotation(Query.class);
        Modifying modifying = method.getAnnotation(Modifying.class);

        assertThat(modifying).isNotNull();
        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("UPDATE CalendarEvent e")
                .contains("SET e.status = :status")
                .contains("e.tenantId = :tenantId")
                .contains("e.updatedBy = :updatedBy")
                .contains("e.updatedAt = :updatedAt")
                .contains("WHERE e.tenantId = :tenantId AND e.id IN :ids")
                .doesNotContain("select")
                .doesNotContain("join");
        assertThat(method.getReturnType()).isEqualTo(int.class);
    }
}
