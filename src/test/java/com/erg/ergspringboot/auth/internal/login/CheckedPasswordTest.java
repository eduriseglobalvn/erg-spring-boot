package com.erg.ergspringboot.auth.internal.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.auth.internal.credential.PasswordService;
import com.erg.ergspringboot.users.persistence.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CheckedPasswordTest {

    private final PasswordService passwords = mock(PasswordService.class);

    @Test
    void rightPasswordMatchesOnlyTheHashThatWasVerified() {
        when(passwords.matches("secret", "hash-1")).thenReturn(true);

        CheckedPassword checked = CheckedPassword.check(passwords, Optional.of(user("hash-1")), "secret");

        assertThat(checked.matches("hash-1")).isTrue();
        // The password changed between hashing and the transaction: never let the old verdict through.
        assertThat(checked.matches("hash-2")).isFalse();
        verify(passwords, times(1)).matches("secret", "hash-1");
    }

    @Test
    void wrongPasswordNeverMatches() {
        when(passwords.matches("wrong", "hash-1")).thenReturn(false);

        assertThat(CheckedPassword.check(passwords, Optional.of(user("hash-1")), "wrong").matches("hash-1")).isFalse();
    }

    @Test
    void unknownAccountOrMissingHashStillCostsOneHash() {
        CheckedPassword unknown = CheckedPassword.check(passwords, Optional.empty(), "secret");
        CheckedPassword noHash = CheckedPassword.check(passwords, Optional.of(user(" ")), "secret");

        assertThat(unknown.matches(null)).isFalse();
        assertThat(noHash.matches(" ")).isFalse();
        verify(passwords, times(2)).burnUnknownAccountCheck("secret");
        verify(passwords, never()).matches(anyString(), anyString());
    }

    private static User user(String hash) {
        User user = new User();
        user.setPasswordHash(hash);
        return user;
    }
}
