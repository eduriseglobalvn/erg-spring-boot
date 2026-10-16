package com.erg.ergspringboot.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.Role;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class BaseEntityIdentityContractTest {

    @Test
    void twoTransientEntitiesAreNeverEqualUnlessTheyAreSameReference() {
        Role first = new Role();
        Role second = new Role();

        assertThat(first).isEqualTo(first).isNotEqualTo(second);
    }

    @Test
    void persistedIdentityIgnoresMutableFieldsAndLifecycleTimestamps() {
        Role first = role("role-1", "first", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        Role second = role("role-1", "second", OffsetDateTime.parse("2026-07-30T00:00:00Z"));

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    void equalIdentifiersFromDifferentEntityTypesAreNotEqual() {
        Role role = role("same-id", "role", OffsetDateTime.now());
        Permission permission = new Permission();
        permission.setId("same-id");

        assertThat(role).isNotEqualTo(permission);
    }

    @Test
    void hashCodeRemainsStableWhenPrePersistAssignsIdentifier() throws Exception {
        Role role = new Role();
        int beforePersist = role.hashCode();

        Method onCreate = BaseEntity.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        onCreate.invoke(role);

        assertThat(role.getId()).isNotBlank();
        assertThat(role.hashCode()).isEqualTo(beforePersist);
    }

    private Role role(String id, String name, OffsetDateTime updatedAt) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setUpdatedAt(updatedAt);
        return role;
    }
}
