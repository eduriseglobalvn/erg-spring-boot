package com.erg.ergspringboot.platform.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;

import com.erg.ergspringboot.auth.internal.credential.PasswordService;
import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** STU-01: a student-only account is never held in the first-login lifecycle; staff accounts still are. */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class StudentLifecycleExemptionIntegrationTest {

    private static final String PASSWORD = "Student#Pass1";

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordService passwords;
    @Autowired private PlatformTransactionManager transactions;

    @Test
    void studentWithMustChangePasswordStillReadsOwnAssignments() throws Exception {
        String token = login(seedUser("role_lms_student"));

        mockMvc.perform(get("/api/v1/lms/students/me/assignments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void teacherWithMustChangePasswordIsStillLifecycleRestricted() throws Exception {
        String token = login(seedUser("role_lms_homeroom_teacher"));

        mockMvc.perform(get("/api/v1/lms/classes").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors").value(containsString("ACCOUNT_LIFECYCLE_RESTRICTED")));
    }

    @Test
    void studentBootstrapCountsOnlyActiveRoleAssignments() throws Exception {
        String email = seedUser("role_lms_student");
        String id = email.substring(0, email.indexOf('@'));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("""
                    insert into user_role_assignments (id, user_id, role_id, tenant_id, scope_type, assigned_by,
                        revoked_at, revoked_by)
                    values (?, ?, 'role_lms_homeroom_teacher', 'erg', 'TENANT', 'usr_super_admin', now(), 'usr_super_admin')
                    """, UUID.randomUUID().toString(), id);
            jdbc.update("""
                    insert into user_role_assignments (id, user_id, role_id, tenant_id, scope_type, assigned_by,
                        created_at, expires_at)
                    values (?, ?, 'role_lms_homeroom_teacher', 'erg', 'TENANT', 'usr_super_admin',
                        now() - interval '2 days', now() - interval '1 day')
                    """, UUID.randomUUID().toString(), id);
        });

        mockMvc.perform(get("/api/v1/sessions/current").header("Authorization", "Bearer " + login(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspaces").value(contains("STUDENT")))
                .andExpect(jsonPath("$.data.lifecycle.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.lifecycle.nextSteps").isEmpty());
    }

    @Test
    void teacherBootstrapReadsWorkspaceFromRoleAssignment() throws Exception {
        String token = login(seedUser("role_lms_homeroom_teacher"));

        mockMvc.perform(get("/api/v1/sessions/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspaces").value(hasItem("TEACHER")))
                .andExpect(jsonPath("$.data.lifecycle.nextSteps").value(hasItem("CHANGE_PASSWORD")));
    }

    private String seedUser(String roleId) {
        String id = "stu01_" + UUID.randomUUID().toString().substring(0, 12);
        String email = id + "@example.test";
        // The pool runs with auto-commit off, so seed rows inside a committed transaction.
        new TransactionTemplate(transactions).executeWithoutResult(status -> seedRows(id, email, roleId));
        return email;
    }

    private void seedRows(String id, String email, String roleId) {
        jdbc.update("""
                insert into users (id, created_at, updated_at, username, email, password_hash, full_name, status,
                    provider, account_type, tenant_id, is_profile_completed, must_change_password,
                    recovery_email_verified, login_count)
                values (?, now(), now(), ?, ?, ?, 'STU-01 Account', 'ACTIVE', 'local', 'erg', 'erg',
                    false, true, false, 0)
                """, id, email, email, passwords.hash(PASSWORD));
        jdbc.update("""
                insert into user_role_assignments (id, user_id, role_id, tenant_id, scope_type, assigned_by)
                values (?, ?, ?, 'erg', 'TENANT', 'usr_super_admin')
                """, UUID.randomUUID().toString(), id, roleId);
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"deviceId\":\"web-stu01\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int start = body.indexOf("\"accessToken\":\"") + "\"accessToken\":\"".length();
        return body.substring(start, body.indexOf('"', start));
    }
}
