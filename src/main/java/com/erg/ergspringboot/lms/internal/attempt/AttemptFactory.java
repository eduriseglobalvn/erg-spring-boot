package com.erg.ergspringboot.lms.internal.attempt;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StartAttemptRequest;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.lms.QuizStartInfo;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a new in-progress attempt pinned to the runtime package, including the immutable policy snapshot
 * (deadline, grace, limits, grading mode) that later submit/expiry decisions are evaluated against.
 */
final class AttemptFactory {
    private final AttemptDeadlinePolicy deadlines;

    AttemptFactory(AttemptDeadlinePolicy deadlines) {
        this.deadlines = deadlines;
    }

    Attempt newAttempt(String tenant, String student, StartAttemptRequest request, Assignment assignment,
            QuizStartInfo info, OffsetDateTime now, String key) {
        Attempt attempt = new Attempt();
        attempt.setId(IdGenerator.newId());
        attempt.setTenantId(tenant);
        attempt.setAssignmentId(request.assignmentId());
        attempt.setQuizId(request.quizId());
        attempt.setStudentId(student);
        boolean hasPackage = info.packageFound();
        String packageId = hasPackage ? info.packageId() : request.packageId();
        String packageHash = hasPackage ? canonicalPackageHash(info, request.packageHash()) : request.packageHash();
        String versionId = hasPackage ? info.quizVersionId() : info.quizCurrentVersionId();
        String gradingMode = hasPackage ? info.gradingMode() : null;
        int questionCount = Math.max(hasPackage ? info.packageQuestionCount() : 0, info.quizQuestionCount());
        attempt.setPackageId(packageId);
        attempt.setPackageHash(packageHash);
        attempt.setQuizVersionId(versionId);
        attempt.setQuizVersion(hasPackage ? info.quizVersion() : null);
        attempt.setGradingMode(gradingMode);
        attempt.setStartIdempotencyKey(key);
        attempt.setMode(mode(info.quizKind()));
        attempt.setStatus(LmsStatePolicy.initialAttemptState());
        attempt.setSubmittedCount(0);
        attempt.setTotalQuestions(questionCount);
        attempt.setLastActiveAt(now);
        attempt.setAnswers(new LinkedHashMap<>());
        attempt.setEvents(List.of());
        attempt.setClient(Map.of());
        attempt.setScore(0.0);
        attempt.setMaxScore(100.0);
        attempt.setPercent(0.0);
        attempt.setPassed(false);
        attempt.setStartedAt(now);
        attempt.setUpdatedAt(now);
        attempt.setPolicySnapshot(policySnapshot(request, assignment, hasPackage ? info.quizVersion() : null, versionId,
                packageId, packageHash, gradingMode, questionCount, now));
        return attempt;
    }

    String canonicalPackageHash(QuizStartInfo info, String fallback) {
        String hash = blank(info.contentHash());
        return hash == null ? blank(fallback) : hash;
    }

    void validatePackageContract(StartAttemptRequest request, QuizStartInfo info) {
        if (!info.packageFound()) {
            return; // Legacy quizzes without a published lineage use the request fields as a compatibility bridge.
        }
        String expectedHash = canonicalPackageHash(info, null);
        if (!Objects.equals(info.packageId(), request.packageId()) || !Objects.equals(expectedHash, request.packageHash())) {
            throw new BadRequestException("package contract mismatch");
        }
    }

    void ensureResumePackageMatches(Attempt attempt, StartAttemptRequest request) {
        if (attempt.getPackageId() != null && !Objects.equals(attempt.getPackageId(), request.packageId())) {
            throw new BadRequestException("attempt package contract mismatch");
        }
        if (attempt.getPackageHash() != null && !Objects.equals(attempt.getPackageHash(), request.packageHash())) {
            throw new BadRequestException("attempt package contract mismatch");
        }
    }

    private Map<String, Object> policySnapshot(StartAttemptRequest request, Assignment assignment, String quizVersion,
            String versionId, String packageId, String packageHash, String gradingMode, int questionCount,
            OffsetDateTime startedAt) {
        OffsetDateTime deadline = deadlines.deadline(assignment, startedAt);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("assignmentId", request.assignmentId());
        snapshot.put("quizId", request.quizId());
        snapshot.put("quizVersionId", versionId);
        snapshot.put("quizVersion", quizVersion);
        snapshot.put("packageId", packageId);
        snapshot.put("packageHash", packageHash);
        snapshot.put("teacherId", assignment.getAssignedBy());
        snapshot.put("questionCount", questionCount);
        snapshot.put("durationMinutes", assignment.getMaxDurationMinutes());
        snapshot.put("deadlineAt", deadline == null ? null : deadline.toString());
        // An int, like the value read back from JSON: a Long here never equals its own snapshot, so Hibernate
        // rewrote the new attempt (and bumped record_version) on every flush of the start transaction.
        snapshot.put("submitGraceSeconds", Math.toIntExact(deadlines.submitGrace().toSeconds()));
        snapshot.put("attemptLimit", assignment.getAttemptLimit());
        snapshot.put("gradingMode", gradingMode);
        snapshot.put("offlineAllowed", assignment.isOfflineAllowed()
                && "server-authoritative".equalsIgnoreCase(gradingMode));
        snapshot.put("startedAt", startedAt.toString());
        snapshot.put("serverTimeAtStart", startedAt.toString());
        snapshot.put("sourceAssignmentUpdatedAt", assignment.getUpdatedAt() == null ? null : assignment.getUpdatedAt().toString());
        return snapshot;
    }

    private String mode(String kind) { return "test".equalsIgnoreCase(kind) || "testing".equalsIgnoreCase(kind) ? "testing" : "training"; }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
