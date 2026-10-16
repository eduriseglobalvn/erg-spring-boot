package com.erg.ergspringboot.enterpriseconnectors.internal;

import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorProviderPort;
import com.erg.ergspringboot.exception.BadRequestException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Provider registry; adding a vendor adapter never changes the application service. */
@Component
public class EnterpriseConnectorProviderRegistry {
    private final Map<String, EnterpriseConnectorProviderPort> providers;

    public EnterpriseConnectorProviderRegistry(List<EnterpriseConnectorProviderPort> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> normalize(provider.adapterKey()), Function.identity(), (left, right) -> {
                    throw new IllegalStateException("duplicate enterprise connector adapter");
                }));
    }

    public EnterpriseConnectorProviderPort require(String adapterKey) {
        EnterpriseConnectorProviderPort provider = providers.get(normalize(adapterKey));
        if (provider == null) throw new BadRequestException("unsupported connector adapter");
        return provider;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
