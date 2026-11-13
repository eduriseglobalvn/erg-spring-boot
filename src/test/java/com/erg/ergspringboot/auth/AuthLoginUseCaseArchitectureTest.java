package com.erg.ergspringboot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthLoginUseCaseArchitectureTest {

    @Test
    void loginPolicyAndTokenIssuanceHaveAnInternalTransactionalOwner() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.auth.internal.login.AuthLoginUseCase"))
                .doesNotThrowAnyException();
        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/auth/AuthOperations.java"));
        assertThat(facade)
                .contains("loginFlow.login(")
                .doesNotContain("issueTokens(")
                .doesNotContain("recordSecurityEvent(");
    }

    @Test
    void loginCollaboratorsRunInsideTheUseCaseTransactionWithoutTheirOwnProxyBoundary() throws Exception {
        String useCase = login("AuthLoginUseCase");
        assertThat(useCase)
                // Argon2 runs before the transaction opens, so a login burst never parks pooled connections on hashing.
                .contains("CheckedPassword.check(passwords, passwordCache, found, request.password())")
                .contains("transaction.execute(")
                .doesNotContain("@Transactional")
                .contains("credentialVerifier.verify(")
                .contains("sessionIssuer.issueTokens(")
                .doesNotContain("passwords.matches(")
                .doesNotContain("new AuthSession()");
        // Rate limiting stays ahead of any credential evaluation.
        assertThat(useCase.indexOf("rateLimiter.allowAccount("))
                .isPositive()
                .isLessThan(useCase.indexOf("CheckedPassword.check("))
                .isLessThan(useCase.indexOf("credentialVerifier.verify("));
        assertThat(useCase.indexOf("CheckedPassword.check(")).isLessThan(useCase.indexOf("transaction.execute("));

        for (String collaborator : new String[] {
                "CredentialVerifier", "LoginAttemptRecorder", "SessionIssuer", "LoginResponseMapper"}) {
            String source = login(collaborator);
            assertThat(source)
                    .as(collaborator)
                    .contains("final class " + collaborator)
                    .doesNotContain("public class")
                    .doesNotContain("@Service")
                    .doesNotContain("@Component")
                    .doesNotContain("@Transactional");
        }
    }

    @Test
    void credentialChecksKeepLockoutBeforePasswordAndPasswordBeforeAccountStatus() throws Exception {
        String verifier = login("CredentialVerifier");
        int lockout = verifier.indexOf("failureEnforcement.isTemporarilyLocked(user)");
        int password = verifier.indexOf("password.matches(user.getPasswordHash())");
        int locked = verifier.indexOf("isLocked(user)");
        assertThat(lockout).isPositive().isLessThan(password);
        assertThat(password).isLessThan(locked);
    }

    private static String login(String className) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/auth/internal/login/" + className + ".java"))
                .replace("\r\n", "\n");
    }
}
