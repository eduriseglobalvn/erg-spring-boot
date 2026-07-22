package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GraphQlSchemaCompatibilityTest {

    @Test
    void additiveFieldsAreCompatible() {
        var changes = GraphQlSchemaCompatibility.breakingChanges(
                "type Query { ping: String! }",
                "type Query { ping: String! pong: String }\n");

        assertThat(changes).isEmpty();
    }

    @Test
    void removedFieldsAndRequiredInputArgumentsAreBreaking() {
        var changes = GraphQlSchemaCompatibility.breakingChanges(
                "type Query { ping(input: PingInput): String! }\n"
                        + "input PingInput { value: String }\n",
                "type Query { }\ninput PingInput { value: String! }\n");

        assertThat(changes).containsExactlyInAnyOrder(
                "Query.ping: field removed",
                "PingInput.value: input field became non-null");
    }

    @Test
    void removedEnumValuesAndNewRequiredArgumentsAreBreaking() {
        var changes = GraphQlSchemaCompatibility.breakingChanges(
                "type Query { search(status: Status): String }\n"
                        + "enum Status { OPEN CLOSED }\n",
                "type Query { search(status: Status, tenantId: String!): String }\n"
                        + "enum Status { OPEN }\n");

        assertThat(changes).containsExactlyInAnyOrder(
                "Query.search(tenantId): required argument added",
                "Status.CLOSED: enum value removed");
    }

    @Test
    void configuredBaseSchemaIsCheckedByCiWhenPresent() throws Exception {
        String base = System.getProperty("erg.graphql.base-schema");
        if (base == null || base.isBlank()) return;

        List<String> changes = GraphQlSchemaCompatibility.breakingChanges(
                java.nio.file.Files.readString(java.nio.file.Path.of(base)),
                java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/graphql/schema.graphqls")));

        assertThat(changes).as("GraphQL schema breaking changes").isEmpty();
    }
}
