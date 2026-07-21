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

        String preview = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/calendar/internal/bulk/CalendarBulkPreviewUseCase.java"));
        String create = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/calendar/internal/bulk/CalendarBulkCreateUseCase.java"));
        assertThat(preview).contains("references.load(");
        assertThat(create).contains("referenceData.load(");
        assertThat(preview + create)
                .doesNotContain("findByTenantIdAndSchoolIdOrderByNameAsc(tenantId, schoolId)")
                .doesNotContain("findTop500ByTenantIdAndStatusIgnoreCaseOrderByNameAsc(tenantId");
    }
}
