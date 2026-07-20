package com.erg.ergspringboot.service.lms;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "erg.lifecycle", name = "question-bank-seed-on-startup", havingValue = "true")
public class QuestionBankDemoSeedInitializer implements ApplicationRunner {

    private final QuestionBankDemoBootstrapSeedService questionBankDemoSeedService;

    public QuestionBankDemoSeedInitializer(QuestionBankDemoBootstrapSeedService questionBankDemoSeedService) {
        this.questionBankDemoSeedService = questionBankDemoSeedService;
    }

    @Override
    public void run(ApplicationArguments args) {
        questionBankDemoSeedService.seedQuestionBankDemo();
    }
}
