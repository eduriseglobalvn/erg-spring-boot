package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KeycloakInternalRealmContractTest {

    @Test
    void realmAllowsOnlyInternalAuthorizationCodeClients() throws Exception {
        JsonNode realm = new ObjectMapper().readTree(Files.readString(Path.of("infra/keycloak/realm-erg.json")));

        assertThat(realm.path("realm").asText()).isEqualTo("erg");
        assertThat(realm.path("registrationAllowed").asBoolean()).isFalse();
        assertThat(realm.path("resetPasswordAllowed").asBoolean()).isFalse();
        assertThat(realm.path("identityProviders").isEmpty()).isTrue();
        assertThat(realm.path("otpPolicyType").asText()).isEqualTo("totp");
        assertThat(realm.path("bruteForceProtected").asBoolean()).isTrue();

        for (JsonNode client : realm.path("clients")) {
            assertThat(client.path("publicClient").asBoolean()).isFalse();
            assertThat(client.path("standardFlowEnabled").asBoolean()).isTrue();
            assertThat(client.path("implicitFlowEnabled").asBoolean()).isFalse();
            assertThat(client.path("directAccessGrantsEnabled").asBoolean()).isFalse();
        }
    }
}
