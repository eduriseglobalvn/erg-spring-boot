package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CalendarAuthorizationPolicyContractTest {

    private static final Path REST = Path.of(
            "src/main/java/com/erg/ergspringboot/controller/calendar/rest/CalendarRestController.java");
    private static final Path LCMS_QUERY = Path.of(
            "src/main/java/com/erg/ergspringboot/controller/calendar/graphql/CalendarLcmsQueryGraphQlController.java");
    private static final Path LMS_QUERY = Path.of(
            "src/main/java/com/erg/ergspringboot/controller/calendar/graphql/CalendarLmsQueryGraphQlController.java");
    private static final Path MUTATION = Path.of(
            "src/main/java/com/erg/ergspringboot/controller/calendar/graphql/CalendarMutationGraphQlController.java");

    @Test
    void restAndGraphQlUseTheSameModuleOwnedTypedPolicies() throws Exception {
        String rest = Files.readString(REST);
        String lcmsQuery = Files.readString(LCMS_QUERY);
        String lmsQuery = Files.readString(LMS_QUERY);
        String mutation = Files.readString(MUTATION);

        assertThat(rest)
                .contains("@CalendarRead", "@CalendarCreate", "@CalendarUpdate",
                        "@CalendarDelete", "@CalendarPublish", "@CalendarImport")
                .doesNotContain("assertPermission(", "private boolean isAllowed(");
        assertThat(lcmsQuery).contains("@CalendarRead");
        assertThat(lmsQuery).contains("@CalendarSelfRead");
        assertThat(mutation)
                .contains("@CalendarCreate", "@CalendarUpdate", "@CalendarDelete",
                        "@CalendarPublish", "@CalendarImport");
    }

    @Test
    void eachTypedPolicyDelegatesToTheSingleDenyAwareEvaluator() throws Exception {
        assertPolicy("CalendarRead", "lms.schedule.read", "lms.schedule.manage", "lms.manage");
        assertPolicy("CalendarSelfRead", "lms.schedule.read_self", "lms.schedule.read",
                "lms.schedule.manage", "lms.manage");
        assertPolicy("CalendarCreate", "lms.schedule.create", "lms.schedule.manage", "lms.manage");
        assertPolicy("CalendarUpdate", "lms.schedule.update", "lms.schedule.manage", "lms.manage");
        assertPolicy("CalendarDelete", "lms.schedule.delete", "lms.schedule.manage", "lms.manage");
        assertPolicy("CalendarPublish", "lms.schedule.publish", "lms.schedule.manage", "lms.manage");
        assertPolicy("CalendarImport", "lms.schedule.import", "lms.schedule.manage", "lms.manage");
    }

    @Test
    void centralFilterChainDoesNotOverrideModuleOwnedCalendarPolicies() throws Exception {
        String securityConfig = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java"));

        assertThat(securityConfig).doesNotContain("/api/v1/calendar");
    }

    private void assertPolicy(String annotation, String operationPermission, String... compatiblePermissions)
            throws Exception {
        Path source = Path.of("src/main/java/com/erg/ergspringboot/calendar/authorization/"
                + annotation + ".java");
        assertThat(source).isRegularFile();
        String content = Files.readString(source);
        assertThat(content)
                .contains("@PreAuthorize", "@interface " + annotation,
                        "@methodAuthorization.isAllowedAny(authentication", operationPermission)
                .contains(compatiblePermissions);
    }
}
