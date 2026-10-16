package com.erg.ergspringboot.users;

import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.PermissionRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.repository.organization.UserAccessScopeRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import com.erg.ergspringboot.service.auth.AccountMailSender;
import com.erg.ergspringboot.service.auth.PasswordService;
import com.erg.ergspringboot.users.internal.create.UserProvisioningUseCase;
import com.erg.ergspringboot.users.internal.lifecycle.UserLifecycleUseCase;
import com.erg.ergspringboot.users.internal.password.UserPasswordUseCase;
import com.erg.ergspringboot.users.internal.profile.UserProfileUseCase;
import com.erg.ergspringboot.users.internal.query.UserQueryUseCase;
import com.erg.ergspringboot.users.internal.roleassignment.UserRoleAssignmentUseCase;
import com.erg.ergspringboot.users.internal.session.UserSessionUseCase;

/** Temporary repository construction seam for the deprecated user-service adapter. */
@Deprecated
public final class UserOperationsCompatibility {
    private UserOperationsCompatibility() { }

    public static UserOperations fromRepositories(
            UserRepository users,
            AuthSessionRepository sessions,
            RoleRepository roles,
            PermissionRepository permissions,
            UserPermissionOverrideRepository overrides,
            UserActivityRepository activities,
            PasswordService passwordService,
            UserAccessScopeRepository scopes,
            AccountMailSender mail) {
        return new UserOperations(
                new UserQueryUseCase(users, sessions, activities),
                new UserProfileUseCase(users, activities),
                new UserPasswordUseCase(users, sessions, activities, passwordService),
                new UserSessionUseCase(sessions, activities),
                new UserProvisioningUseCase(users, sessions, roles, scopes, activities, passwordService, mail),
                new UserLifecycleUseCase(users, sessions, activities),
                new UserRoleAssignmentUseCase(users, roles, permissions, overrides, activities, null));
    }

    /** Compatibility-only dependency injection for legacy service tests. */
    public static void setAuthorizationMutationAuditService(
            UserOperations operations,
            com.erg.ergspringboot.service.authorization.AuthorizationMutationAuditService value) {
        operations.setAuthorizationMutationAuditService(value);
    }
}
