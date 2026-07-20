package com.erg.ergspringboot.service.auth;

import com.erg.ergspringboot.repository.auth.RefreshTokenHistoryRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenHistoryRetentionJob {
    private final RefreshTokenHistoryRepository repository;

    public RefreshTokenHistoryRetentionJob(RefreshTokenHistoryRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${erg.auth.refresh-token-history.retention-cron:0 20 3 * * *}")
    public void deleteExpiredHistory() {
        repository.deleteByConsumedAtBefore(OffsetDateTime.now(ZoneOffset.UTC).minusDays(30));
    }
}
