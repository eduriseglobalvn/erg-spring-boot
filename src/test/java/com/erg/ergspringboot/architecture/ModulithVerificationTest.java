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
        assertThat(modules.getModuleByName("crm")).isPresent();
        assertThat(modules.getModuleByName("elearning")).isPresent();
        assertThat(modules.getModuleByName("reporting")).isPresent();
        assertThat(modules.getModuleByName("authorization")).isPresent();
        assertThat(modules.getModuleByName("users")).isPresent();
        assertThat(modules.getModuleByName("auth")).isPresent();
        assertThat(modules.getModuleByName("lms")).isPresent();
        assertThat(modules.getModuleByName("lcms")).isPresent();
        assertThat(modules.getModuleByName("curriculum")).isPresent();
        assertThat(modules.getModuleByName("learningcontent")).isPresent();
        assertThat(modules.getModuleByName("notifications")).isPresent();
        assertThat(modules.getModuleByName("organization")).isPresent();
        assertThat(modules.getModuleByName("school")).isPresent();
        assertThat(modules.getModuleByName("audit")).isPresent();
        assertThat(modules.getModuleByName("learningoutcomes")).isPresent();
        assertThat(modules.getModuleByName("learningstandards")).isPresent();
        modules.verify();
    }
}
