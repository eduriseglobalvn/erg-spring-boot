package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** A cache hit on a bean that is both cached and transactional must not open a transaction (connection). */
class CacheAdvisorOrderTest {

    static final AtomicInteger TRANSACTIONS = new AtomicInteger();

    @Test
    void cacheHitOpensNoTransactionWhenTheCacheAdvisorWrapsTheTransaction() {
        TRANSACTIONS.set(0);
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            Reader reader = context.getBean(Reader.class);
            reader.read("a");
            reader.read("a");
            reader.read("a");
        }
        assertThat(TRANSACTIONS).hasValue(1);
    }

    @Test
    void productionCacheOrderIsOutsideTheDefaultTransactionOrder() {
        assertThat(CacheConfig.class.getAnnotation(EnableCaching.class).order())
                .isLessThan(org.springframework.core.Ordered.LOWEST_PRECEDENCE);
    }

    @Test
    void lmsReadFacadeHoldsNoTransactionAndItsCachedDelegatesOpenTheirOwn() throws Exception {
        assertThat(com.erg.ergspringboot.lms.internal.readmodel.LmsReadModelUseCase.class.isAnnotationPresent(Transactional.class))
                .isFalse();
        for (Class<?> delegate : java.util.List.of(
                com.erg.ergspringboot.lms.internal.readmodel.LmsClassWorkspaceReadUseCase.class,
                com.erg.ergspringboot.lms.internal.readmodel.LmsStudentGroupReadUseCase.class,
                com.erg.ergspringboot.lms.internal.readmodel.LmsAssignmentProgressQueryUseCase.class,
                com.erg.ergspringboot.lms.internal.readmodel.LmsTeacherHomeworkReadUseCase.class)) {
            boolean transactional = delegate.isAnnotationPresent(Transactional.class)
                    || java.util.Arrays.stream(delegate.getDeclaredMethods()).anyMatch(m -> m.isAnnotationPresent(Transactional.class));
            assertThat(transactional).as(delegate.getSimpleName()).isTrue();
        }
    }

    @Configuration
    @EnableCaching(order = CacheConfig.CACHE_ADVISOR_ORDER)
    @EnableTransactionManagement
    static class Config {
        @Bean CacheManager cacheManager() { return new ConcurrentMapCacheManager("probe"); }
        @Bean Reader reader() { return new Reader(); }
        @Bean PlatformTransactionManager transactionManager() {
            return new PlatformTransactionManager() {
                @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
                    TRANSACTIONS.incrementAndGet();
                    return new SimpleTransactionStatus();
                }
                @Override public void commit(TransactionStatus status) { }
                @Override public void rollback(TransactionStatus status) { }
            };
        }
    }

    static class Reader {
        @Cacheable("probe")
        @Transactional(readOnly = true)
        public String read(String key) { return key; }
    }
}
