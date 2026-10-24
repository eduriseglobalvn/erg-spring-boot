package com.erg.ergspringboot.platform.nativeimage;

import com.erg.ergspringboot.common.response.ApiResponse;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * GraalVM native-image reflection hints for {@link ApiResponse} and its sealed
 * {@code TransportError} record variants.
 *
 * <p>Jackson uses {@code RecordUtil.getRecordFieldNames} at serialization time which
 * requires the record component accessor methods to be registered for reflection.
 * Without this registration the native binary falls back to a Tomcat HTML 401 page
 * instead of the JSON error envelope expected by API clients.
 */
public final class ApiResponseNativeRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // Top-level response record
        hints.reflection().registerType(
                TypeReference.of(ApiResponse.class),
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.PUBLIC_FIELDS);

        // Sealed TransportError variants — each is a record whose component accessors
        // must be reachable so Jackson's RecordUtil can inspect field names.
        hints.reflection().registerType(
                TypeReference.of("com.erg.ergspringboot.common.response.ApiResponse$TransportError$Code"),
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.PUBLIC_FIELDS);

        hints.reflection().registerType(
                TypeReference.of("com.erg.ergspringboot.common.response.ApiResponse$TransportError$Fields"),
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.PUBLIC_FIELDS);

        hints.reflection().registerType(
                TypeReference.of("com.erg.ergspringboot.common.response.ApiResponse$TransportError$Conflict"),
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.PUBLIC_FIELDS);
    }
}
