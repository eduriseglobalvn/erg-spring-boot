package com.erg.ergspringboot.enterpriseconnectors.internal;

import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorPolicy;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorProviderPort;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.ProviderKind;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Deterministic sandbox adapter used for conformance tests and local development. */
@Component
public class ReferenceEnterpriseConnectorAdapter implements EnterpriseConnectorProviderPort {
    private static final int MAX_SAMPLE = 20;
    private static final int MAX_RECORDS = 100;

    @Override
    public String adapterKey() {
        return "REFERENCE";
    }

    @Override
    public boolean supports(ProviderKind providerKind) {
        return providerKind != null;
    }

    @Override
    public PreviewResult preview(ConnectorDescriptor connector, String cursor) {
        List<String> ids = ids(connector, cursor);
        int additions = Math.max(1, ids.size() / 3);
        int updates = Math.max(1, ids.size() / 3);
        int removals = Math.max(0, ids.size() - additions - updates);
        return new PreviewResult(additions, updates, removals, ids.subList(0, Math.min(MAX_SAMPLE, ids.size())));
    }

    @Override
    public SyncResult sync(ConnectorDescriptor connector, String cursor) {
        List<String> ids = ids(connector, cursor);
        int created = Math.max(1, ids.size() / 3);
        int updated = Math.max(0, ids.size() - created);
        return new SyncResult(ids.size(), created, updated, 0, 0, "SUCCEEDED", null);
    }

    @Override
    public DeprovisionResult deprovision(ConnectorDescriptor connector, List<String> externalIds) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (externalIds != null) {
            externalIds.stream().filter(value -> value != null && !value.isBlank())
                    .map(String::trim).limit(MAX_RECORDS).forEach(unique::add);
        }
        int deprovisioned = unique.size();
        return new DeprovisionResult(deprovisioned, Math.max(0, (externalIds == null ? 0 : externalIds.size()) - deprovisioned),
                "DEPROVISIONED");
    }

    private List<String> ids(ConnectorDescriptor connector, String cursor) {
        String seed = String.join("\u001f", connector.providerKey(), connector.providerKind().name(),
                connector.mappingVersion(), connector.mappingHash(), cursor == null ? "" : cursor.trim());
        int count = 3 + (Integer.parseInt(EnterpriseConnectorPolicy.sha256(seed).substring(0, 2), 16) % 5);
        List<String> ids = new ArrayList<>();
        String prefix = connector.providerKind().name().toLowerCase(Locale.ROOT);
        for (int index = 0; index < Math.min(MAX_RECORDS, count); index++) {
            ids.add("reference:" + prefix + ":" + EnterpriseConnectorPolicy.sha256(seed + ":" + index).substring(0, 20));
        }
        return List.copyOf(ids);
    }
}
