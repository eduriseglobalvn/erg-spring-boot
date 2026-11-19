package com.erg.ergspringboot.learningoutcomes.internal;

import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.required;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.trimToNull;

import com.erg.ergspringboot.authorization.catalog.BusinessRoleCatalog;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.learningoutcomes.persistence.CompetencyAttainment;
import com.erg.ergspringboot.learningoutcomes.persistence.LearningPath;
import com.erg.ergspringboot.learningoutcomes.persistence.OutcomeCertificate;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
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

    private final StudentRepository students;

    public OutcomeVisibilityPolicy(StudentRepository students) {
        this.students = students;
    }

    String tenant(UserAccessContext access) {
        if (access == null || access.userId() == null || access.userId().isBlank()) {
            throw new ForbiddenException("authenticated access context is required");
        }
        return AuthenticatedTenant.require(access.tenantId());
    }

    void ensureLearnerAccess(UserAccessContext access, String learnerId) {
        String learner = required(learnerId, "learnerId", 96);
        if (learner.equals(access.userId()) || hasManagePermission() || teacherScopeCovers(access, learner)) return;
        throw new ForbiddenException("learner is outside the current access context");
    }

    /** Owner rule 18/11/2026: a teacher reads outcomes of learners in the classes or schools they are assigned to. */
    private boolean teacherScopeCovers(UserAccessContext access, String learnerId) {
        ErgPrincipal principal = currentPrincipal();
        // Students may carry their own class scope: only the teacher role gets the scoped read, never a classmate.
        if (principal == null || !principal.roles().contains(BusinessRoleCatalog.TEACHER)) return false;
        if (!access.hasManagedClassScope() && !access.hasManagedEducationUnitScope()) return false;
        return students.findFirstByTenantIdAndAuthUserId(access.tenantId(), learnerId)
                .filter(student -> (access.hasManagedClassScope() && hasText(student.getAcademicClassId())
                        && access.canAccessClass(student.getAcademicClassId()))
                        || (access.hasManagedEducationUnitScope() && hasText(student.getSchoolId())
                        && access.canAccessEducationUnit(student.getSchoolId())))
                .isPresent();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ErgPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof ErgPrincipal principal ? principal : null;
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
