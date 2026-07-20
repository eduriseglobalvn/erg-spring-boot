package com.erg.ergspringboot.controller.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EmployeeSecurityFeedContractTest {

    @Test
    void exposesUnifiedPagedFeedAndEnrichedEmployeeList() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/controller/users/AdminEmployeeController.java"));
        String dto = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/common/dto/users/EmployeeDtos.java"));

        assertThat(controller)
                .contains("@GetMapping(\"/security-feed\")")
                .contains("employees.listUnifiedFeed(")
                .contains("employees.listEmployees(")
                .contains("employees.createEmployee(");
        assertThat(dto).contains(
                "record EmployeeListItemResponse(",
                "record CreateEmployeeRequest(",
                "String employeeCode",
                "String departmentId",
                "String jobTitle",
                "int activeDeviceCount",
                "List<String> activePlatforms",
                "String riskCode",
                "record SecurityFeedItemResponse(",
                "String sourceType",
                "String userId",
                "String userName"
        );
    }
}
