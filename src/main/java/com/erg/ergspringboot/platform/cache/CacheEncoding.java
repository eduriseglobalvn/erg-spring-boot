package com.erg.ergspringboot.platform.cache;

public record CacheEncoding(byte[] bytes, int serializedSize, boolean cacheable) {
    public CacheEncoding {
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    public static CacheEncoding accepted(byte[] bytes) {
        return new CacheEncoding(bytes, bytes.length, true);
    }

    public static CacheEncoding oversized(int serializedSize) {
        return new CacheEncoding(new byte[0], serializedSize, false);
    }
}

