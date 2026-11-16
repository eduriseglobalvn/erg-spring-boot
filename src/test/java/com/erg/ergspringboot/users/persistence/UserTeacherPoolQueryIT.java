package com.erg.ergspringboot.users.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/** The teacher pool is "holds the Teacher role" through user_roles or a live assignment, never a name guess. */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
@EnabledIf("dockerAvailable")
class UserTeacherPoolQueryIT {

    private static final String TENANT = "tenant-pool";

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;

    @Test
    void teacherPoolIncludesAssignmentOnlyTeacher() {
        entityManager.persist(user("direct-teacher", "role_lms_teacher"));
        entityManager.persist(user("assigned-teacher"));
        entityManager.persist(user("revoked-teacher"));
        entityManager.persist(user("expired-teacher"));
        entityManager.persist(user("granter"));
        entityManager.flush();
        assign("assigned-teacher", "role_lms_teacher", null, null);
        assign("revoked-teacher", "role_lms_teacher", OffsetDateTime.now().minusDays(1), null);
        assign("expired-teacher", "role_lms_teacher", null, OffsetDateTime.now().minusDays(1));
        entityManager.flush();
        entityManager.clear();

        assertThat(ids(users.findCalendarTeachersByTenant(TENANT)))
                .containsExactlyInAnyOrder("direct-teacher", "assigned-teacher");
        assertThat(ids(users.findCalendarTeachersByTenant(TENANT, org.springframework.data.domain.PageRequest.ofSize(10))))
                .containsExactlyInAnyOrder("direct-teacher", "assigned-teacher");
    }

    @Test
    void teacherPoolExcludesSuperAdminAndPdt() {
        entityManager.persist(user("super-admin", "role_super_admin"));
        entityManager.persist(user("pdt", "role_pdt_operator"));
        entityManager.persist(user("pdt-assigned"));
        entityManager.persist(user("granter"));
        entityManager.flush();
        assign("pdt-assigned", "role_pdt_operator", null, null);
        entityManager.flush();
        entityManager.clear();

        assertThat(ids(users.findCalendarTeachersByTenant(TENANT))).isEmpty();
    }

    private static List<String> ids(List<User> found) {
        return found.stream().map(User::getId).toList();
    }

    private User user(String id, String... roleIds) {
        User user = new User();
        user.setId(id);
        user.setTenantId(TENANT);
        user.setEmail(id + "@example.test");
        user.setFullName(id);
        user.setStatus("ACTIVE");
        user.setAccountType("staff");
        user.setMustChangePassword(false);
        user.setIsProfileCompleted(true);
        user.setRecoveryEmailVerified(false);
        Set<Role> roles = new LinkedHashSet<>();
        for (String roleId : roleIds) {
            roles.add(entityManager.find(Role.class, roleId));
        }
        user.setRoles(roles);
        return user;
    }

    private void assign(String userId, String roleId, OffsetDateTime revokedAt, OffsetDateTime expiresAt) {
        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setUserId(userId);
        assignment.setRoleId(roleId);
        assignment.setTenantId(TENANT);
        assignment.setScopeType("TENANT");
        assignment.setAssignedBy("granter");
        assignment.setRevokedAt(revokedAt);
        assignment.setRevokedBy(revokedAt == null ? null : "granter");
        entityManager.persist(assignment);
        if (expiresAt != null) {
            // ck_user_role_assignments_expiry wants expires_at after created_at: backdate both together.
            entityManager.flush();
            entityManager.createNativeQuery("update user_role_assignments set created_at = cast(:at as timestamptz) - interval '1 day', "
                    + "expires_at = cast(:at as timestamptz) where id = :id").setParameter("at", expiresAt)
                    .setParameter("id", assignment.getId()).executeUpdate();
        }
    }

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }
}
