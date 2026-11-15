package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.calendar.internal.batch.CalendarBatchLifecycleUseCase;
import com.erg.ergspringboot.calendar.persistence.CalendarBatch;
import com.erg.ergspringboot.calendar.persistence.CalendarBatchRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEvent;
import com.erg.ergspringboot.calendar.persistence.CalendarEventRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.dao.DataIntegrityViolationException;

@Import(TeachingScheduleVersioningIT.PublishCounter.class)
class TeachingScheduleVersioningIT extends TeachingScheduleITBase {

    @TestConfiguration
    static class PublishCounter {
        static final AtomicInteger PUBLISHED = new AtomicInteger();

        @Bean
        PublishListener publishListener() {
            return new PublishListener();
        }
    }

    public static class PublishListener {
        // after commit: a rolled-back loser must not count
        @TransactionalEventListener
        public void on(CalendarBatchPublished event) {
            PublishCounter.PUBLISHED.incrementAndGet();
        }
    }

    @Autowired CalendarBatchLifecycleUseCase lifecycle;
    @Autowired CalendarBatchRepository batches;
    @Autowired CalendarEventRepository events;

    @Test
    void entitiesCarryAVersionThatBumpsOnEveryWrite() {
        String tenant = id();
        String school = school(tenant, "THCS Nguyễn Du");
        String batchId = batch(tenant, school, "DRAFT", "Đợt 1");

        CalendarBatch batch = batches.findByTenantIdAndId(tenant, batchId).orElseThrow();
        assertThat(batch.getLabel()).isEqualTo("Đợt 1");
        assertThat(batch.getRecordVersion()).isZero();

        batch.setNote("changed");
        batches.saveAndFlush(batch);
        assertThat(batches.findByTenantIdAndId(tenant, batchId).orElseThrow().getRecordVersion()).isEqualTo(1L);
    }

    @Test
    void batchLabelIsUniquePerSchoolIgnoringCaseAndCancelledBatches() {
        String tenant = id();
        String school = school(tenant, "THCS Nguyễn Du");
        batch(tenant, school, "DRAFT", "Đợt HK1");

        assertThatThrownBy(() -> batch(tenant, school, "DRAFT", "đợt hk1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // a cancelled batch frees the name, and another school may reuse it
        batch(tenant, school, "CANCELLED", "Đợt HK1");
        batch(tenant, school(tenant, "THCS Văn Lang"), "DRAFT", "Đợt HK1");
    }

    @Test
    void twoParallelPublishesSucceedOnceAndFireTheEventOnce() throws Exception {
        String tenant = id();
        String school = school(tenant, "THCS Nguyễn Du");
        String classId = classroom(tenant, school, "9A4");
        String subject = subject(tenant, "Toán");
        String teacher = user(tenant, "Q.Vương");
        String admin = user(tenant, "Giáo vụ");
        String batchId = batch(tenant, school, "DRAFT", "Đợt 1");
        event(tenant, batchId, school, classId, subject, "DRAFT",
                OffsetDateTime.parse("2026-11-18T06:30:00Z"), 6, teacher, null, null);
        int before = PublishCounter.PUBLISHED.get();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Callable<Boolean> task = () -> {
                go.await();
                try {
                    lifecycle.publish(tenant, batchId, admin);
                    return true;
                } catch (RuntimeException expected) {
                    return false;
                }
            };
            results.add(pool.submit(task));
        }
        go.countDown();
        int succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) succeeded++;
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(PublishCounter.PUBLISHED.get() - before).isEqualTo(1);
        List<CalendarEvent> stored = events.findByTenantIdAndBatchId(tenant, batchId);
        assertThat(stored).extracting(CalendarEvent::getStatus).containsOnly("PUBLISHED");
    }
}
