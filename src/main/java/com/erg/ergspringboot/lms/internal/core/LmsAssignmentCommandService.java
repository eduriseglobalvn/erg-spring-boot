package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.CreateAssignmentRequest;
import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.UpdateAssignmentRequest;
import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.UpdateRecipientsRequest;
import com.erg.ergspringboot.common.response.quiz.AssignmentResponses.AssignmentResourceResponse;
import com.erg.ergspringboot.common.response.quiz.AssignmentResponses.CreateAssignmentResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lms.LmsAssignmentAccessPolicy;
import com.erg.ergspringboot.lms.LmsAssignmentCreated;
import com.erg.ergspringboot.lms.LmsReadCacheInvalidator;
import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.model.lms.Assignment;
import com.erg.ergspringboot.model.lms.AssignmentRecipient;
import com.erg.ergspringboot.model.lms.AssignmentResource;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.model.quiz.version.QuizVersion;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.platform.sync.LmsSyncOperationService;
import com.erg.ergspringboot.repository.lms.AssignmentRecipientRepository;
import com.erg.ergspringboot.repository.lms.AssignmentRepository;
import com.erg.ergspringboot.repository.lms.AssignmentResourceRepository;
import com.erg.ergspringboot.repository.lms.StudentGroupMemberRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LMS distribution writes (spec §4.8). An assignment fans one or more quizzes to a recipient set. The
 * recipient mode (class / group / students) is resolved server-side into a flat {@code assignment_recipients}
 * row per student, and each quiz is pinned to its active published version at assign time so a later
 * republish never changes what an in-flight cohort sees. Create is idempotent on {@code X-Idempotency-Key}.
 *
 * <p>Transition contract (Phase 4): new assignments also fill the legacy {@code academic_class_id}/{@code quiz_id}
 * columns (primary class + first quiz) so the ~10 legacy read projections that still join
 * {@code Student ON academic_class_id} keep returning while they are migrated to the recipient table.
 */
@Service
@Transactional(value = "postgresTransactionManager")
public class LmsAssignmentCommandService {

    private static final String STATUS_ACTIVE = "active";
    private final AssignmentRepository assignmentRepository;
    private final AssignmentResourceRepository resourceRepository;
    private final AssignmentRecipientRepository recipientRepository;
    private final AssignmentRecipientResolver recipients;
    private final QuizVersionRepository quizVersionRepository;
    private final LmsReadCacheInvalidator caches;
    private final LmsAssignmentAccessPolicy policy;
    private final LmsSyncOperationService sync;
    private final ApplicationEventPublisher events;

    @Autowired
    public LmsAssignmentCommandService(
            AssignmentRepository assignmentRepository,
            AssignmentResourceRepository resourceRepository,
            AssignmentRecipientRepository recipientRepository,
            StudentGroupMemberRepository studentGroupMemberRepository,
            StudentRepository studentRepository,
            QuizVersionRepository quizVersionRepository,
            LmsReadCacheInvalidator caches,
            LmsAssignmentAccessPolicy policy,
            ObjectProvider<LmsSyncOperationService> sync,
            ApplicationEventPublisher events
    ) {
        this(assignmentRepository, resourceRepository, recipientRepository,
                studentGroupMemberRepository, studentRepository, quizVersionRepository, caches, policy,
                sync == null ? null : sync.getIfAvailable(), events);
    }

    /** Explicit wiring; {@code sync} is optional (null disables durable idempotency receipts). */
    public LmsAssignmentCommandService(
            AssignmentRepository assignmentRepository,
            AssignmentResourceRepository resourceRepository,
            AssignmentRecipientRepository recipientRepository,
            StudentGroupMemberRepository studentGroupMemberRepository,
            StudentRepository studentRepository,
            QuizVersionRepository quizVersionRepository,
            LmsReadCacheInvalidator caches,
            LmsAssignmentAccessPolicy policy,
            LmsSyncOperationService sync,
            ApplicationEventPublisher events
    ) {
        this.assignmentRepository = assignmentRepository;
        this.resourceRepository = resourceRepository;
        this.recipientRepository = recipientRepository;
        this.recipients = new AssignmentRecipientResolver(studentRepository, studentGroupMemberRepository);
        this.quizVersionRepository = quizVersionRepository;
        this.caches = caches;
        this.policy = policy;
        this.sync = sync;
        this.events = events;
    }

    public CreateAssignmentResponse create(UserAccessContext access, String idempotencyKey, CreateAssignmentRequest request) {
        String tenantId = AuthenticatedTenant.require(access.tenantId());
        String userId = access.userId();
        if (request == null || request.quizIds() == null || request.quizIds().isEmpty()) {
            throw new BadRequestException("at least one quizId is required");
        }
        String key = blankToNull(idempotencyKey);
        LmsSyncOperationService.Receipt receipt = sync == null || key == null ? null
                : sync.reserve(tenantId, userId, key, key, "lms.assignment", key, request, null).orElse(null);
        if (receipt != null && receipt.replayed()) return sync.result(receipt, CreateAssignmentResponse.class);
        if (key != null) {
            Assignment existing = assignmentRepository.findByTenantIdAndIdempotencyKey(tenantId, key).orElse(null);
            if (existing != null) {
                policy.ensureManagedScope(existing, access);
                CreateAssignmentResponse replay = describe(existing);
                if (receipt != null) sync.complete(receipt, replay, existing.getRecordVersion() == null ? 0L : existing.getRecordVersion());
                return replay;
            }
        }

        String mode = this.recipients.normalizeMode(request.recipientMode());
        if (blankToNull(request.classId()) != null) policy.ensureClassScope(tenantId, request.classId().trim(), access);
        List<Student> recipients = this.recipients.resolveRecipients(tenantId, mode, request.classId(), request.groupId(), request.studentIds());
        if (recipients.isEmpty()) {
            throw new BadRequestException("assignment resolved to zero recipients");
        }
        ensureRecipientScope(tenantId, recipients, access);
        String primaryClassId = this.recipients.primaryClassId(mode, request.classId(), recipients);

        Assignment assignment = new Assignment();
        assignment.setTenantId(tenantId);
        assignment.setTitle(blankToNull(request.title()));
        assignment.setAcademicClassId(primaryClassId);
        assignment.setQuizId(request.quizIds().getFirst());
        assignment.setStudentIds(recipients.stream().map(Student::getId).toList());
        assignment.setRecipientMode(mode);
        assignment.setStartAt(request.startAt());
        assignment.setDueAt(request.dueAt());
        assignment.setAttemptLimit(request.attemptLimit());
        assignment.setMaxDurationMinutes(request.maxDurationMinutes());
        assignment.setOfflineAllowed(Boolean.TRUE.equals(request.offlineAllowed()));
        assignment.setTeacherNote(blankToNull(request.teacherNote()));
        assignment.setStatus(STATUS_ACTIVE);
        assignment.setAssignedBy(userId);
        assignment.setIdempotencyKey(key);
        assignment.setRecipientCount(recipients.size());
        Assignment saved = assignmentRepository.save(assignment);

        List<AssignmentResource> resources = persistResources(tenantId, saved.getId(), request.quizIds());
        persistRecipients(tenantId, saved.getId(), primaryClassId, mode, recipients);
        caches.evictAfterCommit(tenantId, LmsReadCacheInvalidator.ASSIGNMENT_READ_CACHES);
        events.publishEvent(new LmsAssignmentCreated("assignment-created:" + saved.getId(), tenantId, saved.getId(),
                userId, OffsetDateTime.now(ZoneOffset.UTC)));

        CreateAssignmentResponse result = new CreateAssignmentResponse(saved.getId(), recipients.size(), toResourceResponses(resources));
        if (receipt != null) sync.complete(receipt, result, saved.getRecordVersion() == null ? 0L : saved.getRecordVersion());
        return result;
    }

    public CreateAssignmentResponse updateRecipients(UserAccessContext access, String assignmentId, UpdateRecipientsRequest request) {
        String tenantId = AuthenticatedTenant.require(access.tenantId());
        Assignment assignment = requireAssignment(tenantId, assignmentId);
        policy.ensureManagedScope(assignment, access);
        String mode = this.recipients.normalizeMode(request == null ? null : request.recipientMode());
        if (request != null && blankToNull(request.classId()) != null) policy.ensureClassScope(tenantId, request.classId().trim(), access);
        List<Student> recipients = this.recipients.resolveRecipients(
                tenantId, mode,
                request == null ? null : request.classId(),
                request == null ? null : request.groupId(),
                request == null ? null : request.studentIds());
        if (recipients.isEmpty()) {
            throw new BadRequestException("assignment resolved to zero recipients");
        }
        ensureRecipientScope(tenantId, recipients, access);
        recipientRepository.deleteByAssignmentId(assignmentId);
        String primaryClassId = this.recipients.primaryClassId(mode, request == null ? null : request.classId(), recipients);
        persistRecipients(tenantId, assignmentId, primaryClassId, mode, recipients);

        assignment.setRecipientMode(mode);
        assignment.setAcademicClassId(primaryClassId);
        assignment.setStudentIds(recipients.stream().map(Student::getId).toList());
        assignment.setRecipientCount(recipients.size());
        assignmentRepository.save(assignment);
        caches.evictAfterCommit(tenantId, LmsReadCacheInvalidator.ASSIGNMENT_READ_CACHES);
        return describe(assignment);
    }

    public void updateAssignment(UserAccessContext access, String assignmentId, UpdateAssignmentRequest request) {
        String tenantId = AuthenticatedTenant.require(access.tenantId());
        Assignment assignment = requireAssignment(tenantId, assignmentId);
        policy.ensureManagedScope(assignment, access);
        if (request == null) {
            return;
        }
        if (request.title() != null) {
            assignment.setTitle(blankToNull(request.title()));
        }
        if (request.startAt() != null) {
            assignment.setStartAt(request.startAt());
        }
        if (request.dueAt() != null) {
            assignment.setDueAt(request.dueAt());
        }
        if (request.attemptLimit() != null) {
            assignment.setAttemptLimit(request.attemptLimit());
        }
        if (request.maxDurationMinutes() != null) {
            assignment.setMaxDurationMinutes(request.maxDurationMinutes());
        }
        if (request.offlineAllowed() != null) {
            assignment.setOfflineAllowed(request.offlineAllowed());
        }
        if (request.teacherNote() != null) {
            assignment.setTeacherNote(blankToNull(request.teacherNote()));
        }
        if (blankToNull(request.status()) != null) {
            assignment.setStatus(LmsStatePolicy.assignmentState(request.status()));
        }
        assignmentRepository.save(assignment);
        caches.evictAfterCommit(tenantId, LmsReadCacheInvalidator.ASSIGNMENT_READ_CACHES);
    }

    public void delete(UserAccessContext access, String assignmentId) {
        String tenantId = AuthenticatedTenant.require(access.tenantId());
        Assignment assignment = requireAssignment(tenantId, assignmentId);
        policy.ensureManagedScope(assignment, access);
        recipientRepository.deleteByAssignmentId(assignmentId);
        resourceRepository.deleteByAssignmentId(assignmentId);
        assignmentRepository.delete(assignment);
        caches.evictAfterCommit(tenantId, LmsReadCacheInvalidator.ASSIGNMENT_READ_CACHES);
    }

    /** Every recipient must sit in a class (or, without class, a school) the caller manages. */
    private void ensureRecipientScope(String tenantId, List<Student> recipients, UserAccessContext access) {
        Set<String> checked = new LinkedHashSet<>();
        for (Student student : recipients) {
            String classId = blankToNull(student.getAcademicClassId());
            if (classId == null) {
                if (!access.canAccessEducationUnit(student.getSchoolId())
                        || (blankToNull(student.getSchoolId()) == null && !access.managedEducationUnitIds().contains("*"))) {
                    throw new ForbiddenException("You can only assign students in managed centers/classes");
                }
            } else if (checked.add(classId)) {
                policy.ensureClassScope(tenantId, classId, access);
            }
        }
    }

    private List<AssignmentResource> persistResources(String tenantId, String assignmentId, List<String> quizIds) {
        List<AssignmentResource> resources = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<String> distinctQuizIds = new ArrayList<>(new LinkedHashSet<>(quizIds));
        Map<String, QuizVersion> activeVersions = quizVersionRepository
                .findByQuizIdInAndTenantIdAndIsActiveTrue(distinctQuizIds, tenantId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(QuizVersion::getQuizId, value -> value, (left, right) -> left));
        int order = 0;
        for (String quizId : distinctQuizIds) {
            QuizVersion active = activeVersions.get(quizId);
            if (active == null) {
                throw new BadRequestException("quiz " + quizId + " has no published version to assign");
            }
            AssignmentResource resource = new AssignmentResource();
            resource.setAssignmentId(assignmentId);
            resource.setQuizId(quizId);
            resource.setTenantId(tenantId);
            resource.setQuizVersionId(active.getId());
            resource.setQuizVersionLabel(active.getVersionLabel());
            resource.setOrderIndex(order++);
            resource.setCreatedAt(now);
            resources.add(resource);
        }
        return resources.isEmpty() ? List.of() : resourceRepository.saveAll(resources);
    }

    private void persistRecipients(String tenantId, String assignmentId, String classId, String mode, List<Student> recipients) {
        List<AssignmentRecipient> rows = new ArrayList<>(recipients.size());
        for (Student student : recipients) {
            AssignmentRecipient recipient = new AssignmentRecipient();
            recipient.setAssignmentId(assignmentId);
            recipient.setTenantId(tenantId);
            recipient.setStudentId(student.getId());
            recipient.setStudentUserId(student.getAuthUserId());
            recipient.setClassId(student.getAcademicClassId() == null ? classId : student.getAcademicClassId());
            recipient.setSourceMode(mode);
            rows.add(recipient);
        }
        if (!rows.isEmpty()) recipientRepository.saveAll(rows);
    }

    private CreateAssignmentResponse describe(Assignment assignment) {
        List<AssignmentResourceResponse> resources = toResourceResponses(
                resourceRepository.findByAssignmentIdOrderByOrderIndexAsc(assignment.getId()));
        int recipientCount = assignment.getRecipientCount() == null
                ? (int) recipientRepository.countByAssignmentId(assignment.getId())
                : assignment.getRecipientCount();
        return new CreateAssignmentResponse(assignment.getId(), recipientCount, resources);
    }

    private List<AssignmentResourceResponse> toResourceResponses(List<AssignmentResource> resources) {
        return resources.stream()
                .map(resource -> new AssignmentResourceResponse(
                        resource.getQuizId(), resource.getQuizVersionId(), resource.getQuizVersionLabel()))
                .toList();
    }

    private Assignment requireAssignment(String tenantId, String assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("assignment not found"));
        if (!tenantId.equals(assignment.getTenantId())) {
            throw new ResourceNotFoundException("assignment not found");
        }
        return assignment;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
