package com.erg.ergspringboot.platform.nativeimage;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/** Runtime metadata for Modulith Insight's ArchUnit module graph bootstrap. */
public final class ModulithNativeRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(
                TypeReference.of("com.tngtech.archunit.core.importer.ModuleImportPlugin"),
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        // Spring Modulith's persistent multicaster introspects listener methods
        // when it records a durable publication. Native image otherwise fails
        // on ApplicationListenerMethodAdapter#getTargetMethod at first event.
        hints.reflection().registerType(
                TypeReference.of("org.springframework.context.event.ApplicationListenerMethodAdapter"),
                MemberCategory.INVOKE_PUBLIC_METHODS);
    }
}
