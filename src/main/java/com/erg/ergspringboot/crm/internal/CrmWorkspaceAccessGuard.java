package com.erg.ergspringboot.crm.internal;

import com.erg.ergspringboot.authorization.AuthorizationOperations;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import com.erg.ergspringboot.users.persistence.organization.EducationUnitRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Tenant/education-unit scope and permission checks for CRM workspace commands. */
final class CrmWorkspaceAccessGuard {

    private final EducationUnitRepository unitRepository;
    private final AuthorizationOperations authorization;

    CrmWorkspaceAccessGuard(EducationUnitRepository unitRepository, AuthorizationOperations authorization) {
        this.unitRepository = unitRepository;
        this.authorization = authorization;
    }

    EducationUnit requireSchool(UserAccessContext access, String schoolId) {
        EducationUnit unit = unitRepository.findByIdAndTenantId(schoolId, access.tenantId())
                .orElseThrow(() -> new ForbiddenException("CRM school is outside the authenticated tenant"));
        if (!access.canAccessEducationUnit(schoolId)) {
            throw new ForbiddenException("CRM school is outside the authenticated education-unit scope");
        }
        return unit;
    }

    boolean currentPrincipalHas(UserAccessContext access, String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof ErgPrincipal principal
                && access.userId().equals(principal.userId())
                && authorization.isAllowed(principal, permission, null, access.tenantId());
    }
}
