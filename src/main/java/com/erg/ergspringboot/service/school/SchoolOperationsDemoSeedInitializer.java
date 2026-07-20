package com.erg.ergspringboot.service.school;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class SchoolOperationsDemoSeedInitializer implements ApplicationRunner {
    private static final String SEED_PROPERTY = "erg.lifecycle.school-operations-seed-on-startup";

    private final SchoolOperationsDemoSeedService seedService;
    private final Environment environment;

    public SchoolOperationsDemoSeedInitializer(
            SchoolOperationsDemoSeedService seedService,
            Environment environment
    ) {
        this.seedService = seedService;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (environment.getProperty(SEED_PROPERTY, Boolean.class, false)) {
            seedService.seedIfEmpty("erg");
        }
    }
}
