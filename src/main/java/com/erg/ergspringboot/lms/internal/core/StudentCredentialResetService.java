package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.common.response.lms.LmsOperationalResponses.StudentCredentialResetResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import com.erg.ergspringboot.platform.security.StudentAccountPolicy;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.users.CredentialHasher;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.UserActivity;
import com.erg.ergspringboot.users.persistence.UserActivityRepository;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.auth.AuthSession;
import com.erg.ergspringboot.users.persistence.auth.AuthSessionRepository;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Puts a student's password back to the default for the staff who manage the student's class (BUG-A-008 / BUG-C-011).
 * The password is returned once and never logged or audited; a student is never forced to change it (STU-01).
 */
@Service
public class StudentCredentialResetService {

    /** Owner rule 19/11/2026: a reset puts every student back on this one default password. */
    static final String DEFAULT_PASSWORD = "erg2026";

    private final ClassroomRepository classrooms;
    private final StudentRepository students;
    private final UserRepository users;
    private final UserRoleAssignmentRepository assignments;
    private final AuthSessionRepository sessions;
    private final UserActivityRepository activities;
    private final CredentialHasher passwords;
    private final com.erg.ergspringboot.users.persistence.auth.AuthLoginAttemptRepository loginAttempts;
    private final com.erg.ergspringboot.lms.LmsCallerPermissions permissions;

    public StudentCredentialResetService(ClassroomRepository classrooms, StudentRepository students, UserRepository users,
            UserRoleAssignmentRepository assignments, AuthSessionRepository sessions,
            UserActivityRepository activities, CredentialHasher passwords,
            com.erg.ergspringboot.users.persistence.auth.AuthLoginAttemptRepository loginAttempts,
            com.erg.ergspringboot.lms.LmsCallerPermissions permissions) {
        this.loginAttempts = loginAttempts;
        this.permissions = permissions;
        this.classrooms = classrooms;
        this.students = students;
        this.users = users;
        this.assignments = assignments;
        this.sessions = sessions;
        this.activities = activities;
        this.passwords = passwords;
    }

    @Transactional
    public StudentCredentialResetResponse reset(UserAccessContext access, String classId, String studentId,
            String requestId, String ipAddress, String userAgent) {
        String tenantId = access.tenantId();
        Classroom classroom = classrooms.findByTenantIdAndId(tenantId, classId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found."));
        // Teachers: only a class they manage. Student managers (PĐT, admin) have no class scope, so their
        // school scope decides (BUG-A-008).
        boolean inScope = access.managedClassIds().contains(classId)
                || (access.canAccessEducationUnit(classroom.getSchoolId())
                        && (access.canAccessClass(classId) || permissions.hasAny("lms.student.manage", "*")));
        if (!inScope) throw new ForbiddenException("You can only manage students of your own classes.");
        Student student = students.findByTenantIdAndId(tenantId, studentId)
                .filter(s -> classId.equals(s.getAcademicClassId()))
                .orElseThrow(() -> new ResourceNotFoundException("Student not found in this class."));
        if (student.getAuthUserId() == null) throw new ResourceNotFoundException("Student has no login account.");
        User user = users.findByIdAndTenantId(student.getAuthUserId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student has no login account."));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // Never let this route rotate a staff login that happens to be linked to a student record.
        if (!StudentAccountPolicy.isStudentOnly(user, assignments.findActiveForUser(user.getId(), tenantId, now), now)) {
            throw new ForbiddenException("Only student accounts can be reset here.");
        }
        String password = DEFAULT_PASSWORD;
        user.setPasswordHash(passwords.hash(password));
        user.setMustChangePassword(false);
        users.save(user);
        List<AuthSession> active = sessions.findByUserIdAndTenantIdAndRevokedAtIsNull(user.getId(), tenantId);
        active.forEach(session -> { session.setRevokedAt(now); session.setRevokedReason("credential_reset"); });
        if (!active.isEmpty()) sessions.saveAll(active);
        loginAttempts.recordCredentialReset(user.getTenantId(), user.getId(), now);
        UserActivity activity = new UserActivity();
        activity.setId(IdGenerator.newId());
        activity.setUserId(access.userId());
        activity.setTenantId(tenantId);
        activity.setAction("STUDENT_CREDENTIAL_RESET");
        activity.setTargetUserId(user.getId());
        activity.setDescription("Student password reissued");
        activity.setIpAddress(ipAddress);
        activity.setUserAgent(userAgent);
        activity.setRequestId(requestId);
        activity.setMetadata(Map.of("classId", classId, "studentId", studentId, "revokedSessions", active.size()));
        activity.setCreatedAt(now);
        activities.save(activity);
        return new StudentCredentialResetResponse(student.getId(), user.getUsername(), password);
    }
}
