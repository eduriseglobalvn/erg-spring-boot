package com.erg.ergspringboot.auth.internal.credential;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Argon2id through the system libargon2 (C, ~30% more logins/s than BouncyCastle on the 4-core production box),
 * called with the Foreign Function API and the exact parameters of
 * {@link Argon2PasswordEncoder#defaultsForSpringSecurity_v5_8()}, so stored hashes verify either way. Without the
 * library (dev machines, GraalVM native image) or if it disagrees with the Java encoder, the Java encoder is used.
 */
final class NativeArgon2PasswordEncoder implements PasswordEncoder {

    private static final Logger log = LoggerFactory.getLogger(NativeArgon2PasswordEncoder.class);
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 1 << 14;
    private static final int ITERATIONS = 2;
    private static final int ENCODED_CAPACITY = 256;
    private static final int ARGON2_OK = 0;

    private final MethodHandle hashEncoded;
    private final MethodHandle verify;
    private final PasswordEncoder fallback;
    private final SecureRandom random = new SecureRandom();

    private NativeArgon2PasswordEncoder(MethodHandle hashEncoded, MethodHandle verify, PasswordEncoder fallback) {
        this.hashEncoded = hashEncoded;
        this.verify = verify;
        this.fallback = fallback;
    }

    /** The native encoder when libargon2 loads and agrees with the Java encoder, otherwise the Java encoder itself. */
    static PasswordEncoder preferNative() {
        PasswordEncoder java = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        try {
            SymbolLookup lib = SymbolLookup.libraryLookup("libargon2.so.1", Arena.global());
            Linker linker = Linker.nativeLinker();
            ValueLayout size = ValueLayout.JAVA_LONG;
            MethodHandle hash = linker.downcallHandle(lib.findOrThrow("argon2id_hash_encoded"), FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, size, ValueLayout.ADDRESS, size, size, ValueLayout.ADDRESS, size));
            MethodHandle check = linker.downcallHandle(lib.findOrThrow("argon2id_verify"), FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, size));
            NativeArgon2PasswordEncoder encoder = new NativeArgon2PasswordEncoder(hash, check, java);
            String probe = "erg-native-argon2-probe";
            if (java.matches(probe, encoder.encode(probe)) && encoder.matches(probe, java.encode(probe))
                    && !encoder.matches("wrong", java.encode(probe))) {
                log.info("Password hashing uses native libargon2");
                return encoder;
            }
            log.warn("Native libargon2 disagrees with the Java Argon2 encoder; using the Java encoder");
        } catch (Throwable ex) {
            log.warn("Native libargon2 unavailable ({}); using the Java Argon2 encoder", ex.toString());
        }
        return java;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        byte[] password = rawPassword.toString().getBytes(StandardCharsets.UTF_8);
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pwd = arena.allocateFrom(ValueLayout.JAVA_BYTE, password);
            MemorySegment saltSegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, salt);
            MemorySegment out = arena.allocate(ENCODED_CAPACITY);
            int status = (int) hashEncoded.invokeExact(ITERATIONS, MEMORY_KIB, PARALLELISM, pwd, (long) password.length,
                    saltSegment, (long) SALT_LENGTH, (long) HASH_LENGTH, out, (long) ENCODED_CAPACITY);
            pwd.fill((byte) 0);
            if (status != ARGON2_OK) throw new IllegalStateException("argon2id_hash_encoded failed: " + status);
            return out.getString(0);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        } finally {
            Arrays.fill(password, (byte) 0);
        }
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || !encodedPassword.startsWith("$argon2id$")) {
            return fallback.matches(rawPassword, encodedPassword);
        }
        byte[] password = rawPassword.toString().getBytes(StandardCharsets.UTF_8);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pwd = arena.allocateFrom(ValueLayout.JAVA_BYTE, password);
            int status = (int) verify.invokeExact(arena.allocateFrom(encodedPassword), pwd, (long) password.length);
            pwd.fill((byte) 0);
            // Any other status (malformed hash, wrong version) is a plain mismatch, as in the Java encoder.
            return status == ARGON2_OK;
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        } finally {
            Arrays.fill(password, (byte) 0);
        }
    }
}
