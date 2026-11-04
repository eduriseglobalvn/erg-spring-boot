package com.erg.ergspringboot.learningoutcomes.internal;

import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeResponseMapper.completionResponse;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.COMPLETED;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.MAX_LIST;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.PUBLISHED;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.hash;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.key;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.now;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.required;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.resolveEventCorrelation;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.version;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.EvaluateCompletionRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.CompletionResponse;
import com.erg.ergspringboot.learningoutcomes.LearningPathCompletionEvaluated;
import com.erg.ergspringboot.learningoutcomes.persistence.LearningOutcomeCommandReceipt;
import com.erg.ergspringboot.learningoutcomes.persistence.LearningPath;
import com.erg.ergspringboot.learningoutcomes.persistence.LearningPathCompletion;
import com.erg.ergspringboot.learningoutcomes.persistence.LearningPathNode;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.learningoutcomes.persistence.LearningPathCompletionRepository;
import com.erg.ergspringboot.learningoutcomes.persistence.LearningPathNodeRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Evaluates a learner's completion of a published learning path from the completed content refs. */
@Service
public class CompletionEvaluator {
    private static final String INCOMPLETE = "INCOMPLETE";

    private final LearningPathNodeRepository nodes;
    private final LearningPathCompletionRepository completions;
    private final LearningPathLookup lookup;
    private final OutcomeVisibilityPolicy visibility;
    private final OutcomeReceiptStore receipts;
    private final OutcomeAuditTrail audit;
    private final ApplicationEventPublisher events;

    public CompletionEvaluator(
            LearningPathNodeRepository nodes,
            LearningPathCompletionRepository completions,
            LearningPathLookup lookup,
            OutcomeVisibilityPolicy visibility,
            OutcomeReceiptStore receipts,
            OutcomeAuditTrail audit,
            ApplicationEventPublisher events) {
        this.nodes = nodes;
        this.completions = completions;
        this.lookup = lookup;
        this.visibility = visibility;
        this.receipts = receipts;
        this.audit = audit;
        this.events = events;
    }

    @Transactional("postgresTransactionManager")
    public CompletionResponse evaluateCompletion(UserAccessContext access, String learningPathId,
                                                 EvaluateCompletionRequest request, String idempotencyKey,
                                                 String correlationId) {
        if (completions == null) throw new IllegalStateException("completion persistence is not configured");
        String tenant = visibility.tenant(access);
        EvaluateCompletionRequest safe = Objects.requireNonNull(request, "request");
        String learner = required(safe.learnerId(), "learnerId", 96);
        visibility.ensureLearnerAccess(access, learner);
        String pathId = required(learningPathId, "learningPathId", 96);
        List<String> completedRefs = normalizeRefs(safe.completedContentRefIds());
        if (completedRefs.size() > MAX_LIST) throw new BadRequestException("completedContentRefIds is too large");
        String key = key(idempotencyKey);
        String hash = hash("evaluate-completion", learner, pathId,
                Long.toString(safe.expectedVersion()), String.join("\u001e", completedRefs), safe.evidenceRef());
        LearningOutcomeCommandReceipt replay = receipts.begin(tenant, "EVALUATE_COMPLETION", key, hash);
        if (replay != null) return completionResponse(findCompletion(tenant, replay.getAggregateId()));

        LearningPath path = lookup.require(tenant, pathId);
        visibility.ensureScope(access, path.getEducationUnitId(), path.getClassId());
        if (!PUBLISHED.equals(path.getStatus())) {
            throw new ConflictException("completion requires a published learning path");
        }
        if (!Objects.equals(path.getRecordVersion(), safe.expectedVersion())) {
            throw new ConflictException("learning path changed; reload before evaluating completion");
        }
        List<LearningPathNode> pathNodes = nodes.findTop100ByTenantIdAndPathIdOrderBySortOrderAsc(tenant, pathId);
        Map<String, LearningPathNode> nodeByContentRef = new LinkedHashMap<>();
        pathNodes.forEach(node -> nodeByContentRef.put(node.getContentRefId(), node));
        for (String completedRef : completedRefs) {
            if (!nodeByContentRef.containsKey(completedRef)) {
                throw new BadRequestException("completed content is not part of the learning path");
            }
        }
        List<String> requiredRefs = pathNodes.stream().filter(LearningPathNode::isRequired)
                .map(LearningPathNode::getContentRefId).toList();
        long requiredCompleted = requiredRefs.stream().filter(completedRefs::contains).count();
        int requiredCount = requiredRefs.size();
        int progress = requiredCount == 0 ? 100 : (int) ((requiredCompleted * 100) / requiredCount);
        String status = requiredCompleted == requiredCount ? COMPLETED : INCOMPLETE;

        LearningPathCompletion completion = completions
                .findByTenantIdAndLearnerIdAndLearningPathId(tenant, learner, pathId).orElse(null);
        if (completion == null) {
            completion = new LearningPathCompletion();
            completion.setTenantId(tenant);
            completion.setLearnerId(learner);
            completion.setLearningPathId(pathId);
        }
        completion.setStatus(status);
        completion.setRequiredNodeCount(requiredCount);
        completion.setCompletedNodeCount(completedRefs.size());
        completion.setProgressPercent(progress);
        completion.setEvidenceRef(required(safe.evidenceRef(), "evidenceRef", 255));
        completion.setEvaluatedAt(now());
        completion.setEvaluatedBy(access.userId());
        LearningPathCompletion saved = completions.saveAndFlush(completion);
        receipts.save(tenant, "EVALUATE_COMPLETION", key, hash, "LEARNING_PATH_COMPLETION", saved.getId());
        String eventId = IdGenerator.newId();
        String eventCorrelationId = resolveEventCorrelation(correlationId, eventId);
        events.publishEvent(new LearningPathCompletionEvaluated(eventId, tenant, saved.getId(), learner, pathId,
                status, progress, access.userId(), version(saved), now(), 1, eventCorrelationId, eventCorrelationId));
        audit.record(access, "LEARNING_PATH_COMPLETION_EVALUATED", "LEARNING_PATH_COMPLETION", saved.getId(), key,
                Map.of("learnerId", learner, "learningPathId", pathId, "status", status,
                        "progressPercent", progress));
        return completionResponse(saved);
    }

    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public List<CompletionResponse> completions(UserAccessContext access, String learnerId) {
        if (completions == null) throw new IllegalStateException("completion persistence is not configured");
        String tenant = visibility.tenant(access);
        String learner = required(learnerId, "learnerId", 96);
        visibility.ensureLearnerAccess(access, learner);
        return completions.findTop100ByTenantIdAndLearnerIdOrderByEvaluatedAtDesc(tenant, learner).stream()
                .filter(value -> lookup.find(tenant, value.getLearningPathId())
                        .map(path -> visibility.canSee(access, path))
                        .orElse(false))
                .map(OutcomeResponseMapper::completionResponse)
                .toList();
    }

    private LearningPathCompletion findCompletion(String tenant, String id) {
        if (completions == null) throw new IllegalStateException("completion persistence is not configured");
        return completions.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("learning path completion not found"));
    }

    private List<String> normalizeRefs(List<String> refs) {
        if (refs == null) return List.of();
        LinkedHashMap<String, Boolean> unique = new LinkedHashMap<>();
        refs.stream().map(OutcomeValues::trimToNull).filter(Objects::nonNull).forEach(value -> unique.put(value, Boolean.TRUE));
        return List.copyOf(unique.keySet());
    }
}
