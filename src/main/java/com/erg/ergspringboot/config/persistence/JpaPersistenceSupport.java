package com.erg.ergspringboot.config.persistence;

import com.erg.ergspringboot.platform.performance.RequestExecutionStatementInspector;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

final class JpaPersistenceSupport {

    private JpaPersistenceSupport() {
    }

    static LocalContainerEntityManagerFactoryBean entityManagerFactory(
            String persistenceUnit,
            DataSource dataSource,
            JpaProperties jpaProperties,
            PersistenceManagedTypes managedTypes
    ) {
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(jpaProperties.isShowSql());
        vendorAdapter.setGenerateDdl(false);
        if (jpaProperties.getDatabase() != null) {
            vendorAdapter.setDatabase(jpaProperties.getDatabase());
        }
        if (jpaProperties.getDatabasePlatform() != null && !jpaProperties.getDatabasePlatform().isBlank()) {
            vendorAdapter.setDatabasePlatform(jpaProperties.getDatabasePlatform());
        }

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setPersistenceUnitName(persistenceUnit);
        factory.setDataSource(dataSource);
        factory.setManagedTypes(managedTypes);
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setJpaPropertyMap(jpaProperties(jpaProperties));
        return factory;
    }

    private static Map<String, Object> jpaProperties(JpaProperties jpaProperties) {
        Map<String, Object> properties = new LinkedHashMap<>(jpaProperties.getProperties());
        properties.put(AvailableSettings.STATEMENT_INSPECTOR, new RequestExecutionStatementInspector());
        properties.putIfAbsent("hibernate.hbm2ddl.auto", "validate");
        properties.putIfAbsent("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        properties.putIfAbsent("hibernate.implicit_naming_strategy", "org.springframework.boot.hibernate.SpringImplicitNamingStrategy");
        return properties;
    }
}
