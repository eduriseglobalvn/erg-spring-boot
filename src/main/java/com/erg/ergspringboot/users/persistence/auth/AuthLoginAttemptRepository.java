package com.erg.ergspringboot.users.persistence.auth;

import com.erg.ergspringboot.users.persistence.auth.AuthLoginAttempt;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuthLoginAttemptRepository extends JpaRepository<AuthLoginAttempt, String> {

    String RESULT_RESET = "RESET";

    /** A credential reset lifts the temporary lockout: the lockout scan stops at this row (BUG-A-059). */
    default void recordCredentialReset(String tenantId, String userId, OffsetDateTime at) {
        AuthLoginAttempt marker = new AuthLoginAttempt();
        marker.setId(com.erg.ergspringboot.utils.IdGenerator.newId());
        marker.setTenantId(tenantId);
        marker.setUserId(userId);
        marker.setResult(RESULT_RESET);
        marker.setReason("credential_reset");
        marker.setCreatedAt(at);
        save(marker);
    }

    List<AuthLoginAttempt> findByUserIdOrderByCreatedAtDesc(String userId);

    @Query("select new com.erg.ergspringboot.users.persistence.auth.LoginAttemptSignal(a.result, a.reason, a.createdAt)"
            + " from AuthLoginAttempt a where a.userId = :userId order by a.createdAt desc")
    List<LoginAttemptSignal> findSignalsByUserId(@Param("userId") String userId, Pageable pageable);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query(value = "delete from auth_login_attempts where id in (select id from auth_login_attempts"
            + " where created_at < :cutoff limit :batch)", nativeQuery = true)
    int deleteOlderThan(@Param("cutoff") OffsetDateTime cutoff, @Param("batch") int batch);

    List<AuthLoginAttempt> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    // Explicit lower(): matches the V184 expression index (a derived IgnoreCase query does not say which function it emits).
    @Query("select a from AuthLoginAttempt a where lower(a.attemptedEmail) = lower(:email) order by a.createdAt desc")
    List<AuthLoginAttempt> findByAttemptedEmailIgnoreCaseOrderByCreatedAtDesc(@Param("email") String attemptedEmail);

    @Query("select a from AuthLoginAttempt a where lower(a.attemptedEmail) = lower(:email) order by a.createdAt desc")
    List<AuthLoginAttempt> findByAttemptedEmailIgnoreCaseOrderByCreatedAtDesc(
            @Param("email") String attemptedEmail, Pageable pageable);

    long countByIpAddressAndResultIn(String ipAddress, Collection<String> results);

    @Query("select count(a) from AuthLoginAttempt a where lower(a.attemptedEmail) = lower(:email) and a.result in :results")
    long countByAttemptedEmailIgnoreCaseAndResultIn(
            @Param("email") String attemptedEmail, @Param("results") Collection<String> results);

    long countByIpAddressAndResultInAndCreatedAtAfter(
            String ipAddress,
            Collection<String> results,
            OffsetDateTime createdAt
    );

    @Query("select count(a) from AuthLoginAttempt a where lower(a.attemptedEmail) = lower(:email) and a.result in :results"
            + " and a.createdAt > :createdAt")
    long countByAttemptedEmailIgnoreCaseAndResultInAndCreatedAtAfter(
            @Param("email") String attemptedEmail,
            @Param("results") Collection<String> results,
            @Param("createdAt") OffsetDateTime createdAt
    );

    Page<AuthLoginAttempt> findByTenantIdOrderByCreatedAtDescIdDesc(String tenantId, Pageable pageable);

    Page<AuthLoginAttempt> findByTenantIdAndUserIdOrderByCreatedAtDescIdDesc(
            String tenantId, String userId, Pageable pageable);
}

