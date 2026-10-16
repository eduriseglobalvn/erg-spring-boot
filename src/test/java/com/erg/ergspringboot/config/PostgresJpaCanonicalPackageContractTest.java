package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import com.erg.ergspringboot.config.persistence.JpaRegistrationCatalog;

class PostgresJpaCanonicalPackageContractTest {

    @Test
    void canonicalCurriculumAndLearningContentRegistrationIsCataloged() {
        assertThat(JpaRegistrationCatalog.enabledEntityPackages())
                .contains("com.erg.ergspringboot.model.curriculum")
                .contains("com.erg.ergspringboot.model.learningcontent");
        assertThat(JpaRegistrationCatalog.REPOSITORY_ROOT)
                .isEqualTo("com.erg.ergspringboot.repository");
    }
}
