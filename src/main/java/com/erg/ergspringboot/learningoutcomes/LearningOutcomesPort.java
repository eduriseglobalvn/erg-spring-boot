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

public interface LearningOutcomesPort {
    List<LearningPathResponse> paths(UserAccessContext access, String status, String educationUnitId, String classId);

    LearningPathResponse createPath(UserAccessContext access, CreateLearningPathRequest request,
                                    String idempotencyKey, String correlationId);

    LearningPathResponse publishPath(UserAccessContext access, String pathId, long expectedVersion,
                                     String idempotencyKey, String correlationId);

    PathNodeResponse addNode(UserAccessContext access, String pathId, AddPathNodeRequest request,
                             String idempotencyKey, String correlationId);

    PathDetailResponse path(UserAccessContext access, String pathId);

    CompetencyResponse createCompetency(UserAccessContext access, CreateCompetencyRequest request,
                                        String idempotencyKey, String correlationId);

    CompetencyResponse competency(UserAccessContext access, String competencyId);

    List<CompetencyResponse> competencies(UserAccessContext access, String educationUnitId);

    AttainmentResponse recordAttainment(UserAccessContext access, RecordAttainmentRequest request,
                                        String idempotencyKey, String correlationId);

    List<AttainmentResponse> attainments(UserAccessContext access, String learnerId);

    CompletionResponse evaluateCompletion(UserAccessContext access, String learningPathId,
                                          EvaluateCompletionRequest request, String idempotencyKey, String correlationId);

    List<CompletionResponse> completions(UserAccessContext access, String learnerId);

    CertificateResponse issueCertificate(UserAccessContext access, IssueCertificateRequest request,
                                         String idempotencyKey, String correlationId);

    CertificateResponse revokeCertificate(UserAccessContext access, String certificateId,
                                          RevokeCertificateRequest request, String idempotencyKey, String correlationId);

    CertificateResponse verifyCertificate(UserAccessContext access, String verificationId);

    List<CertificateResponse> certificates(UserAccessContext access, String learnerId);
}
