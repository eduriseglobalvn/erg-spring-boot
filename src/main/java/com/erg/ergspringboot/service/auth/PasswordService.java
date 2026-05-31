package com.erg.ergspringboot.service.auth;

import java.security.MessageDigest;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    private final Argon2PasswordEncoder argon2PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    public String hash(String rawPassword) {
        return argon2PasswordEncoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            return argon2PasswordEncoder.matches(rawPassword, storedHash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
