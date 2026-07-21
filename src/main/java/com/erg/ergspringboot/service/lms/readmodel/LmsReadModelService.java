package com.erg.ergspringboot.service.lms.readmodel;

import com.erg.ergspringboot.common.response.PageResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.AssignmentAttemptScoreResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.AssignmentAttemptSummaryResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.AssignmentDetailResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.AssignmentProgressSummaryResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.AssignmentProgressWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.AssignmentResourceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.ClassOptionResponse;
import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.AssignmentSummaryItemResponse;
import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.ClassInfoResponse;
import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.ClassRiskSummaryResponse;
import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.ClassScoreSummaryResponse;
import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.ClassWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.StudentRosterItemResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.HomeworkAssignmentCardResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.MissingLateStudentResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.OverdueStudentResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.PendingReviewResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.RecipientProgressResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.StudentGroupResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.StudentGroupWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.TeacherHomeworkSummaryResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.TeacherHomeworkWorkspaceResponse;
import com.erg.ergspringboot.model.lms.StudentGroup;
import com.erg.ergspringboot.platform.concurrency.ErgReadTaskExecutor;
import com.erg.ergspringboot.repository.lms.AssignmentRepository;
import com.erg.ergspringboot.repository.lms.AssignmentResourceRepository;
import com.erg.ergspringboot.repository.lms.AttemptRepository;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.StudentGroupMemberRepository;
import com.erg.ergspringboot.repository.lms.StudentGroupRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.lms.projection.AssignmentCardProjection;
import com.erg.ergspringboot.repository.lms.projection.AssignmentDetailProjection;
import com.erg.ergspringboot.repository.lms.projection.AssignmentProgressProjection;
import com.erg.ergspringboot.repository.lms.projection.AssignmentResourceProjection;
import com.erg.ergspringboot.repository.lms.projection.AttemptScoreProjection;
import com.erg.ergspringboot.repository.lms.projection.AttemptSummaryProjection;
import com.erg.ergspringboot.repository.lms.projection.ClassOptionProjection;
import com.erg.ergspringboot.repository.lms.projection.ClassRiskSummaryProjection;
import com.erg.ergspringboot.repository.lms.projection.ClassScoreSummaryProjection;
import com.erg.ergspringboot.repository.lms.projection.ClassWorkspaceProjection;
import com.erg.ergspringboot.repository.lms.projection.MissingLateStudentProjection;
import com.erg.ergspringboot.repository.lms.projection.OverdueStudentProjection;
import com.erg.ergspringboot.repository.lms.projection.PendingReviewProjection;
import com.erg.ergspringboot.repository.lms.projection.RecipientProgressProjection;
import com.erg.ergspringboot.repository.lms.projection.StudentRosterProjection;
import com.erg.ergspringboot.repository.lms.projection.TeacherHomeworkSummaryProjection;
import java.util.Collections;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erg.ergspringboot.lms.internal.readmodel.LmsStudentGroupReadUseCase;
import com.erg.ergspringboot.lms.internal.readmodel.LmsAssignmentProgressQueryUseCase;

@Service
@Transactional(readOnly = true)
public class LmsReadModelService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentResourceRepository assignmentResourceRepository;
    private final AttemptRepository attemptRepository;
    private final ClassroomRepository classroomRepository;
    private final StudentRepository studentRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final StudentGroupMemberRepository studentGroupMemberRepository;
    private final ErgReadTaskExecutor readTaskExecutor;
    private final LmsStudentGroupReadUseCase studentGroupRead;
    private final LmsAssignmentProgressQueryUseCase assignmentProgressRead;

    public LmsReadModelService(
            AssignmentRepository assignmentRepository,
            AssignmentResourceRepository assignmentResourceRepository,
            AttemptRepository attemptRepository,
            ClassroomRepository classroomRepository,
            StudentRepository studentRepository,
            StudentGroupRepository studentGroupRepository,
            StudentGroupMemberRepository studentGroupMemberRepository,
            ErgReadTaskExecutor readTaskExecutor
    ) {
        this.assignmentRepository = assignmentRepository;
        this.assignmentResourceRepository = assignmentResourceRepository;
        this.attemptRepository = attemptRepository;
        this.classroomRepository = classroomRepository;
        this.studentRepository = studentRepository;
        this.studentGroupRepository = studentGroupRepository;
        this.studentGroupMemberRepository = studentGroupMemberRepository;
        this.readTaskExecutor = readTaskExecutor;
        this.studentGroupRead = new LmsStudentGroupReadUseCase(
                studentGroupRepository, studentGroupMemberRepository);
        this.assignmentProgressRead = new LmsAssignmentProgressQueryUseCase(attemptRepository);
    }

    public AssignmentProgressProjection assignmentProgress(String tenantId, String assignmentId) {
        return assignmentProgressRead.summary(tenantId, assignmentId);
    }

    @Cacheable(
            cacheNames = "lmsStudentGroups",
            key = "{#tenantId, #classId, #allowedClassIds, #ownerUserId}",
            unless = "#result == null"
    )
    public StudentGroupWorkspaceResponse studentGroups(
            String tenantId,
            String classId,
            Collection<String> allowedClassIds,
            String ownerUserId
    ) {
        return studentGroupRead.query(tenantId, classId, allowedClassIds, ownerUserId);
        /* String normalizedTenantId = requireText(tenantId, "tenantId");
        String normalizedClassId = blankToNull(classId);
        Set<String> normalizedAllowedClassIds = normalizeIds(allowedClassIds);
        String normalizedOwnerUserId = blankToNull(ownerUserId);

        PageRequest groupLimit = PageRequest.of(0, 201);
        List<StudentGroup> groups;
        if (normalizedClassId != null) {
            groups = studentGroupRepository.findByTenantIdAndClassIdOrderByCreatedAtDesc(
                    normalizedTenantId, normalizedClassId, groupLimit);
        } else if (normalizedAllowedClassIds.contains("*")) {
            groups = studentGroupRepository.findByTenantIdOrderByCreatedAtDesc(normalizedTenantId, groupLimit);
        } else if (normalizedOwnerUserId != null && !normalizedAllowedClassIds.isEmpty()) {
            groups = studentGroupRepository.findByTenantIdAndOwnerUserIdOrClassIdInOrderByCreatedAtDesc(
                    normalizedTenantId, normalizedOwnerUserId, normalizedAllowedClassIds, groupLimit);
        } else if (normalizedOwnerUserId != null) {
            groups = studentGroupRepository.findByTenantIdAndOwnerUserIdOrderByCreatedAtDesc(
                    normalizedTenantId, normalizedOwnerUserId, groupLimit);
        } else if (!normalizedAllowedClassIds.isEmpty()) {
            groups = studentGroupRepository.findByTenantIdAndClassIdInOrderByCreatedAtDesc(
                    normalizedTenantId, normalizedAllowedClassIds, groupLimit);
        } else {
            groups = List.of();
        }
        if (groups == null) {
            // Compatibility fallback for lightweight unit-test doubles and older repository
            // adapters. The PostgreSQL implementation always returns a filtered list.
            groups = studentGroupRepository.findByTenantIdOrderByCreatedAtDesc(normalizedTenantId, groupLimit);
        }
        groups = groups.stream()
                .filter(group -> matchesGroupScope(group, normalizedClassId, normalizedAllowedClassIds, normalizedOwnerUserId))
                .limit(200)
                .toList();
        List<String> groupIds = groups.stream().map(StudentGroup::getId).toList();
        Map<String, List<String>> studentIdsByGroupId = groupIds.isEmpty()
                ? Collections.emptyMap()
                : studentGroupMemberRepository.findByTenantIdAndGroupIdIn(
                        normalizedTenantId, groupIds, PageRequest.of(0, 10_000)).stream()
                        .collect(Collectors.groupingBy(
                                member -> member.getGroupId(),
                                Collectors.mapping(member -> member.getStudentId(), Collectors.toList())
                        ));

        return new StudentGroupWorkspaceResponse(
                normalizedTenantId,
                groups.stream()
                        .map(group -> toStudentGroup(group, studentIdsByGroupId.getOrDefault(group.getId(), List.of())))
                        .toList()
        ); */
    }

    @Cacheable(
            cacheNames = "lmsAssignmentProgress",
            key = "{#tenantId, #assignmentId, #attemptStatus, #page, #size}",
            unless = "#result == null"
    )
    public AssignmentProgressWorkspaceResponse assignmentProgressWorkspace(
            String tenantId,
            String assignmentId,
            String attemptStatus,
            Integer page,
            Integer size
    ) {
        String normalizedTenantId = requireText(tenantId, "tenantId");
        String normalizedAssignmentId = requireText(assignmentId, "assignmentId");
        AssignmentDetailProjection assignment = assignmentRepository
                .findAssignmentDetail(normalizedTenantId, normalizedAssignmentId)
                .orElseThrow(() -> new NoSuchElementException("Assignment progress workspace not found."));

        // Keep all repository reads on the transaction-bound thread. Offloading JDBC work loses
        // the EntityManager transaction and its transaction-local tenant/RLS GUCs.
        String normAttemptStatus = normalizeStatus(attemptStatus);
        Pageable attemptPage = attemptPageable(page, size);
        Pageable recPage = recipientPageable();

        AssignmentProgressProjection summary =
                attemptRepository.summarizeAssignmentProgress(normalizedTenantId, normalizedAssignmentId);
        List<AssignmentResourceProjection> resources =
                assignmentResourceRepository.findResourceProjections(normalizedTenantId, normalizedAssignmentId);
        Page<AttemptSummaryProjection> attempts =
                attemptRepository.findAssignmentAttemptSummaries(normalizedTenantId, normalizedAssignmentId, normAttemptStatus, attemptPage);
        Page<RecipientProgressProjection> recipients =
                assignmentRepository.findAssignmentRecipients(normalizedTenantId, normalizedAssignmentId, recPage);
        Page<MissingLateStudentProjection> missingLateStudents =
                assignmentRepository.findAssignmentMissingLateStudents(normalizedTenantId, normalizedAssignmentId, recPage);

        // These depend on recipient IDs, so must run after recipients are fetched
        List<String> recipientIds = recipients.getContent().stream()
                .map(RecipientProgressProjection::studentId)
                .toList();
        Map<String, AttemptScoreProjection> latestAttempts =
                findLatestAttemptScores(normalizedTenantId, normalizedAssignmentId, recipientIds);
        Map<String, AttemptScoreProjection> bestAttempts =
                findBestAttemptScores(normalizedTenantId, normalizedAssignmentId, recipientIds);

        return new AssignmentProgressWorkspaceResponse(
                normalizedTenantId,
                toAssignmentDetail(assignment, resources),
                toAssignmentProgressSummary(summary),
                toAttemptSummaryPage(attempts),
                toRecipientProgressPage(
                        recipients,
                        latestAttempts,
                        bestAttempts,
                        missingLateStudentsByStudentId(missingLateStudents)
                ),
                toMissingLateStudentPage(missingLateStudents)
        );
    }

    public Page<ClassWorkspaceProjection> schoolClassWorkspaces(
            String tenantId,
            String schoolId,
            String status,
            Pageable pageable
    ) {
        return classroomRepository.findSchoolClassWorkspaces(tenantId, schoolId, normalizeStatus(status), pageable);
    }

    public ClassWorkspaceResponse classWorkspace(
            String tenantId,
            String classId,
            String schoolId,
            String studentStatus,
            Integer page,
            Integer size,
            String assignmentStatus,
            Integer assignmentPage,
            Integer assignmentSize
    ) {
        return classWorkspace(
                tenantId,
                classId,
                schoolId,
                studentStatus,
                page,
                size,
                assignmentStatus,
                assignmentPage,
                assignmentSize,
                false,
                false
        );
    }

    public ClassWorkspaceResponse classWorkspace(
            String tenantId,
            String classId,
            String schoolId,
            String studentStatus,
            Integer page,
            Integer size,
            String assignmentStatus,
            Integer assignmentPage,
            Integer assignmentSize,
            boolean includeScoreSummary
    ) {
        return classWorkspace(
                tenantId,
                classId,
                schoolId,
                studentStatus,
                page,
                size,
                assignmentStatus,
                assignmentPage,
                assignmentSize,
                includeScoreSummary,
                false
        );
    }

    @Cacheable(
            cacheNames = "lmsClassWorkspace",
            key = "{#tenantId, #classId, #schoolId, #studentStatus, #page, #size, #assignmentStatus, #assignmentPage, #assignmentSize, #includeScoreSummary, #includeRiskSummary}",
            unless = "#result == null"
    )
    public ClassWorkspaceResponse classWorkspace(
            String tenantId,
            String classId,
            String schoolId,
            String studentStatus,
            Integer page,
            Integer size,
            String assignmentStatus,
            Integer assignmentPage,
            Integer assignmentSize,
            boolean includeScoreSummary,
            boolean includeRiskSummary
    ) {
        String normalizedTenantId = requireText(tenantId, "tenantId");
        String normalizedClassId = requireText(classId, "classId");
        String normalizedSchoolId = blankToNull(schoolId);
        ClassWorkspaceProjection classInfo = classroomRepository
                .findClassWorkspace(normalizedTenantId, normalizedClassId, normalizedSchoolId)
                .orElseThrow(() -> new NoSuchElementException("Class workspace not found."));

        // Preserve the transaction snapshot and tenant/RLS GUCs by keeping JDBC on this thread.
        String normStudentStatus = normalizeStatus(studentStatus);
        String normAssignmentStatus = normalizeStatus(assignmentStatus);
        Pageable studentPage = studentPageable(page, size);
        Pageable assignmentPageObj = assignmentPageable(assignmentPage, assignmentSize);

        Page<StudentRosterProjection> students =
                studentRepository.findClassRoster(normalizedTenantId, normalizedClassId, normStudentStatus, studentPage);
        Page<AssignmentCardProjection> assignments =
                assignmentRepository.findClassAssignmentCards(normalizedTenantId, normalizedClassId, normAssignmentStatus, assignmentPageObj);
        ClassScoreSummaryResponse scoreSummary = includeScoreSummary
                ? toClassScoreSummary(attemptRepository.summarizeClassWorkspaceScores(normalizedTenantId, normalizedClassId))
                : null;
        ClassRiskSummaryResponse riskSummary = includeRiskSummary
                ? toClassRiskSummary(studentRepository.summarizeClassWorkspaceRisks(normalizedTenantId, normalizedClassId, 50.0D))
                : null;

        return new ClassWorkspaceResponse(
                normalizedTenantId,
                toClassInfo(classInfo),
                scoreSummary,
                riskSummary,
                toStudentPage(students),
                toAssignmentPage(assignments, normalizedTenantId)
        );
    }

    @Cacheable(
            cacheNames = "lmsHomeworkWorkspace",
            key = "{#tenantId, #teacherId, #status, #page, #size}",
            unless = "#result == null"
    )
    public TeacherHomeworkWorkspaceResponse teacherHomeworkWorkspace(
            String tenantId,
            String teacherId,
            String status,
            Integer page,
            Integer size
    ) {
        String normalizedTenantId = requireText(tenantId, "tenantId");
        String normalizedTeacherId = requireText(teacherId, "teacherId");
        String normStatus = normalizeStatus(status);
        Pageable hwPage = assignmentPageable(page, size);

        // Preserve the transaction snapshot and tenant/RLS GUCs by keeping JDBC on this thread.
        Page<AssignmentCardProjection> assignments =
                assignmentRepository.findTeacherAssignmentCards(normalizedTenantId, normalizedTeacherId, normStatus, hwPage);
        TeacherHomeworkSummaryProjection summary =
                assignmentRepository.summarizeTeacherHomeworkWorkspace(normalizedTenantId, normalizedTeacherId);
        List<ClassOptionProjection> classOptions =
                classroomRepository.findTeacherClassOptions(normalizedTenantId, normalizedTeacherId);
        Page<PendingReviewProjection> pendingReviews =
                attemptRepository.findTeacherPendingReviews(normalizedTenantId, normalizedTeacherId, hwPage);
        Page<OverdueStudentProjection> overdueStudents =
                studentRepository.findTeacherOverdueStudents(normalizedTenantId, normalizedTeacherId, hwPage);

        return new TeacherHomeworkWorkspaceResponse(
                normalizedTenantId,
                normalizedTeacherId,
                toTeacherHomeworkSummary(summary),
                toClassOptions(classOptions),
                toHomeworkAssignmentPage(assignments, normalizedTenantId),
                toPendingReviewPage(pendingReviews),
                toOverdueStudentPage(overdueStudents)
        );
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? null : status.trim().toLowerCase();
    }

    private Pageable studentPageable(Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 50);
        return PageRequest.of(safePage, safeSize, Sort.by("fullName").ascending().and(Sort.by("studentCode").ascending()));
    }

    private Pageable assignmentPageable(Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 50);
        return PageRequest.of(safePage, safeSize);
    }

    private Pageable attemptPageable(Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 50);
        return PageRequest.of(safePage, safeSize, Sort.by("updatedAt").descending());
    }

    private Pageable recipientPageable() {
        return PageRequest.of(0, 50);
    }

    private ClassInfoResponse toClassInfo(ClassWorkspaceProjection projection) {
        return new ClassInfoResponse(
                projection.id(),
                projection.schoolId(),
                projection.name(),
                projection.grade(),
                projection.academicYear(),
                projection.status(),
                projection.homeroomTeacherId(),
                projection.studentCount() == null ? 0L : projection.studentCount(),
                projection.assignmentCount() == null ? 0L : projection.assignmentCount(),
                projection.updatedAt()
        );
    }

    private PageResponse<StudentRosterItemResponse> toStudentPage(Page<StudentRosterProjection> students) {
        List<StudentRosterItemResponse> items = students.getContent().stream()
                .map(this::toStudent)
                .toList();
        return new PageResponse<>(
                items,
                students.getNumber(),
                students.getSize(),
                students.getTotalElements(),
                students.getTotalPages(),
                students.hasNext(),
                students.hasPrevious()
        );
    }

    private PageResponse<AssignmentSummaryItemResponse> toAssignmentPage(
            Page<AssignmentCardProjection> assignments,
            String tenantId
    ) {
        Map<String, List<AssignmentResourceProjection>> resourcesByAssignmentId = resourcesByAssignmentId(
                tenantId,
                assignments.getContent()
        );
        List<AssignmentSummaryItemResponse> items = assignments.getContent().stream()
                .map(assignment -> toAssignmentSummary(
                        assignment,
                        resourcesByAssignmentId.getOrDefault(assignment.id(), List.of())
                ))
                .toList();
        return new PageResponse<>(
                items,
                assignments.getNumber(),
                assignments.getSize(),
                assignments.getTotalElements(),
                assignments.getTotalPages(),
                assignments.hasNext(),
                assignments.hasPrevious()
        );
    }

    private PageResponse<HomeworkAssignmentCardResponse> toHomeworkAssignmentPage(
            Page<AssignmentCardProjection> assignments,
            String tenantId
    ) {
        Map<String, List<AssignmentResourceProjection>> resourcesByAssignmentId = resourcesByAssignmentId(
                tenantId,
                assignments.getContent()
        );
        List<HomeworkAssignmentCardResponse> items = assignments.getContent().stream()
                .map(assignment -> toHomeworkAssignment(
                        assignment,
                        resourcesByAssignmentId.getOrDefault(assignment.id(), List.of())
                ))
                .toList();
        return new PageResponse<>(
                items,
                assignments.getNumber(),
                assignments.getSize(),
                assignments.getTotalElements(),
                assignments.getTotalPages(),
                assignments.hasNext(),
                assignments.hasPrevious()
        );
    }

    private StudentRosterItemResponse toStudent(StudentRosterProjection projection) {
        return new StudentRosterItemResponse(
                projection.id(),
                projection.schoolId(),
                projection.academicClassId(),
                projection.studentCode(),
                projection.fullName(),
                projection.username(),
                projection.authUserId(),
                projection.email(),
                projection.status(),
                projection.updatedAt()
        );
    }

    private AssignmentSummaryItemResponse toAssignmentSummary(
            AssignmentCardProjection projection,
            List<AssignmentResourceProjection> resources
    ) {
        return new AssignmentSummaryItemResponse(
                projection.id(),
                projection.academicClassId(),
                projection.quizId(),
                projection.subjectId(),
                projection.dueAt(),
                projection.status(),
                projection.assignedBy(),
                projection.recipientMode(),
                toAssignmentResources(resources),
                projection.createdAt(),
                projection.updatedAt()
        );
    }

    private HomeworkAssignmentCardResponse toHomeworkAssignment(
            AssignmentCardProjection projection,
            List<AssignmentResourceProjection> resources
    ) {
        return new HomeworkAssignmentCardResponse(
                projection.id(),
                projection.academicClassId(),
                projection.quizId(),
                projection.subjectId(),
                projection.dueAt(),
                projection.status(),
                projection.assignedBy(),
                projection.recipientMode(),
                toAssignmentResources(resources),
                projection.createdAt(),
                projection.updatedAt()
        );
    }

    private List<ClassOptionResponse> toClassOptions(List<ClassOptionProjection> projections) {
        return projections.stream()
                .map(this::toClassOption)
                .toList();
    }

    private ClassOptionResponse toClassOption(ClassOptionProjection projection) {
        return new ClassOptionResponse(
                projection.id(),
                projection.schoolId(),
                projection.name(),
                projection.grade(),
                projection.academicYear(),
                projection.status()
        );
    }

    private PageResponse<PendingReviewResponse> toPendingReviewPage(Page<PendingReviewProjection> pendingReviews) {
        List<PendingReviewResponse> items = pendingReviews.getContent().stream()
                .map(this::toPendingReview)
                .toList();
        return new PageResponse<>(
                items,
                pendingReviews.getNumber(),
                pendingReviews.getSize(),
                pendingReviews.getTotalElements(),
                pendingReviews.getTotalPages(),
                pendingReviews.hasNext(),
                pendingReviews.hasPrevious()
        );
    }

    private PendingReviewResponse toPendingReview(PendingReviewProjection projection) {
        return new PendingReviewResponse(
                projection.attemptId(),
                projection.assignmentId(),
                projection.academicClassId(),
                projection.quizId(),
                projection.subjectId(),
                projection.studentId(),
                projection.studentCode(),
                projection.fullName(),
                projection.status(),
                projection.score(),
                projection.maxScore(),
                clampPercent(roundPercent(projection.percent())),
                projection.submittedAt(),
                projection.updatedAt()
        );
    }

    private PageResponse<OverdueStudentResponse> toOverdueStudentPage(Page<OverdueStudentProjection> overdueStudents) {
        List<OverdueStudentResponse> items = overdueStudents.getContent().stream()
                .map(this::toOverdueStudent)
                .toList();
        return new PageResponse<>(
                items,
                overdueStudents.getNumber(),
                overdueStudents.getSize(),
                overdueStudents.getTotalElements(),
                overdueStudents.getTotalPages(),
                overdueStudents.hasNext(),
                overdueStudents.hasPrevious()
        );
    }

    private OverdueStudentResponse toOverdueStudent(OverdueStudentProjection projection) {
        return new OverdueStudentResponse(
                projection.assignmentId(),
                projection.academicClassId(),
                projection.quizId(),
                projection.subjectId(),
                projection.dueAt(),
                projection.studentId(),
                projection.studentCode(),
                projection.fullName(),
                projection.status(),
                projection.updatedAt()
        );
    }

    private AssignmentDetailResponse toAssignmentDetail(
            AssignmentDetailProjection projection,
            List<AssignmentResourceProjection> resources
    ) {
        return new AssignmentDetailResponse(
                projection.id(),
                projection.academicClassId(),
                projection.quizId(),
                projection.subjectId(),
                projection.dueAt(),
                projection.status(),
                projection.assignedBy(),
                projection.recipientMode(),
                toAssignmentResources(resources),
                projection.createdAt(),
                projection.updatedAt()
        );
    }

    private List<AssignmentResourceResponse> toAssignmentResources(List<AssignmentResourceProjection> resources) {
        return resources.stream()
                .sorted(Comparator.comparingInt(resource -> resource.orderIndex() == null ? 0 : resource.orderIndex()))
                .map(resource -> new AssignmentResourceResponse(
                        resource.quizId(),
                        resource.quizVersionId(),
                        resource.quizVersionLabel(),
                        resource.orderIndex() == null ? 0 : resource.orderIndex()
                ))
                .toList();
    }

    private Map<String, List<AssignmentResourceProjection>> resourcesByAssignmentId(
            String tenantId,
            List<AssignmentCardProjection> assignments
    ) {
        List<String> assignmentIds = assignments.stream()
                .map(AssignmentCardProjection::id)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (assignmentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return assignmentResourceRepository
                .findResourceProjectionsByAssignmentIds(tenantId, assignmentIds)
                .stream()
                .collect(Collectors.groupingBy(AssignmentResourceProjection::assignmentId));
    }

    private AssignmentProgressSummaryResponse toAssignmentProgressSummary(AssignmentProgressProjection projection) {
        if (projection == null) {
            return new AssignmentProgressSummaryResponse(0, 0, 0);
        }
        return new AssignmentProgressSummaryResponse(
                projection.submittedCount(),
                projection.inProgressCount(),
                projection.needsReviewCount()
        );
    }

    private PageResponse<AssignmentAttemptSummaryResponse> toAttemptSummaryPage(Page<AttemptSummaryProjection> attempts) {
        List<AssignmentAttemptSummaryResponse> items = attempts.getContent().stream()
                .map(this::toAttemptSummary)
                .toList();
        return new PageResponse<>(
                items,
                attempts.getNumber(),
                attempts.getSize(),
                attempts.getTotalElements(),
                attempts.getTotalPages(),
                attempts.hasNext(),
                attempts.hasPrevious()
        );
    }

    private AssignmentAttemptSummaryResponse toAttemptSummary(AttemptSummaryProjection projection) {
        return new AssignmentAttemptSummaryResponse(
                projection.id(),
                projection.assignmentId(),
                projection.quizId(),
                projection.studentId(),
                projection.status(),
                projection.score(),
                projection.maxScore(),
                clampPercent(roundPercent(projection.percent())),
                projection.passed(),
                projection.startedAt(),
                projection.submittedAt(),
                projection.updatedAt()
        );
    }

    private PageResponse<RecipientProgressResponse> toRecipientProgressPage(
            Page<RecipientProgressProjection> recipients,
            Map<String, AttemptScoreProjection> latestAttempts,
            Map<String, AttemptScoreProjection> bestAttempts,
            Map<String, MissingLateStudentProjection> missingLateStudents
    ) {
        List<RecipientProgressResponse> items = recipients.getContent().stream()
                .map(recipient -> toRecipientProgress(
                        recipient,
                        latestAttempts.get(recipient.studentId()),
                        bestAttempts.get(recipient.studentId()),
                        missingLateStudents.get(recipient.studentId())
                ))
                .toList();
        return new PageResponse<>(
                items,
                recipients.getNumber(),
                recipients.getSize(),
                recipients.getTotalElements(),
                recipients.getTotalPages(),
                recipients.hasNext(),
                recipients.hasPrevious()
        );
    }

    private RecipientProgressResponse toRecipientProgress(
            RecipientProgressProjection projection,
            AttemptScoreProjection latestAttempt,
            AttemptScoreProjection bestAttempt,
            MissingLateStudentProjection missingLateStudent
    ) {
        return new RecipientProgressResponse(
                projection.studentId(),
                projection.schoolId(),
                projection.academicClassId(),
                projection.studentCode(),
                projection.fullName(),
                projection.username(),
                projection.authUserId(),
                projection.email(),
                projection.status(),
                missingLateStudent != null && Boolean.TRUE.equals(missingLateStudent.missing()),
                missingLateStudent != null && Boolean.TRUE.equals(missingLateStudent.late()),
                toAttemptScore(latestAttempt),
                toAttemptScore(bestAttempt),
                projection.updatedAt()
        );
    }

    private PageResponse<MissingLateStudentResponse> toMissingLateStudentPage(Page<MissingLateStudentProjection> students) {
        List<MissingLateStudentResponse> items = students.getContent().stream()
                .map(this::toMissingLateStudent)
                .toList();
        return new PageResponse<>(
                items,
                students.getNumber(),
                students.getSize(),
                students.getTotalElements(),
                students.getTotalPages(),
                students.hasNext(),
                students.hasPrevious()
        );
    }

    private MissingLateStudentResponse toMissingLateStudent(MissingLateStudentProjection projection) {
        return new MissingLateStudentResponse(
                projection.studentId(),
                projection.schoolId(),
                projection.academicClassId(),
                projection.studentCode(),
                projection.fullName(),
                projection.username(),
                projection.authUserId(),
                projection.email(),
                projection.status(),
                projection.dueAt(),
                Boolean.TRUE.equals(projection.missing()),
                Boolean.TRUE.equals(projection.late()),
                projection.updatedAt()
        );
    }

    private Map<String, MissingLateStudentProjection> missingLateStudentsByStudentId(
            Page<MissingLateStudentProjection> students
    ) {
        return students.getContent().stream()
                .collect(Collectors.toMap(
                        MissingLateStudentProjection::studentId,
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    private AssignmentAttemptScoreResponse toAttemptScore(AttemptScoreProjection projection) {
        if (projection == null) {
            return null;
        }
        return new AssignmentAttemptScoreResponse(
                projection.id(),
                projection.assignmentId(),
                projection.quizId(),
                projection.studentId(),
                projection.status(),
                projection.score(),
                projection.maxScore(),
                clampPercent(roundPercent(projection.percent())),
                projection.passed(),
                projection.startedAt(),
                projection.submittedAt(),
                projection.updatedAt()
        );
    }

    private Map<String, AttemptScoreProjection> findLatestAttemptScores(
            String tenantId,
            String assignmentId,
            List<String> recipientIds
    ) {
        if (recipientIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return attemptRepository.findLatestAttemptScoresForRecipients(tenantId, assignmentId, recipientIds)
                .stream()
                .collect(Collectors.toMap(
                        AttemptScoreProjection::studentId,
                        Function.identity(),
                        this::latestAttempt
                ));
    }

    private Map<String, AttemptScoreProjection> findBestAttemptScores(
            String tenantId,
            String assignmentId,
            List<String> recipientIds
    ) {
        if (recipientIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return attemptRepository.findBestAttemptScoresForRecipients(tenantId, assignmentId, recipientIds)
                .stream()
                .collect(Collectors.toMap(
                        AttemptScoreProjection::studentId,
                        Function.identity(),
                        this::bestAttempt
                ));
    }

    private AttemptScoreProjection latestAttempt(AttemptScoreProjection left, AttemptScoreProjection right) {
        if (left.updatedAt() == null) {
            return right;
        }
        if (right.updatedAt() == null) {
            return left;
        }
        int updatedAtComparison = left.updatedAt().compareTo(right.updatedAt());
        if (updatedAtComparison == 0) {
            return left.id().compareTo(right.id()) >= 0 ? left : right;
        }
        return updatedAtComparison > 0 ? left : right;
    }

    private AttemptScoreProjection bestAttempt(AttemptScoreProjection left, AttemptScoreProjection right) {
        int percentComparison = Integer.compare(roundPercent(left.percent()), roundPercent(right.percent()));
        if (percentComparison == 0) {
            return latestAttempt(left, right);
        }
        return percentComparison > 0 ? left : right;
    }

    private TeacherHomeworkSummaryResponse toTeacherHomeworkSummary(TeacherHomeworkSummaryProjection projection) {
        if (projection == null) {
            return new TeacherHomeworkSummaryResponse(0, 0, 0);
        }
        return new TeacherHomeworkSummaryResponse(
                boundedInt(nullToZero(projection.activeAssignmentCount())),
                boundedInt(nullToZero(projection.overdueAssignmentCount())),
                boundedInt(nullToZero(projection.pendingReviewAssignmentCount()))
        );
    }

    private StudentGroupResponse toStudentGroup(StudentGroup group, List<String> studentIds) {
        List<String> orderedStudentIds = studentIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        int studentCount = group.getStudentCount() == null ? orderedStudentIds.size() : group.getStudentCount();
        return new StudentGroupResponse(
                group.getId(),
                group.getName(),
                group.getClassId(),
                group.getNote(),
                group.getColor(),
                group.getOwnerUserId(),
                orderedStudentIds,
                studentCount,
                group.getCreatedAt(),
                group.getUpdatedAt()
        );
    }

    private boolean matchesGroupScope(
            StudentGroup group,
            String requestedClassId,
            Set<String> allowedClassIds,
            String ownerUserId
    ) {
        String groupClassId = blankToNull(group.getClassId());
        if (requestedClassId != null && !requestedClassId.equals(groupClassId)) {
            return false;
        }
        if (allowedClassIds.contains("*")) {
            return true;
        }
        String groupOwnerUserId = blankToNull(group.getOwnerUserId());
        if (ownerUserId != null && ownerUserId.equals(groupOwnerUserId)) {
            return true;
        }
        return groupClassId != null && allowedClassIds.contains(groupClassId);
    }

    private Set<String> normalizeIds(Collection<String> ids) {
        if (ids == null) {
            return Set.of();
        }
        return ids.stream()
                .map(this::blankToNull)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private ClassScoreSummaryResponse toClassScoreSummary(ClassScoreSummaryProjection projection) {
        if (projection == null) {
            return new ClassScoreSummaryResponse(0, 0, 0, 0);
        }
        return new ClassScoreSummaryResponse(
                boundedInt(nullToZero(projection.completedAttemptCount())),
                boundedInt(nullToZero(projection.attemptedStudentCount())),
                clampPercent(roundPercent(projection.averagePercent())),
                clampPercent(roundPercent(projection.bestPercent()))
        );
    }

    private ClassRiskSummaryResponse toClassRiskSummary(ClassRiskSummaryProjection projection) {
        if (projection == null) {
            return new ClassRiskSummaryResponse(0, 0, 0);
        }
        return new ClassRiskSummaryResponse(
                boundedInt(nullToZero(projection.missingOverdueStudentCount())),
                boundedInt(nullToZero(projection.lowScoreStudentCount())),
                boundedInt(nullToZero(projection.inactiveStudentCount()))
        );
    }

    private int roundPercent(Double percent) {
        return percent == null ? 0 : (int) Math.round(percent);
    }

    private int clampPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private int boundedInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0, value);
    }

    private String requireText(String value, String fieldName) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
