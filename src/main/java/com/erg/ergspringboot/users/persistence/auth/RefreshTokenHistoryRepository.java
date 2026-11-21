package com.erg.ergspringboot.users.persistence.auth;

import com.erg.ergspringboot.users.persistence.auth.RefreshTokenHistory;
import java.util.Optional;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenHistoryRepository extends JpaRepository<RefreshTokenHistory, String> {
    Optional<RefreshTokenHistory> findByTokenHash(String tokenHash);

    /** One bounded batch per call, in its own transaction; a result below {@code batchSize} marks the tail. */
    @Modifying
    @Transactional
    @Query(value = """
            delete from refresh_token_history
            where id in (select id from refresh_token_history where consumed_at < :cutoff limit :batchSize)
            """, nativeQuery = true)
    int deleteConsumedBefore(OffsetDateTime cutoff, int batchSize);
}
