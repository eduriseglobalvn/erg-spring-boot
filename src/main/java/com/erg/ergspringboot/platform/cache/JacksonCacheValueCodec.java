package com.erg.ergspringboot.platform.cache;

import tools.jackson.databind.ObjectMapper;

public final class JacksonCacheValueCodec implements CacheValueCodec {

    private final ObjectMapper mapper;

    public JacksonCacheValueCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public <T> CacheEncoding encode(CacheDescriptor<T> descriptor, T value) {
        if (value == null && !descriptor.cacheNulls()) {
            return CacheEncoding.oversized(0);
        }
        byte[] bytes;
        try {
            bytes = mapper.writerFor(descriptor.valueType()).writeValueAsBytes(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Cannot encode cache " + descriptor.name(), exception);
        }
        if (bytes.length > descriptor.maximumSerializedBytes()) {
            return CacheEncoding.oversized(bytes.length);
        }
        return CacheEncoding.accepted(bytes);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T decode(CacheDescriptor<T> descriptor, byte[] bytes) {
        try {
            return (T) mapper.readValue(bytes, descriptor.valueType());
        } catch (RuntimeException exception) {
            throw new CacheValueDecodingException("Corrupt value in cache " + descriptor.name(), exception);
        }
    }
}

