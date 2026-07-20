package com.erg.ergspringboot.config.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.erg.ergspringboot.platform.performance.RequestExecutionStatementInspector;
import javax.sql.DataSource;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

class JpaPersistenceSupportTest {

    @Test
    void registersStatementInspectorAsAnInstanceForNativeImages() {
        var factory = JpaPersistenceSupport.entityManagerFactory(
                "test",
                mock(DataSource.class),
                new JpaProperties(),
                PersistenceManagedTypes.of("com.erg.ergspringboot.model.auth.AuthSession")
        );

        assertThat(factory.getJpaPropertyMap().get(AvailableSettings.STATEMENT_INSPECTOR))
                .isInstanceOf(RequestExecutionStatementInspector.class);
    }
}
