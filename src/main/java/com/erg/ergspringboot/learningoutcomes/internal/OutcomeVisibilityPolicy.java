package com.erg.ergspringboot.learningoutcomes.internal;

import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.required;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.trimToNull;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.model.learningoutcomes.CompetencyAttainment;
import com.erg.ergspringboot.model.learningoutcomes.LearningPath;
import com.erg.ergspringboot.model.learningoutcomes.OutcomeCertificate;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Tenant, scope and learner checks shared by every learning-outcome use case. Write paths fail closed with
 * {@link ForbiddenException}; list reads use the {@code visible*} predicates to drop rows outside scope.
 */
@Component
public class OutcomeVisibilityPolicy {

    String tenant(UserAccessContext access) {
        if (access == null || access.userId() == null || access.userId().isBlank()) {
            throw new ForbiddenException("authenticated access context is required");
        }
        return AuthenticatedTenant.require(access.tenantId());
    }

    void ensureLearnerAccess(UserAccessContext access, String learnerId) {
        String learner = required(learnerId, "learnerId", 96);
        if (learner.equals(access.userId()) || hasManagePermission()) return;
        throw new ForbiddenException("learner is outside the current access context");
    }

    boolean hasManagePermission() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof ErgPrincipal principal)) return false;
        return principal.permissions().stream().map(this::normalizePermission)
                .anyMatch(permission -> permission.equals("*") || permission.equals("learning.outcomes.manage"));
    }

    void ensureScope(UserAccessContext access, String educationUnitId, String classId) {
        if (!access.canAccessEducationUnit(trimToNull(educationUnitId)) || !access.canAccessClass(trimToNull(classId))) {
            throw new ForbiddenException("learning outcome is outside the current scope");
        }
    }

    boolean canSee(UserAccessContext access, LearningPath path) {
        return access.canAccessEducationUnit(trimToNull(path.getEducationUnitId()))
                && access.canAccessClass(trimToNull(path.getClassId()));
    }

    boolean visibleAttainment(UserAccessContext access, Map<String, LearningPath> pathById, CompetencyAttainment attainment) {
        if (!access.canAccessClass(trimToNull(attainment.getClassId()))) return false;
        String pathId = trimToNull(attainment.getLearningPathId());
        if (pathId == null) return true;
        LearningPath value = pathById.get(pathId);
        if (value == null) return false;
        if (!access.canAccessEducationUnit(trimToNull(value.getEducationUnitId()))
                || !access.canAccessClass(trimToNull(value.getClassId()))) return false;
        String pathClassId = trimToNull(value.getClassId());
        String recordClassId = trimToNull(attainment.getClassId());
        return pathClassId == null || recordClassId == null || pathClassId.equals(recordClassId);
    }

    boolean visibleCertificate(UserAccessContext access, Map<String, LearningPath> pathById, OutcomeCertificate certificate) {
        String pathId = trimToNull(certificate.getLearningPathId());
        LearningPath path = pathId == null ? null : pathById.get(pathId);
        return path != null && canSee(access, path);
    }

    private String normalizePermission(String permission) {
        return permission == null ? "" : permission.trim().toLowerCase(Locale.ROOT);
    }
}
