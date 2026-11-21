package com.erg.ergspringboot.controller.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.curriculum.CurriculumOperations;
import com.erg.ergspringboot.platform.security.MethodAuthorizationEvaluator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(CurriculumImpactAccessTest.Config.class)
class CurriculumImpactAccessTest {

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean(name = "methodAuthorization")
        MethodAuthorizationEvaluator methodAuthorization() {
            return mock(MethodAuthorizationEvaluator.class);
        }

        @Bean
        CurriculumOperations curriculumOperations() {
            return mock(CurriculumOperations.class);
        }

        @Bean
        CurriculumController controller(CurriculumOperations operations) {
            return new CurriculumController(operations, null, null, null, null);
        }
    }

    @Autowired
    private CurriculumController controller;
    @Autowired
    private MethodAuthorizationEvaluator evaluator;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readerGetsForbiddenOnAllThreeImpactEndpoints() {
        var auth = new TestingAuthenticationToken("reader", "x", "ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(evaluator.isAllowedAny(eq(auth), any(String[].class))).thenReturn(false);
        var request = new MockHttpServletRequest();

        assertThatThrownBy(() -> controller.subjectImpact("s1", request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.levelImpact("l1", request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.topicImpact("t1", request)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void managerReceivesTheImpact() {
        var auth = new TestingAuthenticationToken("manager", "x", "ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(evaluator.isAllowedAny(eq(auth), eq("curriculum.manage"), eq("*"))).thenReturn(true);

        // Past the permission check the handler builds the access context, which this bare token cannot satisfy.
        assertThatThrownBy(() -> controller.subjectImpact("s1", new MockHttpServletRequest()))
                .isNotInstanceOf(AccessDeniedException.class).hasMessageContaining("verified principal");
    }
}
