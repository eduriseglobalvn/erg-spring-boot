package com.erg.ergspringboot.service.lms;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class LmsBootstrapSeedServiceArchitectureTest {

    @Test
    void bootstrapOrchestratorDoesNotOpenMixedDatabaseTransaction() throws NoSuchMethodException {
        Method seedDefaultHierarchy = LmsBootstrapSeedService.class.getMethod("seedDefaultHierarchy");

        assertThat(seedDefaultHierarchy.getAnnotation(Transactional.class)).isNull();
        assertThat(LmsBootstrapSeedService.class.getAnnotation(Transactional.class)).isNull();
    }

    @Test
    void accountAndLearningSeedStepsUseSinglePostgresTransactionManager() throws NoSuchMethodException {
        Transactional accountTransaction = LmsAccountBootstrapSeedService.class
                .getMethod("seedAccountHierarchy")
                .getAnnotation(Transactional.class);
        Transactional learningTransaction = LmsLearningBootstrapSeedService.class
                .getMethod("seedLearningHierarchy")
                .getAnnotation(Transactional.class);

        assertThat(accountTransaction.value()).isEqualTo("postgresTransactionManager");
        assertThat(learningTransaction.value()).isEqualTo("postgresTransactionManager");
    }
}
