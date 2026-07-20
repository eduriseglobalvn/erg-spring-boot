package com.erg.ergspringboot.platform.cache;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import tools.jackson.databind.JavaType;

public final class CacheRuntimeHints {

    private CacheRuntimeHints() {
    }

    public static void register(RuntimeHints hints, JavaType valueType) {
        hints.reflection().registerType(valueType.getRawClass(),
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);
    }
}
