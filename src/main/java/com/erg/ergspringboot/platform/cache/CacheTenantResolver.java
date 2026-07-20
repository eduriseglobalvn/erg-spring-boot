package com.erg.ergspringboot.platform.cache;

@FunctionalInterface
public interface CacheTenantResolver {
    String currentTenantId();
}

