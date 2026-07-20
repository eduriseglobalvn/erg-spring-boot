package com.erg.ergspringboot.service.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.model.auth.AccountVerificationCode;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.AccountVerificationCodeRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountVerificationServiceTest {

    @Mock UserRepository userRepository;
    @Mock AccountVerificationCodeRepository codeRepository;
    @Mock AccountMailSender mailSender;
    @Mock UserActivityRepository userActivityRepository;

    private PasswordService passwordService;
    private AccountVerificationService service;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService();
        service = new AccountVerificationService(
                userRepository, codeRepository, mailSender, passwordService, userActivityRepository);
    }

    @Test
    void recoveryChallengeStoresOnlyHashedOtpAndVerificationConsumesIt() {
        User user = user();
        AtomicReference<AccountVerificationCode> saved = new AtomicReference<>();
        AtomicReference<String> deliveredCode = new AtomicReference<>();
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));
        when(codeRepository.findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "user-1", "RECOVERY_EMAIL")).thenReturn(Optional.empty());
        when(codeRepository.save(any())).thenAnswer(invocation -> {
            AccountVerificationCode code = invocation.getArgument(0);
            saved.set(code);
            return code;
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            deliveredCode.set(invocation.getArgument(2));
            return null;
        }).when(mailSender).sendRecoveryEmailCode(eq("recovery@example.com"), eq("Teacher One"), any());

        var challenge = service.requestRecoveryEmailChallenge("user-1", "erg", " Recovery@Example.com ");

        assertFalse(saved.get().getCodeHash().contains(deliveredCode.get()));
        when(codeRepository.findById(challenge.challengeId())).thenReturn(Optional.of(saved.get()));

        var result = service.verifyRecoveryEmail("user-1", "erg", challenge.challengeId(), deliveredCode.get());

        assertTrue(result.verified());
        assertFalse(result.lifecycle().nextSteps().contains("VERIFY_RECOVERY_EMAIL"));
        assertTrue(user.getRecoveryEmailVerified());
        assertTrue(saved.get().getConsumedAt() != null);
        verify(userRepository).save(user);
    }

    @Test
    void recoveryChallengeRejectsEmailAlreadyUsedByAnotherAccount() {
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user()));
        when(userRepository.existsByRecoveryEmailIgnoreCaseAndIdNot("used@example.com", "user-1"))
                .thenReturn(true);

        var error = assertThrows(
                com.erg.ergspringboot.exception.AccountLifecycleException.class,
                () -> service.requestRecoveryEmailChallenge("user-1", "erg", "used@example.com")
        );

        org.junit.jupiter.api.Assertions.assertEquals("RECOVERY_EMAIL_CONFLICT", error.getCode());
    }

    private User user() {
        User user = new User();
        user.setId("user-1");
        user.setTenantId("erg");
        user.setEmail("primary@example.com");
        user.setFullName("Teacher One");
        user.setIsProfileCompleted(true);
        user.setMustChangePassword(true);
        user.setRecoveryEmailVerified(false);
        return user;
    }
}
