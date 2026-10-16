package com.erg.ergspringboot.enterpriseconnectors;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** Transport-neutral commands owned by the enterprise-connectors module. */
public final class EnterpriseConnectorRequests {
    private EnterpriseConnectorRequests() {
    }

    public enum ProviderKind {
        SIS, HRIS, SCIM
    }

    public record RegisterConnectorRequest(
            @NotNull ProviderKind providerKind,
            @NotBlank @Size(max = 64) String adapterKey,
            @NotBlank @Size(max = 96) String providerKey,
            @NotBlank @Size(max = 255) String displayName,
            @NotBlank @Size(max = 255) String secretRef,
            @Size(max = 255) String endpoint,
            @NotBlank @Size(max = 64) String mappingVersion,
            @NotNull @Size(max = 32) Map<String, String> fieldMapping) {
    }

    public record RunRequest(
            @NotNull @PositiveOrZero Long expectedVersion,
            @Size(max = 96) String cursor) {
    }

    public record RetryRequest(
            @NotNull @PositiveOrZero Long expectedVersion) {
    }

    public record DeprovisionRequest(
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 96) String> externalIds,
            @NotBlank @Size(max = 255) String reason) {
    }
}
