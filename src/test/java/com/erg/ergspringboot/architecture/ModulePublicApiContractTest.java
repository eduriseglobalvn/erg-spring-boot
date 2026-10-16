package com.erg.ergspringboot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.auth.AuthOperations;
import com.erg.ergspringboot.auth.internal.login.AuthLoginUseCase;
import com.erg.ergspringboot.auth.internal.refresh.AuthRefreshUseCase;
import com.erg.ergspringboot.auth.internal.security.AuthSecurityQueryUseCase;
import com.erg.ergspringboot.auth.internal.session.AuthSessionLifecycleUseCase;
import com.erg.ergspringboot.curriculum.CurriculumOperations;
import com.erg.ergspringboot.curriculum.internal.command.CurriculumCommandUseCase;
import com.erg.ergspringboot.curriculum.internal.impact.CurriculumImpactQueryUseCase;
import com.erg.ergspringboot.curriculum.internal.query.CurriculumTreeQueryUseCase;
import com.erg.ergspringboot.curriculum.internal.validation.CurriculumChainValidationUseCase;
import com.erg.ergspringboot.calendar.CalendarCommandPort;
import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.calendar.internal.command.CalendarCommandUseCase;
import com.erg.ergspringboot.calendar.internal.readmodel.CalendarReadUseCase;
import com.erg.ergspringboot.lcms.LcmsCommandPort;
import com.erg.ergspringboot.lcms.LcmsReadPort;
import com.erg.ergspringboot.lcms.internal.command.LcmsCommandUseCase;
import com.erg.ergspringboot.lcms.internal.readmodel.LcmsReadModelUseCase;
import com.erg.ergspringboot.lms.LmsRuntimePort;
import com.erg.ergspringboot.lms.LmsReadModelPort;
import com.erg.ergspringboot.lms.internal.runtime.LmsRuntimeUseCase;
import com.erg.ergspringboot.lms.internal.readmodel.LmsReadModelUseCase;
import com.erg.ergspringboot.reporting.ReportJobPort;
import com.erg.ergspringboot.reporting.internal.ReportJobUseCase;
import com.erg.ergspringboot.authorization.AuthorizationCommandOperations;
import com.erg.ergspringboot.authorization.internal.access.AuthorizationAccessQueryUseCase;
import com.erg.ergspringboot.authorization.internal.assignment.AuthorizationDirectRoleUseCase;
import com.erg.ergspringboot.authorization.internal.catalog.AuthorizationPermissionCatalogUseCase;
import com.erg.ergspringboot.authorization.internal.override.AuthorizationPermissionOverrideUseCase;
import com.erg.ergspringboot.authorization.internal.roledefinition.AuthorizationRoleDefinitionUseCase;
import com.erg.ergspringboot.users.UserOperations;
import com.erg.ergspringboot.users.internal.create.UserProvisioningUseCase;
import com.erg.ergspringboot.users.internal.lifecycle.UserLifecycleUseCase;
import com.erg.ergspringboot.users.internal.password.UserPasswordUseCase;
import com.erg.ergspringboot.users.internal.profile.UserProfileUseCase;
import com.erg.ergspringboot.users.internal.query.UserQueryUseCase;
import com.erg.ergspringboot.users.internal.roleassignment.UserRoleAssignmentUseCase;
import com.erg.ergspringboot.users.internal.session.UserSessionUseCase;
import com.erg.ergspringboot.learningcontent.LearningContentOperations;
import com.erg.ergspringboot.learningcontent.LearningContentPort;
import com.erg.ergspringboot.learningcontent.internal.LegacyLearningContentAdapter;
import com.erg.ergspringboot.crm.CrmOperations;
import com.erg.ergspringboot.elearning.ElearningOperations;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ModulePublicApiContractTest {
    @Test
    void migratedOperationsWireUseCasesDirectlyAndLegacyServicesAreAdapters() throws Exception {
        assertThat(AuthOperations.class.getDeclaredConstructor(
                AuthLoginUseCase.class,
                AuthRefreshUseCase.class,
                AuthSessionLifecycleUseCase.class,
                AuthSecurityQueryUseCase.class,
                com.erg.ergspringboot.service.auth.AccountVerificationService.class))
                .isNotNull();
        assertThat(CurriculumOperations.class.getDeclaredConstructor(
                CurriculumTreeQueryUseCase.class,
                CurriculumImpactQueryUseCase.class,
                CurriculumChainValidationUseCase.class,
                CurriculumCommandUseCase.class))
                .isNotNull();
        assertThat(AuthorizationCommandOperations.class.getDeclaredConstructor(
                AuthorizationPermissionCatalogUseCase.class,
                AuthorizationRoleDefinitionUseCase.class,
                AuthorizationAccessQueryUseCase.class,
                AuthorizationDirectRoleUseCase.class,
                AuthorizationPermissionOverrideUseCase.class))
                .isNotNull();
        assertThat(UserOperations.class.getDeclaredConstructor(
                UserQueryUseCase.class,
                UserProfileUseCase.class,
                UserPasswordUseCase.class,
                UserSessionUseCase.class,
                UserProvisioningUseCase.class,
                UserLifecycleUseCase.class,
                UserRoleAssignmentUseCase.class))
                .isNotNull();
        assertThat(CalendarCommandUseCase.class).isAssignableTo(CalendarCommandPort.class);
        assertThat(CalendarReadUseCase.class).isAssignableTo(CalendarReadPort.class);
        assertThat(LcmsCommandUseCase.class).isAssignableTo(LcmsCommandPort.class);
        assertThat(LcmsReadModelUseCase.class).isAssignableTo(LcmsReadPort.class);
        assertThat(LmsRuntimeUseCase.class).isAssignableTo(LmsRuntimePort.class);
        assertThat(LmsReadModelUseCase.class).isAssignableTo(LmsReadModelPort.class);
        assertThat(ReportJobUseCase.class).isAssignableTo(ReportJobPort.class);
        assertThat(LegacyLearningContentAdapter.class).isAssignableTo(LearningContentPort.class);
        assertThat(LearningContentOperations.class.getDeclaredConstructor(LearningContentPort.class))
                .isNotNull();
        assertThat(CrmOperations.class.getDeclaredConstructor(
                com.erg.ergspringboot.service.crm.CrmWorkspaceService.class,
                com.erg.ergspringboot.service.crm.CrmSchoolService.class))
                .isNotNull();
        assertThat(ElearningOperations.class.getDeclaredConstructor(
                com.erg.ergspringboot.service.elearning.readmodel.StudentDashboardReadService.class,
                com.erg.ergspringboot.service.elearning.engagement.StudentEngagementService.class))
                .isNotNull();

        String authAdapter = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/auth/AuthService.java"));
        String curriculumAdapter = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/curriculum/CurriculumService.java"));
        assertThat(authAdapter).contains("@Deprecated", "AuthOperations operations")
                .doesNotContain(".internal.");
        assertThat(curriculumAdapter).contains("@Deprecated", "CurriculumOperations operations")
                .doesNotContain(".internal.");
        String authorizationAdapter = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/authorization/AuthorizationService.java"));
        assertThat(authorizationAdapter).contains("@Deprecated", "AuthorizationCommandOperations operations")
                .doesNotContain(".internal.");
        String usersAdapter = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/users/UserService.java"));
        assertThat(usersAdapter).contains("@Deprecated", "UserOperations operations")
                .doesNotContain(".internal.");
        assertThat(Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/calendar/CalendarCommandService.java")))
                .contains("@Deprecated", "CalendarCommandPort operations")
                .doesNotContain(".internal.");
        assertThat(Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/calendar/CalendarReadService.java")))
                .contains("@Deprecated", "CalendarReadPort operations")
                .doesNotContain(".internal.");
        assertThat(Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/lcms/LcmsOperations.java")))
                .contains("LcmsCommandPort commands")
                .doesNotContain("service.quiz", "service.lcms");
        assertThat(Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/lcms/LcmsQueryOperations.java")))
                .contains("LcmsReadPort reads")
                .doesNotContain("service.quiz", "service.lcms");
        assertThat(Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/lms/LmsRuntimeService.java")))
                .contains("@Deprecated", "LmsRuntimePort operations")
                .doesNotContain(".internal.");
        assertThat(Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/lms/readmodel/LmsReadModelService.java")))
                .contains("@Deprecated", "LmsReadModelPort operations")
                .doesNotContain(".internal.");
        assertThat(Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/reporting/ReportJobService.java")))
                .contains("@Deprecated", "ReportJobPort operations")
                .doesNotContain(".internal.");
        String learningContentAdapter = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/learningcontent/internal/LegacyLearningContentAdapter.java"));
        assertThat(learningContentAdapter).contains("implements LearningContentPort", "LearningContentService legacy")
                .doesNotContain("public ContentItem ", "public ContentAsset ", "public TopicContentItem ");
        assertThat(Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/learningcontent/LearningContentService.java")))
                .contains("@Deprecated", "Legacy persistence adapter");
        assertThat(Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/elearning/engagement/StudentEngagementService.java")))
                .contains("@Deprecated", "ElearningOperations");
        assertThat(Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/elearning/readmodel/StudentDashboardReadService.java")))
                .contains("@Deprecated", "ElearningOperations");
    }

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
