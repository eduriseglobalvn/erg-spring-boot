package com.erg.ergspringboot.enterpriseconnectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EnterpriseConnectorPolicyTest {
    @Test
    void onlyServerSideSecretReferencesAreAcceptedAndResponsesNeverNeedRawSecrets() {
        assertThat(EnterpriseConnectorPolicy.secretReference(" secret://vault/tenant-a/sis "))
                .isEqualTo("secret://vault/tenant-a/sis");
        assertThatThrownBy(() -> EnterpriseConnectorPolicy.secretReference("bearer-token-value"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EnterpriseConnectorPolicy.secretReference("secret://vault token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mappingHashIsStableAcrossInputOrderAndBounded() {
        var first = EnterpriseConnectorPolicy.mapping("v1", Map.of("email", "mail", "id", "externalId"));
        var second = EnterpriseConnectorPolicy.mapping("v1", Map.of("id", "externalId", "email", "mail"));
        assertThat(first.hash()).isEqualTo(second.hash()).hasSize(64);
        assertThat(first.fields()).containsExactly("email", "id");
        assertThatThrownBy(() -> EnterpriseConnectorPolicy.mapping("v1", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
