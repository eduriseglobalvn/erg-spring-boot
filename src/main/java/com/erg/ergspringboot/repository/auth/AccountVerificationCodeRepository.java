package com.erg.ergspringboot.repository.auth;

import com.erg.ergspringboot.model.auth.AccountVerificationCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountVerificationCodeRepository extends JpaRepository<AccountVerificationCode, String> {

    Optional<AccountVerificationCode> findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String userId, String purpose);
}
