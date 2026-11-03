package com.erg.ergspringboot.learningoutcomes.internal;

import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeResponseMapper.attainmentResponse;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.PUBLISHED;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.hash;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.key;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.now;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.required;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.resolveEventCorrelation;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.trimToNull;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.version;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.learningoutcomes.CompetencyAttained;
import com.erg.ergspringboot.learningoutcomes.CompetencyProgressRecorded;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.RecordAttainmentRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.AttainmentResponse;
import com.erg.ergspringboot.model.learningoutcomes.Competency;
import com.erg.ergspringboot.model.learningoutcomes.CompetencyAttainment;
import com.erg.ergspringboot.model.learningoutcomes.LearningOutcomeCommandReceipt;
import com.erg.ergspringboot.model.learningoutcomes.LearningPath;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.learningoutcomes.CompetencyAttainmentRepository;
import com.erg.ergspringboot.repository.learningoutcomes.CompetencyRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records learner competency progress/attainment against an optional published learning path. */
@Service
public class AttainmentRecorder {
    private static final String ATTAINED = "ATTAINED";
    private static final String IN_PROGRESS = "IN_PROGRESS";

    private final CompetencyRepository competencies;
    private final CompetencyAttainmentRepository attainments;
    private final LearningPathLookup lookup;
    private final OutcomeVisibilityPolicy visibility;
    private final OutcomeReceiptStore receipts;
    private final OutcomeAuditTrail audit;
    private final ApplicationEventPublisher events;

    public AttainmentRecorder(
            CompetencyRepository competencies,
            CompetencyAttainmentRepository attainments,
            LearningPathLookup lookup,
            OutcomeVisibilityPolicy visibility,
            OutcomeReceiptStore receipts,
            OutcomeAuditTrail audit,
            ApplicationEventPublisher events) {
        this.competencies = competencies;
        this.attainments = attainments;
        this.lookup = lookup;
        this.visibility = visibility;
        this.receipts = receipts;
        this.audit = audit;
        this.events = events;
    }

    @Transactional("postgresTransactionManager")
    public AttainmentResponse recordAttainment(UserAccessContext access, RecordAttainmentRequest request,
                                                String idempotencyKey, String correlationId) {
        String tenant = visibility.tenant(access);
        RecordAttainmentRequest safe = Objects.requireNonNull(request, "request");
        visibility.ensureLearnerAccess(access, safe.learnerId());
        String key = key(idempotencyKey);
        int progressPercent = safe.progressPercent() == null ? 100 : safe.progressPercent();
        if (progressPercent < 0 || progressPercent > 100) {
            throw new BadRequestException("progressPercent must be between 0 and 100");
        }
        String hash = hash("record-attainment", safe.learnerId(), safe.competencyId(), safe.learningPathId(),
                safe.classId(), safe.evidenceRef(), Integer.toString(progressPercent));
        LearningOutcomeCommandReceipt replay = receipts.begin(tenant, "RECORD_ATTAINMENT", key, hash);
        if (replay != null) return attainmentResponse(findAttainment(tenant, replay.getAggregateId()));
        Competency competency = findCompetency(tenant, required(safe.competencyId(), "competencyId", 96));
        visibility.ensureScope(access, competency.getEducationUnitId(), null);
        String requestedClassId = trimToNull(safe.classId());
        visibility.ensureScope(access, null, requestedClassId);
        String pathId = trimToNull(safe.learningPathId());
        if (pathId != null) {
            LearningPath path = lookup.require(tenant, pathId);
            visibility.ensureScope(access, path.getEducationUnitId(), path.getClassId());
            if (!PUBLISHED.equals(path.getStatus())) throw new ConflictException("attainment requires a published learning path");
            String pathClassId = trimToNull(path.getClassId());
            if (pathClassId != null && requestedClassId != null && !pathClassId.equals(requestedClassId)) {
                throw new ConflictException("attainment class must match the learning path class");
            }
            if (requestedClassId == null) requestedClassId = pathClassId;
        }
        if (attainments.findByTenantIdAndLearnerIdAndCompetencyIdAndLearningPathId(
                tenant, safe.learnerId().trim(), competency.getId(), pathId).isPresent()) {
            throw new ConflictException("competency is already recorded for this learner and path");
        }
        CompetencyAttainment attainment = new CompetencyAttainment();
        attainment.setTenantId(tenant);
        attainment.setLearnerId(required(safe.learnerId(), "learnerId", 96));
        attainment.setCompetencyId(competency.getId());
        attainment.setLearningPathId(pathId);
        attainment.setClassId(requestedClassId);
        attainment.setStatus(progressPercent == 100 ? ATTAINED : IN_PROGRESS);
        attainment.setEvidenceRef(required(safe.evidenceRef(), "evidenceRef", 255));
        attainment.setProgressPercent(progressPercent);
        attainment.setAchievedAt(now());
        attainment.setRecordedBy(access.userId());
        CompetencyAttainment saved = attainments.saveAndFlush(attainment);
        receipts.save(tenant, "RECORD_ATTAINMENT", key, hash, "COMPETENCY_ATTAINMENT", saved.getId());
        String eventId = IdGenerator.newId();
        String eventCorrelationId = resolveEventCorrelation(correlationId, eventId);
        events.publishEvent(new CompetencyProgressRecorded(eventId, tenant, saved.getId(), saved.getLearnerId(),
                saved.getCompetencyId(), saved.getLearningPathId(), progressPercent, access.userId(), version(saved),
                now(), 1, eventCorrelationId, eventCorrelationId));
        if (progressPercent == 100) {
            events.publishEvent(new CompetencyAttained(IdGenerator.newId(), tenant, saved.getId(), saved.getLearnerId(),
                    saved.getCompetencyId(), saved.getLearningPathId(), access.userId(), version(saved), now(), 1,
                    eventCorrelationId, eventCorrelationId));
        }
        audit.record(access, progressPercent == 100 ? "COMPETENCY_ATTAINED" : "COMPETENCY_PROGRESS_RECORDED",
                "COMPETENCY_ATTAINMENT", saved.getId(), key,
                Map.of("learnerId", saved.getLearnerId(), "competencyId", saved.getCompetencyId(),
                        "progressPercent", progressPercent));
        return attainmentResponse(saved);
    }

    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public List<AttainmentResponse> attainments(UserAccessContext access, String learnerId) {
        String tenant = visibility.tenant(access);
        String learner = required(learnerId, "learnerId", 96);
        visibility.ensureLearnerAccess(access, learner);
        List<CompetencyAttainment> rows = attainments.findTop100ByTenantIdAndLearnerIdOrderByAchievedAtDesc(tenant, learner);
        // SQ-10: one batched path lookup instead of one query per attainment.
        Map<String, LearningPath> pathById = lookup.byIds(tenant, rows.stream().map(CompetencyAttainment::getLearningPathId).toList());
        return rows.stream()
                .filter(value -> visibility.visibleAttainment(access, pathById, value))
                .map(OutcomeResponseMapper::attainmentResponse).toList();
    }

    private Competency findCompetency(String tenant, String id) {
        return competencies.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("competency not found"));
    }

    private CompetencyAttainment findAttainment(String tenant, String id) {
        return attainments.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("competency attainment not found"));
    }
}
