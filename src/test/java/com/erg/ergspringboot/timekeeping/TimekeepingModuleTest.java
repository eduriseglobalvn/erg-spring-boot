package com.erg.ergspringboot.timekeeping;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.ErgSpringApplication;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
@ActiveProfiles("test")
// The slice runs no Flyway (FlywayConfig is outside the module); TimekeepingRepositoryIT covers the schema.
@TestPropertySource(properties = "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=none")
class TimekeepingModuleTest {

    static {
        System.setProperty("spring.modulith.detection-strategy", "explicitly-annotated");
    }

    @Autowired
    private ApplicationContext context;

    @Test
    void moduleBootstrapsWithItsFacadeAndClock() {
        assertThat(context.getBean(TimekeepingOperations.class)).isNotNull();
        assertThat(context.getBean(Clock.class)).isNotNull();
    }

    @Test
    void moduleDependsOnlyOnDeclaredModules() {
        ApplicationModules modules = ApplicationModules.of(ErgSpringApplication.class);
        ApplicationModule timekeeping = modules.getModuleByName("timekeeping").orElseThrow();

        timekeeping.verifyDependencies(modules);
    }
}
