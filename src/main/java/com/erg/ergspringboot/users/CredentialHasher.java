package com.erg.ergspringboot.users;

/** Password hashing the users module needs; implemented by the auth module. */
public interface CredentialHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String storedHash);
}
