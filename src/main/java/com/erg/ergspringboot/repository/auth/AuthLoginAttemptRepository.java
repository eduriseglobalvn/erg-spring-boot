package com.erg.ergspringboot.repository.auth;

import com.erg.ergspringboot.model.auth.AuthLoginAttempt;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthLoginAttemptRepository extends JpaRepository<AuthLoginAttempt, String> {

    List<AuthLoginAttempt> findByUserIdOrderByCreatedAtDesc(String userId);

    List<AuthLoginAttempt> findByAttemptedEmailIgnoreCaseOrderByCreatedAtDesc(String attemptedEmail);

    long countByIpAddressAndResultIn(String ipAddress, Collection<String> results);

    long countByAttemptedEmailIgnoreCaseAndResultIn(String attemptedEmail, Collection<String> results);
}

