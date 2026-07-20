package com.erg.ergspringboot.platform.cache;

public interface CacheValueCodec {
    <T> CacheEncoding encode(CacheDescriptor<T> descriptor, T value);

    <T> T decode(CacheDescriptor<T> descriptor, byte[] bytes);
}

