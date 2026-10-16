package com.erg.ergspringboot.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OrganizationBoundaryContractTest {

    @Test
    void publicOrganizationAndSchoolBoundariesDoNotExposePersistenceTypes() throws Exception {
        String organization = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/organization/OrganizationOperations.java"));
        String schoolOperations = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/school/SchoolOperations.java"));
        String schoolQueries = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/school/SchoolQueryOperations.java"));
        assertThat(organization).contains("OrganizationEducationUnitQuery")
                .doesNotContain("EducationUnitRepository", "model.organization", "@Entity");
        assertThat(schoolOperations).doesNotContain("Repository", "model.", "@Entity");
        assertThat(schoolQueries).doesNotContain("Repository", "model.", "@Entity");
    }

    @Test
    void legacyEducationUnitServiceIsAnExplicitCompatibilityAdapter() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/organization/EducationUnitService.java"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/controller/organization/EducationUnitController.java"));
        assertThat(service).contains("@Deprecated", "OrganizationEducationUnitQuery");
        assertThat(controller).contains("OrganizationOperations").doesNotContain("EducationUnitService");
    }
}
