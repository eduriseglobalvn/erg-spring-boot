package com.erg.ergspringboot.lms.internal.attempt;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.ImportPortableResultRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.ImportedAttemptResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizPackage;
import com.erg.ergspringboot.lms.LmsAssignmentAccessPolicy;
import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.lms.internal.core.LmsAttemptService;
import com.erg.ergspringboot.lms.internal.quiz.runtime.QuizRuntimePackageService;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports an offline {@code .ergresult}: verifies the checksum and the published package, resolves the learner in
 * the caller's tenant (never from the body) and creates one finished attempt that the server grades. Client times
 * are stored as metadata only. The same file always maps to the same attempt, via X-Idempotency-Key or the checksum.
 */
@Service
public class LmsPortableResultImportUseCase {
    private static final int FORMAT_VERSION = 1;
    private final LmsAssignmentAccessPolicy policy;
    private final QuizRuntimePackageService packages;
    private final LmsAttemptService attempts;
    private final LmsAttemptUseCase attemptUseCase;

    public LmsPortableResultImportUseCase(LmsAssignmentAccessPolicy policy, QuizRuntimePackageService packages,
            LmsAttemptService attempts, LmsAttemptUseCase attemptUseCase) {
        this.policy = policy; this.packages = packages; this.attempts = attempts; this.attemptUseCase = attemptUseCase;
    }

    @Transactional("postgresTransactionManager")
    public ImportedAttemptResponse importPortableResult(ImportPortableResultRequest request, String assignmentId,
            String idempotencyKey, UserAccessContext access) {
        String tenant = AuthenticatedTenant.require(access.tenantId());
        Map<String, Object> body = request.body();
        PortableResultChecksum.verify(body, request.checksum());
        if (!"erg.portable-result".equals(body.get("format")) || !Integer.valueOf(FORMAT_VERSION).equals(body.get("schemaVersion"))) {
            throw new BadRequestException("unsupported result file");
        }
        Map<String, Object> pkgRef = object(body.get("package"));
        Map<String, Object> identity = object(body.get("identity"));
        String quizId = required(pkgRef.get("quizId"));
        Student student = policy.importedLearner(tenant, required(identity.get("classCode")), required(identity.get("studentCode")));
        boolean self = student.getAuthUserId() != null && student.getAuthUserId().equals(access.userId());
        if (!self && !policy.isReviewer()) throw new ForbiddenException("You can only import your own results");

        QuizPackage pkg = packages.packageEntityById(tenant, required(pkgRef.get("id")))
                .orElseThrow(() -> new BadRequestException("result package is not published"));
        if (pkg.getRevokedAt() != null) throw new ConflictException("result package was revoked");
        if (!quizId.equals(pkg.getQuizId()) || blank(pkg.getContentHash()) == null
                || !pkg.getContentHash().equals(required(pkgRef.get("contentHash")))) {
            throw new BadRequestException("package contract mismatch");
        }
        Assignment assignment = matchingAssignment(tenant, assignmentId, quizId, pkg, student);
        if (!self) policy.ensureReviewScope(assignment, student.getId(), access);

        String key = blank(idempotencyKey) != null ? idempotencyKey.trim() : request.checksum().trim().toLowerCase(Locale.ROOT);
        Optional<Attempt> existing = attempts.findByStartIdempotencyKey(tenant, assignment.getId(), quizId, student.getId(), key);
        if (existing.isPresent()) return response(existing.get(), true);
        Integer limit = assignment.getAttemptLimit();
        if (limit != null && limit > 0 && attempts.countCompletedForLimit(tenant, assignment.getId(), quizId, student.getId()) >= limit) {
            throw new ConflictException("attempt limit reached");
        }

        Map<String, Object> clientMeta = new LinkedHashMap<>();
        clientMeta.put("source", "portable-result");
        clientMeta.put("importedBy", access.userId());
        clientMeta.put("checksum", request.checksum().trim());
        for (String field : List.of("attemptId", "startedAt", "submittedAt", "exportedAt")) {
            clientMeta.put("client" + Character.toUpperCase(field.charAt(0)) + field.substring(1), body.get(field));
        }
        try {
            return response(attemptUseCase.saveImported(tenant, student, pkg, assignment, key, clientMeta, object(body.get("answers"))), false);
        } catch (DataIntegrityViolationException raced) {
            // A concurrent import of the same file won the unique start key; a retry returns that attempt.
            throw new ConflictException("this result is already being imported; retry");
        }
    }

    private Assignment matchingAssignment(String tenant, String assignmentId, String quizId, QuizPackage pkg, Student student) {
        List<Assignment> candidates = blank(assignmentId) != null
                ? List.of(policy.assignment(tenant, assignmentId.trim()))
                : policy.visibleAssignmentsFor(tenant, quizId, student);
        List<Assignment> matches = candidates.stream()
                .filter(a -> LmsStatePolicy.assignmentIsVisible(a.getStatus())
                        && policy.isRecipient(a, student, student.getAuthUserId())
                        && policy.assignedQuiz(a, quizId).filter(q -> q.quizVersionId() == null
                                || q.quizVersionId().equals(pkg.getQuizVersionId())).isPresent())
                .toList();
        if (matches.isEmpty()) throw new BadRequestException("no open assignment of this quiz for the student matches the result");
        if (matches.size() > 1) throw new ConflictException("several assignments match; pass assignmentId");
        return matches.get(0);
    }

    private ImportedAttemptResponse response(Attempt a, boolean duplicate) {
        return new ImportedAttemptResponse(a.getId(), a.getAssignmentId(), a.getQuizId(), a.getStudentId(), a.getStatus(),
                num(a.getScore()), num(a.getMaxScore()), num(a.getPercent()), Boolean.TRUE.equals(a.getPassed()), a.isLate(), duplicate);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new BadRequestException("result file is missing required fields");
    }

    private static String required(Object value) {
        String text = value instanceof String s ? blank(s) : null;
        if (text == null) throw new BadRequestException("result file is missing required fields");
        return text;
    }

    private static double num(Double value) { return value == null ? 0 : value; }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
