package com.erg.ergspringboot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.ErgSpringApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.modulith.core.ApplicationModules;

class ModulithVerificationTest {

    @BeforeAll
    static void useExplicitModuleDetection() {
        System.setProperty("spring.modulith.detection-strategy", "explicitly-annotated");
    }

    @Test
    void migratedModulesAreRecognizedAndDependencyGraphIsValid() {
        ApplicationModules modules = ApplicationModules.of(ErgSpringApplication.class);

        assertThat(modules.getModuleByName("calendar")).isPresent();
        assertThat(modules.getModuleByName("reporting")).isPresent();
        assertThat(modules.getModuleByName("authorization")).isPresent();
        assertThat(modules.getModuleByName("users")).isPresent();
        assertThat(modules.getModuleByName("auth")).isPresent();
        assertThat(modules.getModuleByName("lms")).isPresent();
        modules.verify();
    }
}
