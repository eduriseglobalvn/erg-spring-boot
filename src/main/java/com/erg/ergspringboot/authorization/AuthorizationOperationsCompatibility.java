package com.erg.ergspringboot.authorization;

import com.erg.ergspringboot.authorization.internal.access.AuthorizationAccessQueryUseCase;
import com.erg.ergspringboot.authorization.internal.assignment.AuthorizationDirectRoleUseCase;
import com.erg.ergspringboot.authorization.internal.catalog.AuthorizationPermissionCatalogUseCase;
import com.erg.ergspringboot.authorization.internal.override.AuthorizationPermissionOverrideUseCase;
import com.erg.ergspringboot.authorization.internal.roledefinition.AuthorizationRoleDefinitionUseCase;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.PermissionRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.users.UserAuthorizationCompatibility;

/** Temporary repository construction seam for the deprecated authorization adapter. */
@Deprecated
public final class AuthorizationOperationsCompatibility {
    private AuthorizationOperationsCompatibility() { }

    public static AuthorizationCommandOperations fromRepositories(
            RoleRepository roles,
            PermissionRepository permissions,
            UserPermissionOverrideRepository overrides,
            UserRepository users) {
        return new AuthorizationCommandOperations(
                new AuthorizationPermissionCatalogUseCase(permissions, roles, overrides),
                new AuthorizationRoleDefinitionUseCase(roles, permissions),
                new AuthorizationAccessQueryUseCase(UserAuthorizationCompatibility.fromRepositories(users, overrides)),
                new AuthorizationDirectRoleUseCase(users, roles, null),
                new AuthorizationPermissionOverrideUseCase(users, permissions, overrides, null));
    }
}
