package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CalendarBulkReferenceDataArchitectureTest {

    @Test
    void bulkCommandsShareAnInternalReferenceDataOwner() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.calendar.internal.bulk.CalendarBulkReferenceDataUseCase"))
                .doesNotThrowAnyException();

        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/calendar/CalendarCommandService.java"));
        assertThat(facade)
                .contains("bulkReferenceData.load(")
                .doesNotContain("findByTenantIdAndSchoolIdOrderByNameAsc(tenantId, schoolId)")
                .doesNotContain("findTop500ByTenantIdAndStatusIgnoreCaseOrderByNameAsc(tenantId");
    }
}
