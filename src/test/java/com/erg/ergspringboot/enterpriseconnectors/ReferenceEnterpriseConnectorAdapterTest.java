package com.erg.ergspringboot.enterpriseconnectors;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.ProviderKind;
import com.erg.ergspringboot.enterpriseconnectors.internal.ReferenceEnterpriseConnectorAdapter;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceEnterpriseConnectorAdapterTest {
    private final ReferenceEnterpriseConnectorAdapter adapter = new ReferenceEnterpriseConnectorAdapter();

    @Test
    void referenceAdapterIsDeterministicAndNetworkFreeForEveryProviderKind() {
        for (ProviderKind kind : ProviderKind.values()) {
            var descriptor = new EnterpriseConnectorProviderPort.ConnectorDescriptor(
                    "sandbox-1", kind, "v1", EnterpriseConnectorPolicy.sha256("mapping"));
            var first = adapter.preview(descriptor, null);
            var second = adapter.preview(descriptor, null);
            assertThat(first).isEqualTo(second);
            assertThat(first.sampleExternalIds()).hasSizeBetween(1, 20);
            assertThat(adapter.sync(descriptor, null).failed()).isZero();
        }
    }

    @Test
    void deprovisionDeduplicatesAndBoundsExplicitIds() {
        var descriptor = new EnterpriseConnectorProviderPort.ConnectorDescriptor(
                "sandbox-1", ProviderKind.SCIM, "v1", EnterpriseConnectorPolicy.sha256("mapping"));
        var result = adapter.deprovision(descriptor, List.of("a", "a", "b"));
        assertThat(result.deprovisioned()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("DEPROVISIONED");
    }
}
