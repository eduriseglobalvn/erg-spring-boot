package com.erg.ergspringboot.users.persistence.auth;

import com.erg.ergspringboot.users.persistence.auth.RefreshTokenHistory;
import java.util.Optional;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenHistoryRepository extends JpaRepository<RefreshTokenHistory, String> {
    Optional<RefreshTokenHistory> findByTokenHash(String tokenHash);
    long deleteByConsumedAtBefore(OffsetDateTime cutoff);
}
