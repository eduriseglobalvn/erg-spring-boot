package com.erg.ergspringboot.repository.auth;

import com.erg.ergspringboot.model.auth.AuthLoginAttempt;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuthLoginAttemptRepository extends JpaRepository<AuthLoginAttempt, String> {

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

