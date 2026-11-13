package com.erg.ergspringboot.auth.internal.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.erg.ergspringboot.exception.auth.AuthFailure;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordServiceConcurrencyTest {

    @Test
    void fiftyConcurrentMatchesNeverExceedTheConfiguredHashConcurrency() throws Exception {
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        PasswordEncoder slow = new FakeEncoder(() -> {
            peak.accumulateAndGet(inside.incrementAndGet(), Math::max);
            sleep(20);
            inside.decrementAndGet();
        });
        PasswordService service = new PasswordService(slow, 8);

        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return service.matches("secret", "stored");
            }));
        }
        start.countDown();
        for (Future<Boolean> result : results) {
            assertThat(result.get(10, TimeUnit.SECONDS)).isTrue();
        }
        pool.shutdown();

        assertThat(peak.get()).isLessThanOrEqualTo(8);
    }

    @Test
    void hashingFromAVirtualThreadRunsOnAnOsThreadSoItNeverPinsACarrier() throws Exception {
        AtomicInteger hashes = new AtomicInteger();
        AtomicInteger virtualHashes = new AtomicInteger();
        PasswordService service = new PasswordService(new FakeEncoder(() -> {
            hashes.incrementAndGet();
            if (Thread.currentThread().isVirtual()) virtualHashes.incrementAndGet();
        }), 2);

        Thread request = Thread.ofVirtual().start(() -> service.matches("secret", "stored"));
        request.join(5_000);

        assertThat(hashes.get()).isEqualTo(1);
        assertThat(virtualHashes.get()).isZero();
    }

    @Test
    void waitingLongerThanTheTimeoutIsRejectedAsTooManyRequests() throws Exception {
        CountDownLatch holderInside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PasswordEncoder blocking = new FakeEncoder(() -> {
            holderInside.countDown();
            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        PasswordService service = new PasswordService(blocking, 1);
        Thread holder = new Thread(() -> service.matches("secret", "stored"));
        holder.start();
        try {
            assertThat(holderInside.await(5, TimeUnit.SECONDS)).isTrue();

            // Preemptive timeout: without the semaphore the call would block on the holder forever.
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertThatThrownBy(() -> service.burnUnknownAccountCheck("secret"))
                    .isInstanceOfSatisfying(AuthFailure.PasswordHashBusy.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                        assertThat(ex.getCode()).isEqualTo("AUTH_PASSWORD_HASH_BUSY");
                    }));
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertThatThrownBy(() -> service.hash("other"))
                    .isInstanceOf(AuthFailure.PasswordHashBusy.class));
        } finally {
            release.countDown();
            holder.join(5_000);
        }
        // The permit is returned once the holder finishes.
        assertThat(service.hash("next")).isEqualTo("hash");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private record FakeEncoder(Runnable work) implements PasswordEncoder {
        @Override
        public String encode(CharSequence rawPassword) {
            return "hash";
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            work.run();
            return true;
        }
    }
}
