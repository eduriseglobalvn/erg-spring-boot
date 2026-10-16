package com.erg.ergspringboot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductionCapabilityStubContractTest {

    @Test
    void publishedCalendarExcelImportIsAStableCapabilityResponse() throws Exception {
        String rest = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/controller/calendar/rest/CalendarRestController.java"));
        String graphql = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/controller/calendar/graphql/CalendarMutationGraphQlController.java"));
        String service = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/calendar/CalendarExcelImportService.java"));

        assertThat(rest).contains("NotImplementedException", "CALENDAR_EXCEL_IMPORT_NOT_IMPLEMENTED")
                .doesNotContain("throw new UnsupportedOperationException");
        assertThat(graphql).contains("NotImplementedException", "CALENDAR_EXCEL_IMPORT_NOT_IMPLEMENTED")
                .doesNotContain("throw new UnsupportedOperationException");
        assertThat(service).contains("Profile(\"prototype\")", "@Deprecated", "NotImplementedException")
                .doesNotContain("throw new UnsupportedOperationException");
    }
}
