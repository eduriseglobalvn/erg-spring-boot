package com.erg.ergspringboot.users.internal.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.users.UserAuthorizationPort;
import com.erg.ergspringboot.users.UserAuthorizationProjection;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.authorization.UserPermissionOverride;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
@EnabledIf("dockerAvailable")
class UserAuthorizationAdapterIT {

    private static final String TENANT = "tenant-uaa";

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired private UserAuthorizationPort port;
    @Autowired private EntityManager entityManager;

    @Test
    void managedUnitsAreAssignedRootsPlusDescendantsInTheTenantOnly() {
        entityManager.persist(user("u-1", TENANT));
        entityManager.persist(user("granter", TENANT));
        unit("center", TENANT, null);
        unit("school-a", TENANT, "center");
        unit("school-b", TENANT, "center");
        unit("annex", TENANT, "school-a");
        unit("unrelated", TENANT, null);
        unit("foreign", "tenant-other", null);
        unit("foreign-child", "tenant-other", "foreign");
        unit("revoked-root", TENANT, null);
        unit("expired-root", TENANT, null);
        entityManager.flush();
        // cycle: center -> school-a -> annex -> center
        entityManager.find(EducationUnit.class, "center").setParentId("annex");
        assign("u-1", "role_lms_admin", "center", null, null);
        assign("u-1", "role_lms_admin", "foreign", null, null);
        assign("u-1", "role_lms_admin", "revoked-root", OffsetDateTime.now().minusDays(1), null);
        assign("u-1", "role_lms_admin", "expired-root", null, OffsetDateTime.now().minusDays(1));
        entityManager.flush();
        entityManager.clear();

        assertThat(port.managedEducationUnitIds("u-1", TENANT))
                .containsExactlyInAnyOrder("center", "school-a", "school-b", "annex");
        assertThat(port.managedEducationUnitIds("u-1", "tenant-other")).isEmpty();
        assertThat(port.managedEducationUnitIds("nobody", TENANT)).isEmpty();
    }

    @Test
    void effectiveGrantsIncludeActiveAssignmentRolesAndApplyDenies() {
        entityManager.persist(user("u-2", TENANT));
        entityManager.persist(user("granter", TENANT));
        unit("school-x", TENANT, null);
        entityManager.flush();
        assign("u-2", "role_lms_admin", "school-x", null, null);
        assign("u-2", "role_lms_teacher_standard", "school-x", OffsetDateTime.now().minusDays(1), null);
        assign("u-2", "role_lms_teacher_lead", "school-x", null, OffsetDateTime.now().minusDays(1));
        entityManager.persist(new UserPermissionOverride("ov-1", "u-2", null, "lms.read", null, "deny", "test", null, "granter", OffsetDateTime.now()));
        entityManager.flush();
        entityManager.clear();

        UserAuthorizationProjection effective = port.findEffective("u-2", TENANT).orElseThrow();

        assertThat(effective.roleNames()).containsExactly("lms.admin");
        assertThat(effective.effectivePermissions()).contains("notification.campaign.send").doesNotContain("lms.read");
        assertThat(effective.deniedPermissions()).contains("lms.read");
        // the plain role snapshot does not see assignment-only roles; findEffective does
        assertThat(port.find("u-2", TENANT).orElseThrow().roleNames()).doesNotContain("lms.admin");
        assertThat(port.findEffective("u-2", "tenant-other")).isEmpty();
    }

    private void unit(String id, String tenant, String parentId) {
        EducationUnit unit = new EducationUnit();
        unit.setId(id);
        unit.setTenantId(tenant);
        unit.setName(id);
        unit.setType("school");
        unit.setParentId(parentId);
        entityManager.persist(unit);
        entityManager.flush();
    }

    private void assign(String userId, String roleId, String scopeId, OffsetDateTime revokedAt,
            OffsetDateTime expiresAt) {
        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setUserId(userId);
        assignment.setRoleId(roleId);
        assignment.setTenantId(TENANT);
        assignment.setScopeType("SCHOOL");
        assignment.setScopeId(scopeId);
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

    private static User user(String id, String tenant) {
        User user = new User();
        user.setId(id);
        user.setTenantId(tenant);
        user.setEmail(id + "@example.test");
        user.setFullName(id);
        user.setStatus("ACTIVE");
        user.setAccountType("staff");
        user.setMustChangePassword(false);
        user.setIsProfileCompleted(true);
        user.setRecoveryEmailVerified(false);
        return user;
    }

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }
}
