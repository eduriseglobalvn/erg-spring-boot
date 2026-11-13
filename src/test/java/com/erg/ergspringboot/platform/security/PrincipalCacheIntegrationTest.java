package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erg.ergspringboot.auth.internal.credential.PasswordService;
import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.platform.performance.RequestExecutionContext;
import com.erg.ergspringboot.users.persistence.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PERF-01: a warm principal cache authenticates without SQL, and revocation still takes effect at once. */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PrincipalCacheIntegrationTest {

    private static final String PASSWORD = "Perf01#Pass1";
    // No handler is mapped here: an authenticated request ends in 404, an unauthenticated one in 401,
    // so every statement counted below is spent on authentication.
    private static final String PROBE = "/api/v1/perf01-auth-probe";

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordService passwords;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private UserRepository users;
    @MockitoSpyBean private AuthSessionTenantResolver tenantResolver;

    @Test
    void warmCacheAuthenticatesWithoutSql() throws Exception {
        String token = login(seedUser("role_lms_student"));

        long cold = authStatements(token);
        long warm = authStatements(token);

        System.out.printf("PERF-01 auth SQL per request: cold=%d warm=%d%n", cold, warm);
        assertThat(cold).as("cold request still loads the session").isPositive();
        assertThat(warm).as("warm request must not touch the database to authenticate").isZero();
    }

    @Test
    void logoutRejectsTheCachedTokenImmediately() throws Exception {
        String token = login(seedUser("role_lms_student"));
        probe(token).andExpect(status().isNotFound());
        probe(token).andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        probe(token).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAllRejectsTheCachedTokenImmediately() throws Exception {
        String email = seedUser("role_lms_student");
        String token = login(email);
        probe(token).andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/auth/logout-all").header("Authorization", "Bearer " + login(email)))
                .andExpect(status().isOk());

        probe(token).andExpect(status().isUnauthorized());
    }

    @Test
    void disablingTheAccountRejectsTheCachedTokenImmediately() throws Exception {
        String email = seedUser("role_lms_student");
        String token = login(email);
        probe(token).andExpect(status().isNotFound());

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var user = users.findByIdAndTenantId(email.substring(0, email.indexOf('@')), "erg").orElseThrow();
            user.setStatus("INACTIVE");
            users.save(user);
        });

        probe(token).andExpect(status().isUnauthorized());
    }

    @Test
    void lifecycleFlagChangeIsSeenImmediately() throws Exception {
        String email = seedUser("role_lms_homeroom_teacher");
        String id = email.substring(0, email.indexOf('@'));
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                jdbc.update("update users set must_change_password = false, is_profile_completed = true,"
                        + " recovery_email_verified = true where id = ?", id));
        String token = login(email);
        probe(token).andExpect(status().isNotFound());

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var user = users.findByIdAndTenantId(id, "erg").orElseThrow();
            user.setMustChangePassword(true);
            users.save(user);
        });

        probe(token).andExpect(status().isForbidden());
    }

    private long authStatements(String token) throws Exception {
        clearInvocations(tenantResolver);
        try (RequestExecutionContext.Scope ignored = RequestExecutionContext.open("perf01-probe")) {
            probe(token).andExpect(status().isNotFound());
            long hibernate = RequestExecutionContext.currentSnapshot().statementCount();
            // The tenant lookup runs through JdbcTemplate, which Hibernate does not count: one call = one statement.
            long jdbcLookups = mockingDetails(tenantResolver).getInvocations().size();
            return hibernate + jdbcLookups;
        }
    }

    private org.springframework.test.web.servlet.ResultActions probe(String token) throws Exception {
        return mockMvc.perform(get(PROBE).header("Authorization", "Bearer " + token));
    }

    private String seedUser(String roleId) {
        String id = "perf01_" + UUID.randomUUID().toString().substring(0, 12);
        String email = id + "@example.test";
        // The pool runs with auto-commit off, so seed rows inside a committed transaction.
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("""
                    insert into users (id, created_at, updated_at, username, email, password_hash, full_name, status,
                        provider, account_type, tenant_id, is_profile_completed, must_change_password,
                        recovery_email_verified, login_count)
                    values (?, now(), now(), ?, ?, ?, 'PERF-01 Account', 'ACTIVE', 'local', 'erg', 'erg',
                        false, true, false, 0)
                    """, id, email, email, passwords.hash(PASSWORD));
            jdbc.update("""
                    insert into user_role_assignments (id, user_id, role_id, tenant_id, scope_type, assigned_by)
                    values (?, ?, ?, 'erg', 'TENANT', 'usr_super_admin')
                    """, UUID.randomUUID().toString(), id, roleId);
        });
        return email;
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + email + "\",\"password\":\"" + PASSWORD
                                + "\",\"deviceId\":\"web-perf01-" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int start = body.indexOf("\"accessToken\":\"") + "\"accessToken\":\"".length();
        return body.substring(start, body.indexOf('"', start));
    }
}
