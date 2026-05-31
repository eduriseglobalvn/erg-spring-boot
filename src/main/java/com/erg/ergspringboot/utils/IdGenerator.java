package com.erg.ergspringboot.utils;

import java.security.SecureRandom;
import java.util.UUID;

public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private IdGenerator() {
    }

    public static String newId() {
        return newUuidV7().toString();
    }

    public static UUID newUuidV7() {
        long timestamp = System.currentTimeMillis();
        long randomA = RANDOM.nextLong() & 0x0fffL;
        long randomB = RANDOM.nextLong() & 0x3fff_ffff_ffff_ffffL;
        long mostSignificantBits = (timestamp << 16) | 0x7000L | randomA;
        long leastSignificantBits = 0x8000_0000_0000_0000L | randomB;

        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
