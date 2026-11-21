package com.erg.ergspringboot.calendar.internal.bulk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CalendarBulkLockSqlTest {

    @Test
    void oneStatementTakesEveryTeacherKeyInOrder() {
        String sql = CalendarBulkCreateUseCase.tryLocksSql(List.of(7L, 9L, 12L));

        assertThat(sql).contains("array[7,9,12]").contains("order by n").contains("bool_and(got)");
        assertThat(sql.split("pg_try_advisory_xact_lock", -1)).hasSize(2);
    }

    @Test
    void teacherKeysDoNotAliasAcrossTeachersOrTenants() {
        assertThat(CalendarBulkCreateUseCase.teacherLockId("t1", "a")).isNotEqualTo(CalendarBulkCreateUseCase.teacherLockId("t1", "b"));
        assertThat(CalendarBulkCreateUseCase.teacherLockId("t1", "a")).isNotEqualTo(CalendarBulkCreateUseCase.teacherLockId("t2", "a"));
    }
}
