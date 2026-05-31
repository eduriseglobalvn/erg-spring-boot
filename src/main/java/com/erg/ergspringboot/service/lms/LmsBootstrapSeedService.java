package com.erg.ergspringboot.service.lms;

import org.springframework.stereotype.Service;

@Service
public class LmsBootstrapSeedService {

    private final LmsAccountBootstrapSeedService accountSeedService;
    private final LmsLearningBootstrapSeedService learningSeedService;

    public LmsBootstrapSeedService(
            LmsAccountBootstrapSeedService accountSeedService,
            LmsLearningBootstrapSeedService learningSeedService
    ) {
        this.accountSeedService = accountSeedService;
        this.learningSeedService = learningSeedService;
    }

    public void seedDefaultHierarchy() {
        accountSeedService.seedAccountHierarchy();
        learningSeedService.seedLearningHierarchy();
    }
}
