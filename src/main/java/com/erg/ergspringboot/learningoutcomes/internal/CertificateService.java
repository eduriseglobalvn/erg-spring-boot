package com.erg.ergspringboot.learningoutcomes.internal;

import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeResponseMapper.certificateResponse;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.COMPLETED;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.ISSUED;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.PUBLISHED;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.hash;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.key;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.now;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.required;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.resolveEventCorrelation;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.version;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.IssueCertificateRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.RevokeCertificateRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.CertificateResponse;
import com.erg.ergspringboot.learningoutcomes.OutcomeCertificateIssued;
import com.erg.ergspringboot.learningoutcomes.OutcomeCertificateRevoked;
import com.erg.ergspringboot.learningoutcomes.persistence.LearningOutcomeCommandReceipt;
import com.erg.ergspringboot.learningoutcomes.persistence.LearningPath;
import com.erg.ergspringboot.learningoutcomes.persistence.LearningPathCompletion;
import com.erg.ergspringboot.learningoutcomes.persistence.OutcomeCertificate;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.learningoutcomes.persistence.LearningPathCompletionRepository;
import com.erg.ergspringboot.learningoutcomes.persistence.OutcomeCertificateRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Outcome certificate lifecycle: issue after completion, revoke, verify and per-learner listing. */
@Service
public class CertificateService {
    private static final String REVOKED = "REVOKED";

    private final OutcomeCertificateRepository certificates;
    private final LearningPathCompletionRepository completions;
    private final LearningPathLookup lookup;
    private final OutcomeVisibilityPolicy visibility;
    private final OutcomeReceiptStore receipts;
    private final OutcomeAuditTrail audit;
    private final ApplicationEventPublisher events;

    public CertificateService(
            OutcomeCertificateRepository certificates,
            LearningPathCompletionRepository completions,
            LearningPathLookup lookup,
            OutcomeVisibilityPolicy visibility,
            OutcomeReceiptStore receipts,
            OutcomeAuditTrail audit,
            ApplicationEventPublisher events) {
        this.certificates = certificates;
        this.completions = completions;
        this.lookup = lookup;
        this.visibility = visibility;
        this.receipts = receipts;
        this.audit = audit;
        this.events = events;
    }

    @Transactional("postgresTransactionManager")
    public CertificateResponse issueCertificate(UserAccessContext access, IssueCertificateRequest request,
                                                String idempotencyKey, String correlationId) {
        String tenant = visibility.tenant(access);
        IssueCertificateRequest safe = Objects.requireNonNull(request, "request");
        String learner = required(safe.learnerId(), "learnerId", 96);
        visibility.ensureLearnerAccess(access, learner);
        String key = key(idempotencyKey);
        String hash = hash("issue-certificate", safe.learnerId(), safe.learningPathId(), safe.certificateNumber(), safe.evidenceRef(),
                safe.expiresAt() == null ? null : safe.expiresAt().toString());
        LearningOutcomeCommandReceipt replay = receipts.begin(tenant, "ISSUE_CERTIFICATE", key, hash);
        if (replay != null) return certificateResponse(findCertificate(tenant, replay.getAggregateId()));
        LearningPath path = lookup.require(tenant, required(safe.learningPathId(), "learningPathId", 96));
        visibility.ensureScope(access, path.getEducationUnitId(), path.getClassId());
        if (!PUBLISHED.equals(path.getStatus())) throw new ConflictException("certificate requires a published learning path");
        if (completions != null) {
            LearningPathCompletion completion = completions
                    .findByTenantIdAndLearnerIdAndLearningPathId(tenant, learner, path.getId())
                    .orElseThrow(() -> new ConflictException("certificate requires a completed learning path"));
            if (!COMPLETED.equals(completion.getStatus())) {
                throw new ConflictException("certificate requires a completed learning path");
            }
        }
        if (safe.expiresAt() != null && !safe.expiresAt().isAfter(now())) {
            throw new BadRequestException("expiresAt must be in the future");
        }
        if (certificates.findByTenantIdAndLearnerIdAndLearningPathId(tenant, learner, path.getId()).isPresent()) {
            throw new ConflictException("learner already has a certificate for this learning path");
        }
        String number = required(safe.certificateNumber(), "certificateNumber", 128);
        if (certificates.findByTenantIdAndCertificateNumber(tenant, number).isPresent()) {
            throw new ConflictException("certificate number already exists");
        }
        OutcomeCertificate certificate = new OutcomeCertificate();
        certificate.setTenantId(tenant);
        certificate.setLearnerId(learner);
        certificate.setLearningPathId(path.getId());
        certificate.setCertificateNumber(number);
        certificate.setVerificationId(IdGenerator.newId());
        certificate.setStatus(ISSUED);
        certificate.setEvidenceRef(required(safe.evidenceRef(), "evidenceRef", 255));
        certificate.setIssuedAt(now());
        certificate.setExpiresAt(safe.expiresAt());
        certificate.setIssuedBy(access.userId());
        OutcomeCertificate saved = certificates.saveAndFlush(certificate);
        receipts.save(tenant, "ISSUE_CERTIFICATE", key, hash, "OUTCOME_CERTIFICATE", saved.getId());
        String eventId = IdGenerator.newId();
        String eventCorrelationId = resolveEventCorrelation(correlationId, eventId);
        events.publishEvent(new OutcomeCertificateIssued(eventId, tenant, saved.getId(), saved.getLearnerId(),
                saved.getLearningPathId(), access.userId(), version(saved), now(), 1, eventCorrelationId, eventCorrelationId));
        audit.record(access, "OUTCOME_CERTIFICATE_ISSUED", "OUTCOME_CERTIFICATE", saved.getId(), key,
                Map.of("learnerId", saved.getLearnerId(), "learningPathId", saved.getLearningPathId()));
        return certificateResponse(saved);
    }

    @Transactional("postgresTransactionManager")
    public CertificateResponse revokeCertificate(UserAccessContext access, String certificateId,
                                                 RevokeCertificateRequest request, String idempotencyKey,
                                                 String correlationId) {
        String tenant = visibility.tenant(access);
        if (!visibility.hasManagePermission()) throw new ForbiddenException("learning outcome certificate management is required");
        RevokeCertificateRequest safe = Objects.requireNonNull(request, "request");
        String id = required(certificateId, "certificateId", 96);
        String key = key(idempotencyKey);
        String reason = required(safe.reason(), "reason", 255);
        String hash = hash("revoke-certificate", id, Long.toString(safe.expectedVersion()), reason);
        LearningOutcomeCommandReceipt replay = receipts.begin(tenant, "REVOKE_CERTIFICATE", key, hash);
        if (replay != null) return certificateResponse(findCertificate(tenant, replay.getAggregateId()));
        OutcomeCertificate certificate = findCertificate(tenant, id);
        LearningPath path = lookup.require(tenant, certificate.getLearningPathId());
        visibility.ensureScope(access, path.getEducationUnitId(), path.getClassId());
        if (version(certificate) != safe.expectedVersion()) {
            throw new ConflictException("certificate changed; reload before revoking");
        }
        if (!ISSUED.equals(certificate.getStatus())) {
            throw new ConflictException("only an issued certificate can be revoked");
        }
        certificate.setStatus(REVOKED);
        certificate.setRevokedAt(now());
        certificate.setRevokedBy(access.userId());
        certificate.setRevocationReason(reason);
        OutcomeCertificate saved = certificates.saveAndFlush(certificate);
        receipts.save(tenant, "REVOKE_CERTIFICATE", key, hash, "OUTCOME_CERTIFICATE", saved.getId());
        String eventId = IdGenerator.newId();
        String eventCorrelationId = resolveEventCorrelation(correlationId, eventId);
        events.publishEvent(new OutcomeCertificateRevoked(eventId, tenant, saved.getId(), saved.getLearnerId(),
                saved.getLearningPathId(), access.userId(), reason, version(saved), now(), 1,
                eventCorrelationId, eventCorrelationId));
        audit.record(access, "OUTCOME_CERTIFICATE_REVOKED", "OUTCOME_CERTIFICATE", saved.getId(), key,
                Map.of("reason", reason, "learnerId", saved.getLearnerId()));
        return certificateResponse(saved);
    }

    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public CertificateResponse verifyCertificate(UserAccessContext access, String verificationId) {
        String tenant = visibility.tenant(access);
        OutcomeCertificate certificate = certificates.findByTenantIdAndVerificationId(
                        tenant, required(verificationId, "verificationId", 96))
                .orElseThrow(() -> new ResourceNotFoundException("outcome certificate not found"));
        LearningPath path = lookup.require(tenant, certificate.getLearningPathId());
        visibility.ensureScope(access, path.getEducationUnitId(), path.getClassId());
        return certificateResponse(certificate);
    }

    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public List<CertificateResponse> certificates(UserAccessContext access, String learnerId) {
        String tenant = visibility.tenant(access);
        String learner = required(learnerId, "learnerId", 96);
        visibility.ensureLearnerAccess(access, learner);
        List<OutcomeCertificate> rows = certificates.findTop100ByTenantIdAndLearnerIdOrderByIssuedAtDesc(tenant, learner);
        Map<String, LearningPath> pathById = lookup.byIds(tenant, rows.stream().map(OutcomeCertificate::getLearningPathId).toList());
        return rows.stream()
                .filter(value -> visibility.visibleCertificate(access, pathById, value))
                .map(OutcomeResponseMapper::certificateResponse).toList();
    }

    private OutcomeCertificate findCertificate(String tenant, String id) {
        return certificates.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("outcome certificate not found"));
    }
}
