package com.erg.ergspringboot.auth.internal.credential;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Argon2id through libargon2 (C) with the exact parameters of {@link Argon2PasswordEncoder#defaultsForSpringSecurity_v5_8()},
 * so stored hashes verify either way. Falls back to the Java encoder when the native library cannot load
 * (GraalVM native image, unsupported CPU/OS) or the stored hash is not one libargon2 reads.
 */
final class NativeArgon2PasswordEncoder implements PasswordEncoder {

    private static final Logger log = LoggerFactory.getLogger(NativeArgon2PasswordEncoder.class);
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 1 << 14;
    private static final int ITERATIONS = 2;

    private final Argon2 argon2;
    private final PasswordEncoder fallback;

    private NativeArgon2PasswordEncoder(Argon2 argon2, PasswordEncoder fallback) {
        this.argon2 = argon2;
        this.fallback = fallback;
    }

    /** The native encoder when libargon2 loads and agrees with the Java encoder, otherwise the Java encoder itself. */
    static PasswordEncoder preferNative() {
        PasswordEncoder java = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        try {
            NativeArgon2PasswordEncoder encoder = new NativeArgon2PasswordEncoder(
                    Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id, SALT_LENGTH, HASH_LENGTH), java);
            String probe = "erg-native-argon2-probe";
            if (java.matches(probe, encoder.encode(probe)) && encoder.matches(probe, java.encode(probe))) {
                log.info("Password hashing uses native libargon2");
                return encoder;
            }
            log.warn("Native libargon2 disagrees with the Java Argon2 encoder; using the Java encoder");
        } catch (RuntimeException | LinkageError ex) {
            log.warn("Native libargon2 unavailable ({}); using the Java Argon2 encoder", ex.toString());
        }
        return java;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        char[] chars = toChars(rawPassword);
        try {
            return argon2.hash(ITERATIONS, MEMORY_KIB, PARALLELISM, chars);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || !encodedPassword.startsWith("$argon2id$")) {
            return fallback.matches(rawPassword, encodedPassword);
        }
        char[] chars = toChars(rawPassword);
        try {
            return argon2.verify(encodedPassword, chars);
        } catch (RuntimeException malformed) {
            return fallback.matches(rawPassword, encodedPassword);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    private static char[] toChars(CharSequence value) {
        char[] chars = new char[value.length()];
        for (int i = 0; i < chars.length; i++) chars[i] = value.charAt(i);
        return chars;
    }
}
