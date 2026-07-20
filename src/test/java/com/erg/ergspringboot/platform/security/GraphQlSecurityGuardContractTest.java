package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.platform.graphql.ErgGraphQlProperties;
import com.erg.ergspringboot.platform.graphql.GraphQlGuardFilter;
import com.erg.ergspringboot.platform.graphql.GraphQlRateLimiter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class GraphQlSecurityGuardContractTest {

    private GraphQlGuardFilter filter;
    private ErgGraphQlProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ErgGraphQlProperties();
        filter = new GraphQlGuardFilter(new ObjectMapper(), properties, new GraphQlRateLimiter(properties), "/api/v1/graphql");
    }

    @Test
    void securityConfigRequiresAuthenticatedPostToGraphQlEndpoint() throws IOException {
        String securityConfig = Files.readString(
                Path.of("src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java")
        );

        assertThat(securityConfig)
                .contains("HttpMethod.POST")
                .contains("\"/api/v1/graphql\"")
                .contains(".authenticated()");
    }

    @Test
    void graphQlRequestsMustDeclareOperationName() throws Exception {
        MockHttpServletRequest request = graphQlRequest("""
                {"query":"query LearningResourceLibrary { lms { learningResourceLibrary { items { id } } } }"}
                """);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("operationName");
    }

    @Test
    void graphQlIntrospectionIsRejectedWhenDisabled() throws Exception {
        MockHttpServletRequest request = graphQlRequest("""
                {"operationName":"SchemaProbe","query":"query SchemaProbe { __schema { queryType { name } } }"}
                """);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("introspection is disabled");
    }

    @Test
    void graphQlBodyOverTheConfiguredLimitIsRejectedBeforeParsing() throws Exception {
        properties.setMaxRequestBodyBytes(64);
        MockHttpServletRequest request = graphQlRequest("x".repeat(65));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("body is too large");
    }

    @Test
    void oversizedVariableArraysAreRejectedBeforeResolverExecution() throws Exception {
        properties.setMaxVariableArrayItems(2);
        MockHttpServletRequest request = graphQlRequest("""
                {"operationName":"Calendar","query":"query Calendar($ids:[String!]) { lms { myStudentCalendar { events { id } } } }","variables":{"ids":["1","2","3"]}}
                """);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("cardinality");
    }

    private MockHttpServletRequest graphQlRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/graphql");
        request.setContentType("application/json");
        request.setContent(body.getBytes());
        return request;
    }
}
