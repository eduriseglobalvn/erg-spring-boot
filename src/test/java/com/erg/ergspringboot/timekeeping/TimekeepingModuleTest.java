package com.erg.ergspringboot.timekeeping;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** Dependency rules are verified for every module in ModulithVerificationTest; this checks the module boots alone. */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
@ActiveProfiles("test")
// The slice runs no Flyway (FlywayConfig is outside the module); TimekeepingRepositoryIT covers the schema.
@TestPropertySource(properties = "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=none")
class TimekeepingModuleTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void moduleBootstrapsWithItsFacadeAndClock() {
        assertThat(context.getBean(TimekeepingOperations.class)).isNotNull();
        assertThat(context.getBean(Clock.class)).isNotNull();
    }
}
