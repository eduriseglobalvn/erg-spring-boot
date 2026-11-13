package com.erg.ergspringboot.auth.internal.credential;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class NativeArgon2PasswordEncoderTest {

    private final PasswordEncoder java = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final PasswordEncoder preferred = NativeArgon2PasswordEncoder.preferNative();

    @Test
    void loadsTheNativeLibraryOnTheBuildPlatforms() {
        assertThat(preferred).isInstanceOf(NativeArgon2PasswordEncoder.class);
    }

    @Test
    void hashesStayInterchangeableWithTheJavaEncoder() {
        String stored = java.encode("hs-secret-1");
        assertThat(preferred.matches("hs-secret-1", stored)).isTrue();
        assertThat(preferred.matches("hs-secret-2", stored)).isFalse();

        String issued = preferred.encode("hs-secret-1");
        assertThat(issued).startsWith("$argon2id$v=19$m=16384,t=2,p=1$");
        assertThat(java.matches("hs-secret-1", issued)).isTrue();
    }

    @Test
    void foreignOrBrokenHashesNeverMatch() {
        assertThat(preferred.matches("x", "not-a-hash")).isFalse();
        assertThat(preferred.matches("x", "$argon2id$v=19$m=16384,t=2,p=1$broken")).isFalse();
    }
}
