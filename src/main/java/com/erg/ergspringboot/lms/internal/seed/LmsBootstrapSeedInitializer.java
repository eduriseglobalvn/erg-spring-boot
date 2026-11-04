package com.erg.ergspringboot.lms.internal.seed;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod")
@ConditionalOnProperty(prefix = "erg.lifecycle", name = "lms-seed-on-startup", havingValue = "true")
public class LmsBootstrapSeedInitializer implements ApplicationRunner {

    private final LmsBootstrapSeedService lmsBootstrapSeedService;

    public LmsBootstrapSeedInitializer(LmsBootstrapSeedService lmsBootstrapSeedService) {
        this.lmsBootstrapSeedService = lmsBootstrapSeedService;
    }

    @Override
    public void run(ApplicationArguments args) {
        lmsBootstrapSeedService.seedDefaultHierarchy();
    }
}
