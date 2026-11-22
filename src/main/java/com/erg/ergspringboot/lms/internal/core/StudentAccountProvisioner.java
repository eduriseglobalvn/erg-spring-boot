package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.platform.security.ErgAuthProperties;
import com.erg.ergspringboot.users.CredentialHasher;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import org.springframework.stereotype.Component;

/**
 * Creates the login account of a student imported by an admin: active, role {@code lms.learner} on the school,
 * and already past onboarding (profile completed, recovery email verified, no forced password change), so the
 * student can sign in straight to the learner portal with the handed-out credentials.
 */
@Component
class StudentAccountProvisioner {
    static final String STUDENT_ROLE_ID = "role_lms_student";

    private final UserRepository users;
    private final UserRoleAssignmentRepository assignments;
    private final CredentialHasher passwords;
    private final ErgAuthProperties authProperties;

    StudentAccountProvisioner(UserRepository users, UserRoleAssignmentRepository assignments, CredentialHasher passwords,
                              ErgAuthProperties authProperties) {
        this.authProperties = authProperties;
        this.users = users;
        this.assignments = assignments;
        this.passwords = passwords;
    }

    /**
     * The login an import may use: the requested one, or the first free numbered variant (ten.ho.lop2, ...).
     * Two schools can both have a class 8A with a student of the same name (BUG-A-003).
     */
    String freeLogin(String username) {
        String login = username == null || username.isBlank() ? null : username.trim().toLowerCase();
        if (login == null || !users.existsByUsernameIgnoreCase(login)) return login;
        for (int n = 2; n <= 99; n++) {
            if (!users.existsByUsernameIgnoreCase(login + n)) return login + n;
        }
        return login; // provision() reports the conflict
    }

    record ImportedAccount(String userId, String login) { }

    /**
     * Import path: a taken login is numbered instead of refused. Owner rule 21/11/2026: a missing password, or one
     * equal to the username (older frontends), becomes the default password (erg.auth.student-default-password).
     */
    ImportedAccount provisionForImport(UserAccessContext access, String schoolId, String username, String email,
                                       String fullName, String phone, String password) {
        String login = freeLogin(username);
        boolean sameAsUsername = password != null && username != null && password.equalsIgnoreCase(username.trim());
        return new ImportedAccount(
                provision(access, schoolId, login, email, fullName, phone, sameAsUsername ? null : password), login);
    }

    /** Returns the new user's id. Throws {@link ConflictException} when the username or email is taken. */
    String provision(UserAccessContext access, String schoolId, String username, String email, String fullName,
                     String phone, String password) {
        String login = username == null || username.isBlank() ? null : username.trim().toLowerCase();
        if (login == null) throw new BadRequestException("Username is required to create a student account.");
        if (password != null && password.isBlank()) password = null;
        if (password == null || password.equalsIgnoreCase(login)) password = authProperties.getStudentDefaultPassword();
        if (password.length() < 6 || password.length() > 128) {
            throw new BadRequestException("Student password must be 6-128 characters.");
        }
        String mail = email == null || email.isBlank() ? null : email.trim().toLowerCase();
        if (users.existsByUsernameIgnoreCase(login) || (mail != null && users.existsByEmailIgnoreCase(mail))) {
            throw new ConflictException("Login '" + login + "' is already used by another account.");
        }
        User user = new User();
        user.setId(IdGenerator.newId());
        user.setTenantId(access.tenantId());
        user.setUsername(login);
        user.setEmail(mail);
        user.setPasswordHash(passwords.hash(password));
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setStatus("ACTIVE");
        user.setProvider("local");
        user.setAccountType("lms");
        user.setIsProfileCompleted(true);
        user.setRecoveryEmailVerified(true);
        user.setMustChangePassword(false);
        user.setLoginCount(0L);
        users.save(user);

        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setId(IdGenerator.newId());
        assignment.setUserId(user.getId());
        assignment.setRoleId(STUDENT_ROLE_ID);
        assignment.setTenantId(access.tenantId());
        assignment.setScopeType("SCHOOL");
        assignment.setScopeId(schoolId);
        assignment.setAssignedBy(access.userId());
        assignments.save(assignment);
        return user.getId();
    }
}
