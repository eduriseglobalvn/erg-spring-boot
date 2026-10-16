package com.erg.ergspringboot.learningoutcomes;

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

/** Public use-case facade for the learning-outcomes module. */
@Service
public class LearningOutcomesOperations {
    private final LearningOutcomesPort port;

    public LearningOutcomesOperations(LearningOutcomesPort port) {
        this.port = port;
    }

    public LearningPathResponse createPath(UserAccessContext access, CreateLearningPathRequest request,
                                           String idempotencyKey, String correlationId) {
        return port.createPath(access, request, idempotencyKey, correlationId);
    }

    public List<LearningPathResponse> paths(UserAccessContext access, String status, String educationUnitId,
                                            String classId) {
        return port.paths(access, status, educationUnitId, classId);
    }

    public LearningPathResponse publishPath(UserAccessContext access, String pathId, long expectedVersion,
                                            String idempotencyKey, String correlationId) {
        return port.publishPath(access, pathId, expectedVersion, idempotencyKey, correlationId);
    }

    public PathNodeResponse addNode(UserAccessContext access, String pathId, AddPathNodeRequest request,
                                    String idempotencyKey, String correlationId) {
        return port.addNode(access, pathId, request, idempotencyKey, correlationId);
    }

    public PathDetailResponse path(UserAccessContext access, String pathId) {
        return port.path(access, pathId);
    }

    public CompetencyResponse createCompetency(UserAccessContext access, CreateCompetencyRequest request,
                                               String idempotencyKey, String correlationId) {
        return port.createCompetency(access, request, idempotencyKey, correlationId);
    }

    public CompetencyResponse competency(UserAccessContext access, String competencyId) {
        return port.competency(access, competencyId);
    }

    public List<CompetencyResponse> competencies(UserAccessContext access, String educationUnitId) {
        return port.competencies(access, educationUnitId);
    }

    public AttainmentResponse recordAttainment(UserAccessContext access, RecordAttainmentRequest request,
                                               String idempotencyKey, String correlationId) {
        return port.recordAttainment(access, request, idempotencyKey, correlationId);
    }

    public List<AttainmentResponse> attainments(UserAccessContext access, String learnerId) {
        return port.attainments(access, learnerId);
    }

    public CompletionResponse evaluateCompletion(UserAccessContext access, String learningPathId,
                                                 EvaluateCompletionRequest request, String idempotencyKey,
                                                 String correlationId) {
        return port.evaluateCompletion(access, learningPathId, request, idempotencyKey, correlationId);
    }

    public List<CompletionResponse> completions(UserAccessContext access, String learnerId) {
        return port.completions(access, learnerId);
    }

    public CertificateResponse issueCertificate(UserAccessContext access, IssueCertificateRequest request,
                                                String idempotencyKey, String correlationId) {
        return port.issueCertificate(access, request, idempotencyKey, correlationId);
    }

    public CertificateResponse revokeCertificate(UserAccessContext access, String certificateId,
                                                 RevokeCertificateRequest request, String idempotencyKey,
                                                 String correlationId) {
        return port.revokeCertificate(access, certificateId, request, idempotencyKey, correlationId);
    }

    public CertificateResponse verifyCertificate(UserAccessContext access, String verificationId) {
        return port.verifyCertificate(access, verificationId);
    }

    public List<CertificateResponse> certificates(UserAccessContext access, String learnerId) {
        return port.certificates(access, learnerId);
    }
}
