package com.erg.ergspringboot.users.persistence.auth;

import com.erg.ergspringboot.users.persistence.auth.AuthLoginAttempt;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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

    List<AuthLoginAttempt> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<AuthLoginAttempt> findByAttemptedEmailIgnoreCaseOrderByCreatedAtDesc(String attemptedEmail);

    List<AuthLoginAttempt> findByAttemptedEmailIgnoreCaseOrderByCreatedAtDesc(
            String attemptedEmail, Pageable pageable);

    long countByIpAddressAndResultIn(String ipAddress, Collection<String> results);

    long countByAttemptedEmailIgnoreCaseAndResultIn(String attemptedEmail, Collection<String> results);

    long countByIpAddressAndResultInAndCreatedAtAfter(
            String ipAddress,
            Collection<String> results,
            OffsetDateTime createdAt
    );

    long countByAttemptedEmailIgnoreCaseAndResultInAndCreatedAtAfter(
            String attemptedEmail,
            Collection<String> results,
            OffsetDateTime createdAt
    );

    Page<AuthLoginAttempt> findByTenantIdOrderByCreatedAtDescIdDesc(String tenantId, Pageable pageable);

    Page<AuthLoginAttempt> findByTenantIdAndUserIdOrderByCreatedAtDescIdDesc(
            String tenantId, String userId, Pageable pageable);
}

