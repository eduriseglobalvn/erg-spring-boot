package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import com.erg.ergspringboot.config.persistence.JpaRegistrationCatalog;

class SchoolJpaRegistrationTest {

    @Test
    void postgresPersistenceRegistersSchoolEntitiesThroughCatalog() {
        assertThat(JpaRegistrationCatalog.enabledEntityPackages())
                .contains("com.erg.ergspringboot.school.persistence");
        assertThat(JpaRegistrationCatalog.REPOSITORY_SCAN_ROOT)
                .isEqualTo("com.erg.ergspringboot");
    }
}
