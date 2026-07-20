package com.erg.ergspringboot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ModulePublicApiContractTest {
    @Test
    void calendarPublicPackageContainsOnlyFacadeAndEvents() throws Exception {
        String facade = Files.readString(Path.of("src/main/java/com/erg/ergspringboot/calendar/CalendarOperations.java"));
        String event = Files.readString(Path.of("src/main/java/com/erg/ergspringboot/calendar/CalendarBatchPublished.java"));

        assertThat(facade).contains("public class CalendarOperations").doesNotContain("@Entity", "Repository");
        assertThat(event).contains("public record CalendarBatchPublished").doesNotContain("@Entity", "Repository");
    }

    @Test
    void reportingPublicFacadeDoesNotExposePersistenceTypes() throws Exception {
        String facade = Files.readString(Path.of("src/main/java/com/erg/ergspringboot/reporting/ReportingOperations.java"));

        assertThat(facade).contains("public class ReportingOperations")
                .doesNotContain("@Entity", "Repository", "public Assignment", "public ReportJob ");
    }
}
