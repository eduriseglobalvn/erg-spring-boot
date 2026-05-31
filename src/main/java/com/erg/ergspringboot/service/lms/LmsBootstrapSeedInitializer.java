package com.erg.ergspringboot.service.lms;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
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
