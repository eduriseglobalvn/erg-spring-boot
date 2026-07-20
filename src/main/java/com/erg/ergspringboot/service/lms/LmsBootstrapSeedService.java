package com.erg.ergspringboot.service.lms;

import org.springframework.stereotype.Service;

@Service
public class LmsBootstrapSeedService {

    private final LmsAccountBootstrapSeedService accountSeedService;
    private final LmsLearningBootstrapSeedService learningSeedService;
    private final QuestionBankDemoBootstrapSeedService questionBankDemoSeedService;
    private final QuizDemoBootstrapSeedService quizDemoSeedService;

    public LmsBootstrapSeedService(
            LmsAccountBootstrapSeedService accountSeedService,
            LmsLearningBootstrapSeedService learningSeedService,
            QuestionBankDemoBootstrapSeedService questionBankDemoSeedService,
            QuizDemoBootstrapSeedService quizDemoSeedService
    ) {
        this.accountSeedService = accountSeedService;
        this.learningSeedService = learningSeedService;
        this.questionBankDemoSeedService = questionBankDemoSeedService;
        this.quizDemoSeedService = quizDemoSeedService;
    }

    public void seedDefaultHierarchy() {
        accountSeedService.seedAccountHierarchy();
        learningSeedService.seedLearningHierarchy();
        questionBankDemoSeedService.seedQuestionBankDemo();
        quizDemoSeedService.seedQuizDemos();
    }
}
