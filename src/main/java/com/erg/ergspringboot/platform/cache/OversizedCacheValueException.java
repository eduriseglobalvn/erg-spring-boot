package com.erg.ergspringboot.platform.cache;

public class OversizedCacheValueException extends RuntimeException {
    public OversizedCacheValueException(String cacheName, int actualBytes, int maximumBytes) {
        super("Cache value for " + cacheName + " is " + actualBytes
                + " bytes; maximum is " + maximumBytes + " bytes");
    }
}

