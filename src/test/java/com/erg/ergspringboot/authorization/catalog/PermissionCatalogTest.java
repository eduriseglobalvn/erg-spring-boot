package com.erg.ergspringboot.authorization.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.authorization.catalog.PermissionCatalog.PermissionDef;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PermissionCatalogTest {

    @Test
    void everyPermissionHasFunctionAndKind() {
        Set<String> seen = new HashSet<>();
        for (PermissionDef def : PermissionCatalog.all()) {
            assertThat(def.name()).isNotBlank();
            assertThat(def.function()).as(def.name()).isNotNull();
            assertThat(def.kind()).as(def.name()).isNotNull();
            assertThat(seen.add(def.name())).as("duplicate " + def.name()).isTrue();
            assertThat(PermissionCatalog.kindOf(def.name())).contains(def.kind());
        }
    }

    @Test
    void exportsAreXemAndRoleDefinitionPermissionsAreGone() {
        PermissionCatalog.all().stream()
                .filter(def -> def.name().endsWith(".export"))
                .forEach(def -> assertThat(def.kind()).as(def.name()).isEqualTo(PermissionKind.XEM));
        assertThat(PermissionCatalog.kindOf("rbac.role.create")).isEmpty();
        assertThat(PermissionCatalog.kindOf("rbac.role.update")).isEmpty();
        assertThat(PermissionCatalog.kindOf("rbac.role.delete")).isEmpty();
        assertThat(PermissionCatalog.kindOf("rbac.*")).isEmpty();
        assertThat(PermissionCatalog.kindOf("rbac.override.manage")).isEmpty();
    }

    @Test
    void newReadPermissionsAreXem() {
        for (String name : new String[] {"users.read", "users.activity.read", "rbac.assignment.read", "elearning.read",
                "curriculum.read", "lms.subject.read", "documents.read", "notifications.read", "timekeeping.read",
                "audit.export", "audit.read", "rbac.role.read"}) {
            assertThat(PermissionCatalog.kindOf(name)).as(name).contains(PermissionKind.XEM);
        }
    }
}
