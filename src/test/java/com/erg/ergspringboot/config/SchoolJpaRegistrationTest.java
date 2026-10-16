package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import com.erg.ergspringboot.config.persistence.JpaRegistrationCatalog;

class SchoolJpaRegistrationTest {

    @Test
    void postgresPersistenceRegistersSchoolEntitiesThroughCatalog() {
        assertThat(JpaRegistrationCatalog.enabledEntityPackages())
                .contains("com.erg.ergspringboot.model.school");
        assertThat(JpaRegistrationCatalog.REPOSITORY_ROOT)
                .startsWith("com.erg.ergspringboot.repository");
    }
}
