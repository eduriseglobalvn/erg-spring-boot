package com.erg.ergspringboot.repository.auth;

import com.erg.ergspringboot.model.auth.AuthPin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthPinRepository extends JpaRepository<AuthPin, String> {

    Optional<AuthPin> findFirstByEmailIgnoreCaseAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
            String email,
            String purpose
    );
}

