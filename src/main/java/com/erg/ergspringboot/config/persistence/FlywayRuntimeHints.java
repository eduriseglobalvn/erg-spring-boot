package com.erg.ergspringboot.config.persistence;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers the Flyway migration scripts as native-image resources. Native images do not
 * bundle arbitrary classpath resources, so without these patterns {@code flyway.migrate()}
 * finds zero migrations at runtime and the schema validation/boot fails.
 */
public class FlywayRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources().registerPattern("db/migration/**");
        hints.resources().registerPattern("db/migration/postgres/*.sql");
    }
}
