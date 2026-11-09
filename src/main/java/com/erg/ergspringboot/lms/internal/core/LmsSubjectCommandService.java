package com.erg.ergspringboot.lms.internal.core;

import static com.erg.ergspringboot.common.request.lms.LmsOperationalRequests.*;
import static com.erg.ergspringboot.common.response.lms.LmsOperationalResponses.*;
import static com.erg.ergspringboot.lms.internal.core.LmsOperationalResponseMapper.*;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.ContentScope;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import com.erg.ergspringboot.lms.persistence.Subject;
import com.erg.ergspringboot.lms.persistence.SubjectRepository;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.users.persistence.UserActivity;
import com.erg.ergspringboot.users.persistence.organization.EducationUnitRepository;
import com.erg.ergspringboot.users.persistence.UserActivityRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Write side of the subject directory, split from {@link LmsOperationalCommandService} to keep both small. */
@Service
public class LmsSubjectCommandService {
    private static final Set<String> ACTIVE_STATUSES = Set.of("ACTIVE", "INACTIVE", "ARCHIVED", "DRAFT", "ENROLLED");

    private final SubjectRepository subjects;
    private final LmsOperationalScopeGuard guard;
    private final UserActivityRepository activities;

    public LmsSubjectCommandService(
            ClassroomRepository classrooms,
            StudentRepository students,
            SubjectRepository subjects,
            EducationUnitRepository schools,
            UserActivityRepository activities) {
        this.subjects = subjects;
        this.guard = new LmsOperationalScopeGuard(classrooms, students, subjects, schools);
        this.activities = activities;
    }

    @Transactional
    @CacheEvict(cacheNames = {"lmsSchoolSubjects", "lcmsSchoolOperations", "lcmsSchoolDirectory"}, allEntries = true)
    public SubjectResponse createSubject(CreateSubjectRequest request, UserAccessContext access) {
        String schoolId = trim(request.schoolId());
        if (schoolId != null) {
            guard.assertSchool(access, schoolId);
            guard.school(access, schoolId);
        }
        String code = required(request.code(), "Subject code");
        if (subjects.findByTenantIdAndCodeIgnoreCase(access.tenantId(), code).isPresent()) {
            throw new ConflictException("Subject code already exists in this tenant.");
        }
        Subject subject = new Subject();
        subject.setId(IdGenerator.newId());
        subject.setTenantId(access.tenantId());
        subject.setScope(new ContentScope(schoolId == null ? "tenant" : "school", schoolId));
        subject.setName(required(request.name(), "Subject name"));
        subject.setCode(code);
        subject.setStatus(status(request.status(), "ACTIVE"));
        Subject saved = subjects.save(subject);
        audit(access, "LMS_SUBJECT_CREATED", saved.getId(), Map.of("schoolId", schoolId == null ? "tenant" : schoolId));
        return subjectResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {"lmsSchoolSubjects", "lcmsSchoolOperations", "lcmsSchoolDirectory"}, allEntries = true)
    public SubjectResponse updateSubject(String subjectId, UpdateSubjectRequest request, UserAccessContext access) {
        Subject subject = guard.subject(access, subjectId);
        guard.assertSubjectScope(access, subject);
        assertVersion(request.version(), subject.getRecordVersion(), "Subject");
        if (request.code() != null && !normalize(request.code()).equals(normalize(subject.getCode()))) {
            subjects.findByTenantIdAndCodeIgnoreCase(access.tenantId(), request.code().trim())
                    .filter(existing -> !existing.getId().equals(subjectId))
                    .ifPresent(existing -> { throw new ConflictException("Subject code already exists in this tenant."); });
            subject.setCode(required(request.code(), "Subject code"));
        }
        if (request.name() != null) subject.setName(required(request.name(), "Subject name"));
        if (request.status() != null) subject.setStatus(status(request.status(), subject.getStatus()));
        Subject saved = subjects.save(subject);
        audit(access, "LMS_SUBJECT_UPDATED", saved.getId(), Map.of("scope", "tenant"));
        return subjectResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {"lmsSchoolSubjects", "lcmsSchoolOperations", "lcmsSchoolDirectory"}, allEntries = true)
    public SubjectResponse archiveSubject(String subjectId, Long version, UserAccessContext access) {
        Subject subject = guard.subject(access, subjectId);
        guard.assertSubjectScope(access, subject);
        assertVersion(version, subject.getRecordVersion(), "Subject");
        subject.setStatus("ARCHIVED");
        Subject saved = subjects.save(subject);
        audit(access, "LMS_SUBJECT_ARCHIVED", saved.getId(), Map.of("scope", "tenant"));
        return subjectResponse(saved);
    }

    private void assertVersion(Long requested, Long actual, String resource) {
        if (requested == null) {
            throw new BadRequestException(resource + " version is required.");
        }
        if (!Objects.equals(requested, actual == null ? 0L : actual)) {
            throw new ConflictException(resource + " has been updated by another request.");
        }
    }

    private void audit(UserAccessContext access, String action, String resourceId, Map<String, Object> metadata) {
        UserActivity activity = new UserActivity();
        activity.setId(IdGenerator.newId()); activity.setUserId(access.userId()); activity.setTenantId(access.tenantId());
        activity.setAction(action); activity.setDescription("LMS operational mutation"); activity.setMetadata(metadata);
        activity.setCreatedAt(OffsetDateTime.now()); activities.save(activity);
    }

    private String status(String value, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!ACTIVE_STATUSES.contains(normalized)) throw new BadRequestException("Unsupported status.");
        return normalized;
    }
    private String required(String value, String name) { if (value == null || value.isBlank()) throw new BadRequestException(name + " is required."); return value.trim(); }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
}
