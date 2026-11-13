package com.erg.ergspringboot.auth.internal.credential;

import com.erg.ergspringboot.exception.auth.AuthFailure;
import com.erg.ergspringboot.platform.security.ErgAuthProperties;
import com.erg.ergspringboot.users.CredentialHasher;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService implements CredentialHasher {

    private static final long PERMIT_WAIT_SECONDS = 2;

    private final PasswordEncoder passwordEncoder;
    // Each Argon2 hash allocates ~16 MB; a login burst on 200 Tomcat threads would exhaust the heap.
    private final Semaphore hashPermits;
    private final String dummyHash;

    @Autowired
    public PasswordService(ErgAuthProperties properties) {
        this(Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(), properties.getPasswordHashConcurrency());
    }

    PasswordService(PasswordEncoder passwordEncoder, int hashConcurrency) {
        this.passwordEncoder = passwordEncoder;
        this.hashPermits = new Semaphore(hashConcurrency, true);
        this.dummyHash = passwordEncoder.encode("erg-invalid-login-dummy-secret");
    }

    @Override
    public String hash(String rawPassword) {
        return withHashPermit(() -> passwordEncoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        return withHashPermit(() -> {
            try {
                return passwordEncoder.matches(rawPassword, storedHash);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        });
    }

    /** Performs the same-cost password verification when the account identifier is unknown. */
    public void burnUnknownAccountCheck(String rawPassword) {
        matches(rawPassword, dummyHash);
    }

    private <T> T withHashPermit(Supplier<T> work) {
        try {
            if (!hashPermits.tryAcquire(PERMIT_WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new AuthFailure.PasswordHashBusy();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AuthFailure.PasswordHashBusy();
        }
        try {
            return work.get();
        } finally {
            hashPermits.release();
        }
    }
}
