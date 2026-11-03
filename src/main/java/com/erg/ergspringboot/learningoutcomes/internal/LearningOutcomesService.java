package com.erg.ergspringboot.learningoutcomes.internal;

import com.erg.ergspringboot.learningoutcomes.LearningOutcomesPort;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.AddPathNodeRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.CreateCompetencyRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.CreateLearningPathRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.EvaluateCompletionRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.IssueCertificateRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.RecordAttainmentRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.RevokeCertificateRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.AttainmentResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.CertificateResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.CompletionResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.CompetencyResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.LearningPathResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.PathDetailResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.PathNodeResponse;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Tenant-scoped application service for learning outcomes. SQ-20: this is now a thin facade over one use-case
 * bean per job; each delegate owns its own {@code @Transactional} boundary, idempotency (via
 * {@link OutcomeReceiptStore}) and scope checks (via {@link OutcomeVisibilityPolicy}). Controllers only map
 * transport requests into these use cases.
 */
@Service
public class LearningOutcomesService implements LearningOutcomesPort {
    private final LearningPathCommands pathCommands;
    private final CompetencyCatalog competencyCatalog;
    private final AttainmentRecorder attainmentRecorder;
    private final CompletionEvaluator completionEvaluator;
    private final CertificateService certificateService;

    public LearningOutcomesService(
            LearningPathCommands pathCommands,
            CompetencyCatalog competencyCatalog,
            AttainmentRecorder attainmentRecorder,
            CompletionEvaluator completionEvaluator,
            CertificateService certificateService) {
        this.pathCommands = pathCommands;
        this.competencyCatalog = competencyCatalog;
        this.attainmentRecorder = attainmentRecorder;
        this.completionEvaluator = completionEvaluator;
        this.certificateService = certificateService;
    }

    @Override
    public List<LearningPathResponse> paths(UserAccessContext access, String status, String educationUnitId,
                                            String classId) {
        return pathCommands.paths(access, status, educationUnitId, classId);
    }

    @Override
    public LearningPathResponse createPath(UserAccessContext access, CreateLearningPathRequest request,
                                           String idempotencyKey, String correlationId) {
        return pathCommands.createPath(access, request, idempotencyKey, correlationId);
    }

    @Override
    public LearningPathResponse publishPath(UserAccessContext access, String pathId, long expectedVersion,
                                            String idempotencyKey, String correlationId) {
        return pathCommands.publishPath(access, pathId, expectedVersion, idempotencyKey, correlationId);
    }

    @Override
    public PathNodeResponse addNode(UserAccessContext access, String pathId, AddPathNodeRequest request,
                                    String idempotencyKey, String correlationId) {
        return pathCommands.addNode(access, pathId, request, idempotencyKey, correlationId);
    }

    @Override
    public PathDetailResponse path(UserAccessContext access, String pathId) {
        return pathCommands.path(access, pathId);
    }

    @Override
    public CompetencyResponse createCompetency(UserAccessContext access, CreateCompetencyRequest request,
                                               String idempotencyKey, String correlationId) {
        return competencyCatalog.createCompetency(access, request, idempotencyKey, correlationId);
    }

    @Override
    public CompetencyResponse competency(UserAccessContext access, String competencyId) {
        return competencyCatalog.competency(access, competencyId);
    }

    @Override
    public List<CompetencyResponse> competencies(UserAccessContext access, String educationUnitId) {
        return competencyCatalog.competencies(access, educationUnitId);
    }

    @Override
    public AttainmentResponse recordAttainment(UserAccessContext access, RecordAttainmentRequest request,
                                                String idempotencyKey, String correlationId) {
        return attainmentRecorder.recordAttainment(access, request, idempotencyKey, correlationId);
    }

    @Override
    public List<AttainmentResponse> attainments(UserAccessContext access, String learnerId) {
        return attainmentRecorder.attainments(access, learnerId);
    }

    @Override
    public CompletionResponse evaluateCompletion(UserAccessContext access, String learningPathId,
                                                 EvaluateCompletionRequest request, String idempotencyKey,
                                                 String correlationId) {
        return completionEvaluator.evaluateCompletion(access, learningPathId, request, idempotencyKey, correlationId);
    }

    @Override
    public List<CompletionResponse> completions(UserAccessContext access, String learnerId) {
        return completionEvaluator.completions(access, learnerId);
    }

    @Override
    public CertificateResponse issueCertificate(UserAccessContext access, IssueCertificateRequest request,
                                                String idempotencyKey, String correlationId) {
        return certificateService.issueCertificate(access, request, idempotencyKey, correlationId);
    }

    @Override
    public CertificateResponse revokeCertificate(UserAccessContext access, String certificateId,
                                                 RevokeCertificateRequest request, String idempotencyKey,
                                                 String correlationId) {
        return certificateService.revokeCertificate(access, certificateId, request, idempotencyKey, correlationId);
    }

    @Override
    public CertificateResponse verifyCertificate(UserAccessContext access, String verificationId) {
        return certificateService.verifyCertificate(access, verificationId);
    }

    @Override
    public List<CertificateResponse> certificates(UserAccessContext access, String learnerId) {
        return certificateService.certificates(access, learnerId);
    }
}
