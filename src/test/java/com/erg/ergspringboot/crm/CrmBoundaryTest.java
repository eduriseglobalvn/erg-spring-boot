package com.erg.ergspringboot.crm;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.controller.crm.CrmSchoolController;
import com.erg.ergspringboot.controller.crm.CrmWorkspaceController;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CrmBoundaryTest {

    @Test
    void controllersDependOnlyOnThePublicFacade() {
        assertThat(CrmWorkspaceController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(CrmOperations.class));
        assertThat(CrmSchoolController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(CrmOperations.class));
    }

    @Test
    void publicCrmPackageDoesNotImportPersistenceTypes() throws Exception {
        Path root = Path.of("src/main/java/com/erg/ergspringboot/crm");
        try (var files = Files.walk(root)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .noneMatch(source -> source.contains("com.erg.ergspringboot.model.")
                            || source.contains("com.erg.ergspringboot.repository.")
                            || source.contains("jakarta.persistence.")))
                    .isTrue();
        }
    }

    @Test
    void legacyServicesAreExplicitlyAdapters() throws Exception {
        assertThat(Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/crm/CrmWorkspaceService.java")))
                .contains("@Deprecated", "legacy CRM");
        assertThat(Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/crm/CrmSchoolService.java")))
                .contains("@Deprecated", "legacy CRM");
    }
}
