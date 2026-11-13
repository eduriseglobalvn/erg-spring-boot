package com.erg.ergspringboot.auth.internal.credential;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Runs against libargon2 where installed (Linux with libargon2-1); elsewhere it checks the Java fallback. */
class NativeArgon2PasswordEncoderTest {

    private final PasswordEncoder java = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final PasswordEncoder preferred = NativeArgon2PasswordEncoder.preferNative();

    @Test
    void theJvmImageLoadsTheSystemLibrary() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        assertThat(dockerfile).contains("libargon2-1").contains("--enable-native-access=ALL-UNNAMED");
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
        // Through PasswordService, as logins call it: the Java encoder throws on a broken hash and the service maps that to false.
        PasswordService service = new PasswordService(preferred, 1);
        assertThat(service.matches("x", "not-a-hash")).isFalse();
        assertThat(service.matches("x", java.encode("y").replace("$v=19$", "$v=16$"))).isFalse();
    }
}
