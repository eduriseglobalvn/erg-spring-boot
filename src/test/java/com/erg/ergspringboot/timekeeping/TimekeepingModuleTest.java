package com.erg.ergspringboot.timekeeping;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.notifications.NotificationAdminOperations;
import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.users.persistence.EmployeeProfileRepository;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.organization.EducationUnitRepository;
import com.erg.ergspringboot.users.persistence.organization.UserAccessScopeRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Dependency rules are verified for every module in ModulithVerificationTest; this checks the module boots alone. */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
@ActiveProfiles("test")
// The slice runs no Flyway (FlywayConfig is outside the module); TimekeepingRepositoryIT covers the schema.
@TestPropertySource(properties = "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=none")
class TimekeepingModuleTest {

    // Collaborators from other modules and from platform (not a module), outside the standalone slice.
    @MockitoBean CalendarReadPort calendar;
    @MockitoBean NotificationAdminOperations notifications;
    @MockitoBean EducationUnitRepository educationUnits;
    @MockitoBean UserAccessScopeRepository accessScopes;
    @MockitoBean ClusterJobLock jobLock;
    @MockitoBean ErgPolicyService policy;
    @MockitoBean UserRepository users;
    @MockitoBean EmployeeProfileRepository profiles;

    @Autowired
    private ApplicationContext context;

    @Test
    void moduleBootstrapsWithItsFacadeAndClock() {
        assertThat(context.getBean(TimekeepingOperations.class)).isNotNull();
        assertThat(context.getBean(Clock.class)).isNotNull();
    }
}
