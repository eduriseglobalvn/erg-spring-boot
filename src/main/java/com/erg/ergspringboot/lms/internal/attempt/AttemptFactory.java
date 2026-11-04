package com.erg.ergspringboot.lms.internal.attempt;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StartAttemptRequest;
import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.model.quiz.version.QuizPackage;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            Quiz quiz, Optional<QuizPackage> activePackage, OffsetDateTime now, String key) {
        Attempt attempt = new Attempt();
        attempt.setId(IdGenerator.newId());
        attempt.setTenantId(tenant);
        attempt.setAssignmentId(request.assignmentId());
        attempt.setQuizId(request.quizId());
        attempt.setStudentId(student);
        QuizPackage pkg = activePackage.orElse(null);
        String packageId = pkg == null ? request.packageId() : pkg.getId();
        String packageHash = pkg == null ? request.packageHash() : canonicalPackageHash(pkg, request.packageHash());
        String versionId = pkg == null ? (quiz == null ? null : quiz.getCurrentVersionId()) : pkg.getQuizVersionId();
        String gradingMode = pkg == null ? null : pkg.getGradingMode();
        int packageQuestionCount = pkg == null ? 0 : totalQuestions(pkg.getPackageJson());
        int questionCount = Math.max(packageQuestionCount, questionCount(quiz));
        attempt.setPackageId(packageId);
        attempt.setPackageHash(packageHash);
        attempt.setQuizVersionId(versionId);
        attempt.setQuizVersion(pkg == null ? null : pkg.getQuizVersion());
        attempt.setGradingMode(gradingMode);
        attempt.setStartIdempotencyKey(key);
        attempt.setMode(mode(quiz == null ? null : quiz.getKind()));
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
        attempt.setPolicySnapshot(policySnapshot(request, assignment, pkg, versionId, packageId, packageHash,
                gradingMode, questionCount, now));
        return attempt;
    }

    String canonicalPackageHash(QuizPackage pkg, String fallback) {
        String hash = blank(pkg.getContentHash());
        return hash == null ? blank(fallback) : hash;
    }

    private Map<String, Object> policySnapshot(StartAttemptRequest request, Assignment assignment, QuizPackage pkg,
            String versionId, String packageId, String packageHash, String gradingMode, int questionCount,
            OffsetDateTime startedAt) {
        OffsetDateTime deadline = deadlines.deadline(assignment, startedAt);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("assignmentId", request.assignmentId());
        snapshot.put("quizId", request.quizId());
        snapshot.put("quizVersionId", versionId);
        snapshot.put("quizVersion", pkg == null ? null : pkg.getQuizVersion());
        snapshot.put("packageId", packageId);
        snapshot.put("packageHash", packageHash);
        snapshot.put("teacherId", assignment.getAssignedBy());
        snapshot.put("questionCount", questionCount);
        snapshot.put("durationMinutes", assignment.getMaxDurationMinutes());
        snapshot.put("deadlineAt", deadline == null ? null : deadline.toString());
        snapshot.put("submitGraceSeconds", deadlines.submitGrace().toSeconds());
        snapshot.put("attemptLimit", assignment.getAttemptLimit());
        snapshot.put("gradingMode", gradingMode);
        snapshot.put("offlineAllowed", assignment.isOfflineAllowed()
                && "server-authoritative".equalsIgnoreCase(gradingMode));
        snapshot.put("startedAt", startedAt.toString());
        snapshot.put("serverTimeAtStart", startedAt.toString());
        snapshot.put("sourceAssignmentUpdatedAt", assignment.getUpdatedAt() == null ? null : assignment.getUpdatedAt().toString());
        return snapshot;
    }

    private int questionCount(Quiz quiz) {
        return quiz == null || quiz.getQuestionIds() == null ? 0 : quiz.getQuestionIds().size();
    }
    private int totalQuestions(Map<String,Object> json) { Object q = json == null ? null : json.get("quiz"); if (!(q instanceof Map<?,?> qm)) return 0; Object sections = ((Map<?,?>) qm).get("sections"); if (!(sections instanceof List<?> list)) return 0; return list.stream().filter(Map.class::isInstance).mapToInt(x -> { Object qs = ((Map<?,?>) x).get("questions"); return qs instanceof List<?> l ? l.size() : 0; }).sum(); }
    private String mode(String kind) { return "test".equalsIgnoreCase(kind) || "testing".equalsIgnoreCase(kind) ? "testing" : "training"; }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
