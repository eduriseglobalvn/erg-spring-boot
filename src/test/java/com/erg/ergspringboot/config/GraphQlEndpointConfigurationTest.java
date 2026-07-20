package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.graphql.autoconfigure.GraphQlProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

class GraphQlEndpointConfigurationTest {

    @Test
    void pomKeepsSpringBootGraphQlDependencies() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom)
                .contains("<artifactId>spring-boot-starter-graphql</artifactId>");
    }

    @Test
    void applicationYamlPinsSingleVersionedGraphQlEndpoint() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationYaml)
                .contains("graphql:")
                .contains("http:")
                .contains("path: ${SPRING_GRAPHQL_PATH:/api/v1/graphql}")
                .contains("schema:")
                .contains("introspection:")
                .contains("enabled: ${SPRING_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED:${ERG_GRAPHQL_INTROSPECTION_ENABLED:false}}");
        assertThat(applicationYaml)
                .doesNotContain("spring.graphql.path")
                .doesNotContain("/api/v1/lms/graphql")
                .doesNotContain("/api/v1/lcms/graphql")
                .doesNotContain("/api/v1/crm/graphql");
    }

    @Test
    void applicationYamlBindsBootFourGraphQlHttpEndpoint() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load("application", new FileSystemResource("src/main/resources/application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        GraphQlProperties properties = Binder.get(environment)
                .bind("spring.graphql", GraphQlProperties.class)
                .orElseThrow(() -> new IllegalStateException("spring.graphql properties must bind"));

        assertThat(properties.getHttp().getPath()).isEqualTo("/api/v1/graphql");
    }

    @Test
    void productionDisablesGraphiQlAndSchemaIntrospection() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));
        String prodYaml = profileSection("prod");

        assertThat(applicationYaml)
                .contains("enabled: ${SPRING_GRAPHQL_GRAPHIQL_ENABLED:false}")
                .contains("introspection-enabled: ${ERG_GRAPHQL_INTROSPECTION_ENABLED:false}");
        assertThat(prodYaml)
                .doesNotContain("SPRING_GRAPHQL_GRAPHIQL_ENABLED:true")
                .doesNotContain("ERG_GRAPHQL_INTROSPECTION_ENABLED:true")
                .doesNotContain("introspection-enabled: true");
    }

    private String profileSection(String profileName) throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));
        String marker = "on-profile: " + profileName;
        int profileStart = applicationYaml.indexOf(marker);
        assertThat(profileStart).as("profile section %s exists", profileName).isNotNegative();
        int nextDocument = applicationYaml.indexOf("\n---", profileStart);
        return nextDocument < 0 ? applicationYaml.substring(profileStart) : applicationYaml.substring(profileStart, nextDocument);
    }
}
