package com.erg.ergspringboot.learningoutcomes.internal;

import com.erg.ergspringboot.audit.AuditOperations;
import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditRecord;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.learningoutcomes.CompetencyAttained;
import com.erg.ergspringboot.learningoutcomes.CompetencyProgressRecorded;
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
import com.erg.ergspringboot.learningoutcomes.LearningPathCreated;
import com.erg.ergspringboot.learningoutcomes.LearningPathPublished;
import com.erg.ergspringboot.learningoutcomes.OutcomeCertificateIssued;
import com.erg.ergspringboot.learningoutcomes.OutcomeCertificateRevoked;
import com.erg.ergspringboot.learningoutcomes.LearningPathCompletionEvaluated;
import com.erg.ergspringboot.model.learningoutcomes.Competency;
import com.erg.ergspringboot.model.learningoutcomes.CompetencyAttainment;
import com.erg.ergspringboot.model.learningoutcomes.LearningOutcomeCommandReceipt;
import com.erg.ergspringboot.model.learningoutcomes.LearningPathCompletion;
import com.erg.ergspringboot.model.learningoutcomes.LearningPath;
import com.erg.ergspringboot.model.learningoutcomes.LearningPathNode;
import com.erg.ergspringboot.model.learningoutcomes.OutcomeCertificate;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.learningoutcomes.CompetencyAttainmentRepository;
import com.erg.ergspringboot.repository.learningoutcomes.CompetencyRepository;
import com.erg.ergspringboot.repository.learningoutcomes.LearningOutcomeCommandReceiptRepository;
import com.erg.ergspringboot.repository.learningoutcomes.LearningPathNodeRepository;
import com.erg.ergspringboot.repository.learningoutcomes.LearningPathRepository;
import com.erg.ergspringboot.repository.learningoutcomes.LearningPathCompletionRepository;
import com.erg.ergspringboot.repository.learningoutcomes.OutcomeCertificateRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scoped application service for learning outcomes. The service owns
 * idempotency, scope checks and aggregate version guards; controllers only map
 * transport requests into these use cases.
 */
@Service
public class LearningOutcomesService implements LearningOutcomesPort {
    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String ACTIVE = "ACTIVE";
    private static final String ATTAINED = "ATTAINED";
    private static final String ISSUED = "ISSUED";
    private static final String REVOKED = "REVOKED";
    private static final String INCOMPLETE = "INCOMPLETE";
    private static final String COMPLETED = "COMPLETED";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final int MAX_LIST = 100;

    private final LearningPathRepository paths;
    private final LearningPathNodeRepository nodes;
    private final LearningPathCompletionRepository completions;
    private final CompetencyRepository competencies;
    private final CompetencyAttainmentRepository attainments;
    private final OutcomeCertificateRepository certificates;
    private final LearningOutcomeCommandReceiptRepository receipts;
    private final ApplicationEventPublisher events;
    private final AuditOperations audit;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public LearningOutcomesService(
            LearningPathRepository paths,
            LearningPathNodeRepository nodes,
            LearningPathCompletionRepository completions,
            CompetencyRepository competencies,
            CompetencyAttainmentRepository attainments,
            OutcomeCertificateRepository certificates,
            LearningOutcomeCommandReceiptRepository receipts,
            ApplicationEventPublisher events,
            AuditOperations audit) {
        this.paths = paths;
        this.nodes = nodes;
        this.completions = completions;
        this.competencies = competencies;
        this.attainments = attainments;
        this.certificates = certificates;
        this.receipts = receipts;
        this.events = events;
        this.audit = audit;
    }

    /** Compatibility constructor for focused unit tests that predate completion persistence. */
    public LearningOutcomesService(
            LearningPathRepository paths,
            LearningPathNodeRepository nodes,
            CompetencyRepository competencies,
            CompetencyAttainmentRepository attainments,
            OutcomeCertificateRepository certificates,
            LearningOutcomeCommandReceiptRepository receipts,
            ApplicationEventPublisher events,
            AuditOperations audit) {
        this(paths, nodes, null, competencies, attainments, certificates, receipts, events, audit);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public List<LearningPathResponse> paths(UserAccessContext access, String status, String educationUnitId,
                                            String classId) {
        String tenant = tenant(access);
        String requestedEducationUnitId = trimToNull(educationUnitId);
        String requestedClassId = trimToNull(classId);
        ensureScope(access, requestedEducationUnitId, requestedClassId);
        String requestedStatus = trimToNull(status);
        List<LearningPath> candidates = requestedStatus == null
                ? paths.findTop100ByTenantIdOrderByUpdatedAtDesc(tenant)
                : paths.findTop100ByTenantIdAndStatusOrderByUpdatedAtDesc(tenant,
                        requestedStatus.toUpperCase(Locale.ROOT));
        return candidates.stream()
                .filter(value -> requestedEducationUnitId == null
                        || requestedEducationUnitId.equals(trimToNull(value.getEducationUnitId())))
                .filter(value -> requestedClassId == null
                        || requestedClassId.equals(trimToNull(value.getClassId())))
                .filter(value -> access.canAccessEducationUnit(trimToNull(value.getEducationUnitId()))
                        && access.canAccessClass(trimToNull(value.getClassId())))
                .map(this::pathResponse)
                .toList();
    }

    @Override
    @Transactional("postgresTransactionManager")
    public LearningPathResponse createPath(UserAccessContext access, CreateLearningPathRequest request,
                                           String idempotencyKey, String correlationId) {
        String tenant = tenant(access);
        CreateLearningPathRequest safe = Objects.requireNonNull(request, "request");
        String key = key(idempotencyKey);
        String hash = hash("create-path", safe.title(), safe.description(), safe.educationUnitId(), safe.classId());
        LearningOutcomeCommandReceipt replay = begin(tenant, "CREATE_PATH", key, hash);
        if (replay != null) return pathResponse(findPath(tenant, replay.getAggregateId()));
        ensureScope(access, safe.educationUnitId(), safe.classId());
        LearningPath path = new LearningPath();
        path.setTenantId(tenant);
        path.setTitle(required(safe.title(), "title", 255));
        path.setDescription(trimToNull(safe.description()));
        path.setStatus(DRAFT);
        path.setEducationUnitId(trimToNull(safe.educationUnitId()));
        path.setClassId(trimToNull(safe.classId()));
        path.setCreatedBy(access.userId());
        path.setUpdatedBy(access.userId());
        LearningPath saved = paths.saveAndFlush(path);
        saveReceipt(tenant, "CREATE_PATH", key, hash, "LEARNING_PATH", saved.getId());
        String eventId = IdGenerator.newId();
        String eventCorrelationId = resolveEventCorrelation(correlationId, eventId);
        events.publishEvent(new LearningPathCreated(eventId, tenant, saved.getId(), access.userId(),
                version(saved), now(), 1, eventCorrelationId, eventCorrelationId));
        Map<String, Object> pathDetails = new LinkedHashMap<>();
        pathDetails.put("status", saved.getStatus());
        if (scope(access) != null) pathDetails.put("scope", scope(access));
        audit(access, "LEARNING_PATH_CREATED", "LEARNING_PATH", saved.getId(), key, pathDetails);
        return pathResponse(saved);
    }

    @Override
    @Transactional("postgresTransactionManager")
    public LearningPathResponse publishPath(UserAccessContext access, String pathId, long expectedVersion,
                                            String idempotencyKey, String correlationId) {
        String tenant = tenant(access);
        String id = required(pathId, "pathId", 96);
        String key = key(idempotencyKey);
        String hash = hash("publish-path", id, Long.toString(expectedVersion));
        LearningOutcomeCommandReceipt replay = begin(tenant, "PUBLISH_PATH", key, hash);
        if (replay != null) return pathResponse(findPath(tenant, replay.getAggregateId()));
        LearningPath path = findPath(tenant, id);
        ensureScope(access, path.getEducationUnitId(), path.getClassId());
        if (!Objects.equals(path.getRecordVersion(), expectedVersion)) {
            throw new ConflictException("learning path changed; reload before publishing");
        }
        if (!DRAFT.equals(path.getStatus())) throw new ConflictException("only a draft learning path can be published");
        if (!nodes.existsByTenantIdAndPathId(tenant, id)) {
            throw new BadRequestException("learning path must contain at least one node");
        }
        path.setStatus(PUBLISHED);
        path.setUpdatedBy(access.userId());
        LearningPath saved = paths.saveAndFlush(path);
        saveReceipt(tenant, "PUBLISH_PATH", key, hash, "LEARNING_PATH", saved.getId());
        String eventId = IdGenerator.newId();
        String eventCorrelationId = resolveEventCorrelation(correlationId, eventId);
        events.publishEvent(new LearningPathPublished(eventId, tenant, saved.getId(), access.userId(),
                version(saved), now(), 1, eventCorrelationId, eventCorrelationId));
        audit(access, "LEARNING_PATH_PUBLISHED", "LEARNING_PATH", saved.getId(), key,
                Map.of("status", saved.getStatus()));
        return pathResponse(saved);
    }

    @Override
    @Transactional("postgresTransactionManager")
    public PathNodeResponse addNode(UserAccessContext access, String pathId, AddPathNodeRequest request,
                                    String idempotencyKey, String correlationId) {
        String tenant = tenant(access);
        AddPathNodeRequest safe = Objects.requireNonNull(request, "request");
        String id = required(pathId, "pathId", 96);
        String key = key(idempotencyKey);
        String hash = hash("add-node", id, safe.contentRefId(), safe.contentKind(), Integer.toString(safe.sortOrder()),
                Boolean.toString(safe.required()), Long.toString(safe.expectedPathVersion()));
        LearningOutcomeCommandReceipt replay = begin(tenant, "ADD_PATH_NODE", key, hash);
        if (replay != null) return nodeResponse(nodes.findByTenantIdAndId(tenant, replay.getAggregateId())
                .orElseThrow(() -> new ResourceNotFoundException("path node not found")));
        LearningPath path = findPath(tenant, id);
        ensureScope(access, path.getEducationUnitId(), path.getClassId());
        if (!DRAFT.equals(path.getStatus())) throw new ConflictException("nodes can only be added to a draft path");
        if (!Objects.equals(path.getRecordVersion(), safe.expectedPathVersion())) {
            throw new ConflictException("learning path changed; reload before adding a node");
        }
        String contentRef = required(safe.contentRefId(), "contentRefId", 96);
        if (nodes.countByTenantIdAndPathId(tenant, id) >= MAX_LIST) {
            throw new BadRequestException("learning path cannot contain more than 100 nodes");
        }
        if (nodes.existsByTenantIdAndPathIdAndContentRefId(tenant, id, contentRef)) {
            throw new ConflictException("content is already present in this learning path");
        }
        LearningPathNode node = new LearningPathNode();
        node.setTenantId(tenant);
        node.setPathId(id);
        node.setContentRefId(contentRef);
        node.setContentKind(required(safe.contentKind(), "contentKind", 48).toUpperCase(Locale.ROOT));
        if (safe.sortOrder() < 0) throw new BadRequestException("sortOrder must be non-negative");
        node.setSortOrder(safe.sortOrder());
        node.setRequired(safe.required());
        LearningPathNode saved = nodes.saveAndFlush(node);
        path.setUpdatedBy(access.userId());
        paths.saveAndFlush(path);
        saveReceipt(tenant, "ADD_PATH_NODE", key, hash, "LEARNING_PATH_NODE", saved.getId());
        audit(access, "LEARNING_PATH_NODE_ADDED", "LEARNING_PATH_NODE", saved.getId(), key,
                Map.of("pathId", id, "contentKind", saved.getContentKind()));
        return nodeResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public PathDetailResponse path(UserAccessContext access, String pathId) {
        String tenant = tenant(access);
        LearningPath path = findPath(tenant, required(pathId, "pathId", 96));
        ensureScope(access, path.getEducationUnitId(), path.getClassId());
        List<PathNodeResponse> nodeResponses = nodes.findTop100ByTenantIdAndPathIdOrderBySortOrderAsc(tenant, path.getId())
                .stream().map(this::nodeResponse).toList();
        return new PathDetailResponse(pathResponse(path, nodeResponses.size()), nodeResponses);
    }

    @Override
    @Transactional("postgresTransactionManager")
    public CompetencyResponse createCompetency(UserAccessContext access, CreateCompetencyRequest request,
                                               String idempotencyKey, String correlationId) {
        String tenant = tenant(access);
        CreateCompetencyRequest safe = Objects.requireNonNull(request, "request");
        String key = key(idempotencyKey);
        String hash = hash("create-competency", safe.code(), safe.name(), safe.description(), safe.educationUnitId());
        LearningOutcomeCommandReceipt replay = begin(tenant, "CREATE_COMPETENCY", key, hash);
        if (replay != null) return competencyResponse(findCompetency(tenant, replay.getAggregateId()));
        ensureScope(access, safe.educationUnitId(), null);
        String code = required(safe.code(), "code", 96).toUpperCase(Locale.ROOT);
        if (competencies.findByTenantIdAndCode(tenant, code).isPresent()) {
            throw new ConflictException("competency code already exists");
        }
        Competency competency = new Competency();
        competency.setTenantId(tenant);
        competency.setCode(code);
        competency.setName(required(safe.name(), "name", 255));
        competency.setDescription(trimToNull(safe.description()));
        competency.setStatus(ACTIVE);
        competency.setEducationUnitId(trimToNull(safe.educationUnitId()));
        competency.setCreatedBy(access.userId());
        competency.setUpdatedBy(access.userId());
        Competency saved = competencies.saveAndFlush(competency);
        saveReceipt(tenant, "CREATE_COMPETENCY", key, hash, "COMPETENCY", saved.getId());
        audit(access, "COMPETENCY_CREATED", "COMPETENCY", saved.getId(), key,
                Map.of("code", saved.getCode()));
        return competencyResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public CompetencyResponse competency(UserAccessContext access, String competencyId) {
        Competency competency = findCompetency(tenant(access), required(competencyId, "competencyId", 96));
        ensureScope(access, competency.getEducationUnitId(), null);
        return competencyResponse(competency);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public List<CompetencyResponse> competencies(UserAccessContext access, String educationUnitId) {
        String tenant = tenant(access);
        String requestedEducationUnitId = trimToNull(educationUnitId);
        ensureScope(access, requestedEducationUnitId, null);
        List<Competency> candidates = requestedEducationUnitId == null
                ? competencies.findTop100ByTenantIdOrderByUpdatedAtDesc(tenant)
                : competencies.findTop100ByTenantIdAndEducationUnitIdOrderByUpdatedAtDesc(
                        tenant, requestedEducationUnitId);
        return candidates.stream()
                .filter(value -> access.canAccessEducationUnit(trimToNull(value.getEducationUnitId())))
                .map(this::competencyResponse)
                .toList();
    }

    @Override
    @Transactional("postgresTransactionManager")
    public AttainmentResponse recordAttainment(UserAccessContext access, RecordAttainmentRequest request,
                                                String idempotencyKey, String correlationId) {
        String tenant = tenant(access);
        RecordAttainmentRequest safe = Objects.requireNonNull(request, "request");
        ensureLearnerAccess(access, safe.learnerId());
        String key = key(idempotencyKey);
        int progressPercent = safe.progressPercent() == null ? 100 : safe.progressPercent();
        if (progressPercent < 0 || progressPercent > 100) {
            throw new BadRequestException("progressPercent must be between 0 and 100");
        }
        String hash = hash("record-attainment", safe.learnerId(), safe.competencyId(), safe.learningPathId(),
                safe.classId(), safe.evidenceRef(), Integer.toString(progressPercent));
        LearningOutcomeCommandReceipt replay = begin(tenant, "RECORD_ATTAINMENT", key, hash);
        if (replay != null) return attainmentResponse(findAttainment(tenant, replay.getAggregateId()));
        Competency competency = findCompetency(tenant, required(safe.competencyId(), "competencyId", 96));
        ensureScope(access, competency.getEducationUnitId(), null);
        String requestedClassId = trimToNull(safe.classId());
        ensureScope(access, null, requestedClassId);
        String pathId = trimToNull(safe.learningPathId());
        if (pathId != null) {
            LearningPath path = findPath(tenant, pathId);
            ensureScope(access, path.getEducationUnitId(), path.getClassId());
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
        saveReceipt(tenant, "RECORD_ATTAINMENT", key, hash, "COMPETENCY_ATTAINMENT", saved.getId());
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
        audit(access, progressPercent == 100 ? "COMPETENCY_ATTAINED" : "COMPETENCY_PROGRESS_RECORDED",
                "COMPETENCY_ATTAINMENT", saved.getId(), key,
                Map.of("learnerId", saved.getLearnerId(), "competencyId", saved.getCompetencyId(),
                        "progressPercent", progressPercent));
        return attainmentResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public List<AttainmentResponse> attainments(UserAccessContext access, String learnerId) {
        String tenant = tenant(access);
        String learner = required(learnerId, "learnerId", 96);
        ensureLearnerAccess(access, learner);
        return attainments.findTop100ByTenantIdAndLearnerIdOrderByAchievedAtDesc(tenant, learner).stream()
                .filter(value -> visibleAttainment(access, tenant, value))
                .map(this::attainmentResponse).toList();
    }

    @Override
    @Transactional("postgresTransactionManager")
    public CompletionResponse evaluateCompletion(UserAccessContext access, String learningPathId,
                                                 EvaluateCompletionRequest request, String idempotencyKey,
                                                 String correlationId) {
        if (completions == null) throw new IllegalStateException("completion persistence is not configured");
        String tenant = tenant(access);
        EvaluateCompletionRequest safe = Objects.requireNonNull(request, "request");
        String learner = required(safe.learnerId(), "learnerId", 96);
        ensureLearnerAccess(access, learner);
        String pathId = required(learningPathId, "learningPathId", 96);
        List<String> completedRefs = normalizeRefs(safe.completedContentRefIds());
        if (completedRefs.size() > MAX_LIST) throw new BadRequestException("completedContentRefIds is too large");
        String key = key(idempotencyKey);
        String hash = hash("evaluate-completion", learner, pathId,
                Long.toString(safe.expectedVersion()), String.join("\u001e", completedRefs), safe.evidenceRef());
        LearningOutcomeCommandReceipt replay = begin(tenant, "EVALUATE_COMPLETION", key, hash);
        if (replay != null) return completionResponse(findCompletion(tenant, replay.getAggregateId()));

        LearningPath path = findPath(tenant, pathId);
        ensureScope(access, path.getEducationUnitId(), path.getClassId());
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
        saveReceipt(tenant, "EVALUATE_COMPLETION", key, hash, "LEARNING_PATH_COMPLETION", saved.getId());
        String eventId = IdGenerator.newId();
        String eventCorrelationId = resolveEventCorrelation(correlationId, eventId);
        events.publishEvent(new LearningPathCompletionEvaluated(eventId, tenant, saved.getId(), learner, pathId,
                status, progress, access.userId(), version(saved), now(), 1, eventCorrelationId, eventCorrelationId));
        audit(access, "LEARNING_PATH_COMPLETION_EVALUATED", "LEARNING_PATH_COMPLETION", saved.getId(), key,
                Map.of("learnerId", learner, "learningPathId", pathId, "status", status,
                        "progressPercent", progress));
        return completionResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public List<CompletionResponse> completions(UserAccessContext access, String learnerId) {
        if (completions == null) throw new IllegalStateException("completion persistence is not configured");
        String tenant = tenant(access);
        String learner = required(learnerId, "learnerId", 96);
        ensureLearnerAccess(access, learner);
        return completions.findTop100ByTenantIdAndLearnerIdOrderByEvaluatedAtDesc(tenant, learner).stream()
                .filter(value -> paths.findByTenantIdAndId(tenant, value.getLearningPathId())
                        .map(path -> access.canAccessEducationUnit(trimToNull(path.getEducationUnitId()))
                                && access.canAccessClass(trimToNull(path.getClassId())))
                        .orElse(false))
                .map(this::completionResponse)
                .toList();
    }

    @Override
    @Transactional("postgresTransactionManager")
    public CertificateResponse issueCertificate(UserAccessContext access, IssueCertificateRequest request,
                                                String idempotencyKey, String correlationId) {
        String tenant = tenant(access);
        IssueCertificateRequest safe = Objects.requireNonNull(request, "request");
        String learner = required(safe.learnerId(), "learnerId", 96);
        ensureLearnerAccess(access, learner);
        String key = key(idempotencyKey);
        String hash = hash("issue-certificate", safe.learnerId(), safe.learningPathId(), safe.certificateNumber(), safe.evidenceRef(),
                safe.expiresAt() == null ? null : safe.expiresAt().toString());
        LearningOutcomeCommandReceipt replay = begin(tenant, "ISSUE_CERTIFICATE", key, hash);
        if (replay != null) return certificateResponse(findCertificate(tenant, replay.getAggregateId()));
        LearningPath path = findPath(tenant, required(safe.learningPathId(), "learningPathId", 96));
        ensureScope(access, path.getEducationUnitId(), path.getClassId());
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
        saveReceipt(tenant, "ISSUE_CERTIFICATE", key, hash, "OUTCOME_CERTIFICATE", saved.getId());
        String eventId = IdGenerator.newId();
        String eventCorrelationId = resolveEventCorrelation(correlationId, eventId);
        events.publishEvent(new OutcomeCertificateIssued(eventId, tenant, saved.getId(), saved.getLearnerId(),
                saved.getLearningPathId(), access.userId(), version(saved), now(), 1, eventCorrelationId, eventCorrelationId));
        audit(access, "OUTCOME_CERTIFICATE_ISSUED", "OUTCOME_CERTIFICATE", saved.getId(), key,
                Map.of("learnerId", saved.getLearnerId(), "learningPathId", saved.getLearningPathId()));
        return certificateResponse(saved);
    }

    @Override
    @Transactional("postgresTransactionManager")
    public CertificateResponse revokeCertificate(UserAccessContext access, String certificateId,
                                                 RevokeCertificateRequest request, String idempotencyKey,
                                                 String correlationId) {
        String tenant = tenant(access);
        if (!hasManagePermission()) throw new ForbiddenException("learning outcome certificate management is required");
        RevokeCertificateRequest safe = Objects.requireNonNull(request, "request");
        String id = required(certificateId, "certificateId", 96);
        String key = key(idempotencyKey);
        String reason = required(safe.reason(), "reason", 255);
        String hash = hash("revoke-certificate", id, Long.toString(safe.expectedVersion()), reason);
        LearningOutcomeCommandReceipt replay = begin(tenant, "REVOKE_CERTIFICATE", key, hash);
        if (replay != null) return certificateResponse(findCertificate(tenant, replay.getAggregateId()));
        OutcomeCertificate certificate = findCertificate(tenant, id);
        LearningPath path = findPath(tenant, certificate.getLearningPathId());
        ensureScope(access, path.getEducationUnitId(), path.getClassId());
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
        saveReceipt(tenant, "REVOKE_CERTIFICATE", key, hash, "OUTCOME_CERTIFICATE", saved.getId());
        String eventId = IdGenerator.newId();
        String eventCorrelationId = resolveEventCorrelation(correlationId, eventId);
        events.publishEvent(new OutcomeCertificateRevoked(eventId, tenant, saved.getId(), saved.getLearnerId(),
                saved.getLearningPathId(), access.userId(), reason, version(saved), now(), 1,
                eventCorrelationId, eventCorrelationId));
        audit(access, "OUTCOME_CERTIFICATE_REVOKED", "OUTCOME_CERTIFICATE", saved.getId(), key,
                Map.of("reason", reason, "learnerId", saved.getLearnerId()));
        return certificateResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public CertificateResponse verifyCertificate(UserAccessContext access, String verificationId) {
        String tenant = tenant(access);
        OutcomeCertificate certificate = certificates.findByTenantIdAndVerificationId(
                        tenant, required(verificationId, "verificationId", 96))
                .orElseThrow(() -> new ResourceNotFoundException("outcome certificate not found"));
        LearningPath path = findPath(tenant, certificate.getLearningPathId());
        ensureScope(access, path.getEducationUnitId(), path.getClassId());
        return certificateResponse(certificate);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public List<CertificateResponse> certificates(UserAccessContext access, String learnerId) {
        String tenant = tenant(access);
        String learner = required(learnerId, "learnerId", 96);
        ensureLearnerAccess(access, learner);
        return certificates.findTop100ByTenantIdAndLearnerIdOrderByIssuedAtDesc(tenant, learner).stream()
                .filter(value -> visibleCertificate(access, tenant, value))
                .map(this::certificateResponse).toList();
    }

    private LearningOutcomeCommandReceipt begin(String tenant, String operation, String key, String hash) {
        lock(tenant + ":" + operation + ":" + key);
        LearningOutcomeCommandReceipt existing = receipts
                .findByTenantIdAndIdempotencyKeyAndOperation(tenant, key, operation).orElse(null);
        if (existing != null && !hash.equals(existing.getPayloadHash())) {
            throw new ConflictException("idempotency key was used for another learning-outcome command");
        }
        return existing;
    }

    private void saveReceipt(String tenant, String operation, String key, String hash, String aggregateType, String aggregateId) {
        LearningOutcomeCommandReceipt receipt = new LearningOutcomeCommandReceipt();
        receipt.setTenantId(tenant);
        receipt.setOperation(operation);
        receipt.setIdempotencyKey(key);
        receipt.setPayloadHash(hash);
        receipt.setAggregateType(aggregateType);
        receipt.setAggregateId(aggregateId);
        receipts.saveAndFlush(receipt);
    }

    private void lock(String lockKey) {
        if (entityManager == null) return;
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:lockKey, 0))")
                .setParameter("lockKey", lockKey).getSingleResult();
    }

    private void audit(UserAccessContext access, String action, String resourceType, String resourceId,
                       String idempotencyKey, Map<String, Object> details) {
        Map<String, Object> bounded = new LinkedHashMap<>();
        details.forEach((name, value) -> {
            if (bounded.size() < 16 && name != null && value != null) bounded.put(name, String.valueOf(value));
        });
        audit.record(new AuditRecord(access.tenantId(), access.userId(),
                access.currentClassId() == null ? "EDUCATION_UNIT" : "CLASS",
                access.currentClassId() == null ? access.currentEducationUnitId() : access.currentClassId(),
                action, resourceType, resourceId, AuditOutcome.SUCCESS, null, null, null,
                bounded, "learning-outcomes:" + idempotencyKey, now(), null));
    }

    private void ensureLearnerAccess(UserAccessContext access, String learnerId) {
        String learner = required(learnerId, "learnerId", 96);
        if (learner.equals(access.userId()) || hasManagePermission()) return;
        throw new ForbiddenException("learner is outside the current access context");
    }

    private void ensureManageAccess() {
        if (!hasManagePermission()) {
            throw new ForbiddenException("learning outcome management permission is required");
        }
    }

    private boolean hasManagePermission() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof ErgPrincipal principal)) return false;
        return principal.permissions().stream().map(this::normalizePermission)
                .anyMatch(permission -> permission.equals("*") || permission.equals("learning.outcomes.manage"));
    }

    private void ensureScope(UserAccessContext access, String educationUnitId, String classId) {
        if (!access.canAccessEducationUnit(trimToNull(educationUnitId)) || !access.canAccessClass(trimToNull(classId))) {
            throw new ForbiddenException("learning outcome is outside the current scope");
        }
    }

    private String tenant(UserAccessContext access) {
        if (access == null || access.userId() == null || access.userId().isBlank()) {
            throw new ForbiddenException("authenticated access context is required");
        }
        return AuthenticatedTenant.require(access.tenantId());
    }

    private LearningPath findPath(String tenant, String id) {
        return paths.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("learning path not found"));
    }

    private Competency findCompetency(String tenant, String id) {
        return competencies.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("competency not found"));
    }

    private CompetencyAttainment findAttainment(String tenant, String id) {
        return attainments.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("competency attainment not found"));
    }

    private LearningPathCompletion findCompletion(String tenant, String id) {
        if (completions == null) throw new IllegalStateException("completion persistence is not configured");
        return completions.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("learning path completion not found"));
    }

    private OutcomeCertificate findCertificate(String tenant, String id) {
        return certificates.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("outcome certificate not found"));
    }

    private LearningPathResponse pathResponse(LearningPath path) {
        return pathResponse(path, nodes.findTop100ByTenantIdAndPathIdOrderBySortOrderAsc(path.getTenantId(), path.getId()).size());
    }

    private LearningPathResponse pathResponse(LearningPath path, int nodeCount) {
        return new LearningPathResponse(path.getId(), path.getTenantId(), path.getTitle(), path.getDescription(),
                path.getStatus(), path.getEducationUnitId(), path.getClassId(), version(path), nodeCount,
                path.getCreatedAt(), path.getUpdatedAt());
    }

    private PathNodeResponse nodeResponse(LearningPathNode node) {
        return new PathNodeResponse(node.getId(), node.getTenantId(), node.getPathId(), node.getContentRefId(),
                node.getContentKind(), node.getSortOrder(), node.isRequired(), node.getCreatedAt());
    }

    private boolean visibleAttainment(UserAccessContext access, String tenant, CompetencyAttainment attainment) {
        if (!access.canAccessClass(trimToNull(attainment.getClassId()))) return false;
        String pathId = trimToNull(attainment.getLearningPathId());
        if (pathId == null) return true;
        Optional<LearningPath> path = paths.findByTenantIdAndId(tenant, pathId);
        if (path.isEmpty()) return false;
        LearningPath value = path.get();
        if (!access.canAccessEducationUnit(trimToNull(value.getEducationUnitId()))
                || !access.canAccessClass(trimToNull(value.getClassId()))) return false;
        String pathClassId = trimToNull(value.getClassId());
        String recordClassId = trimToNull(attainment.getClassId());
        return pathClassId == null || recordClassId == null || pathClassId.equals(recordClassId);
    }

    private boolean visibleCertificate(UserAccessContext access, String tenant, OutcomeCertificate certificate) {
        LearningPath path = paths.findByTenantIdAndId(tenant, certificate.getLearningPathId()).orElse(null);
        return path != null
                && access.canAccessEducationUnit(trimToNull(path.getEducationUnitId()))
                && access.canAccessClass(trimToNull(path.getClassId()));
    }

    private CompetencyResponse competencyResponse(Competency competency) {
        return new CompetencyResponse(competency.getId(), competency.getTenantId(), competency.getCode(),
                competency.getName(), competency.getDescription(), competency.getStatus(), competency.getEducationUnitId(),
                version(competency), competency.getCreatedAt(), competency.getUpdatedAt());
    }

    private AttainmentResponse attainmentResponse(CompetencyAttainment attainment) {
        return new AttainmentResponse(attainment.getId(), attainment.getTenantId(), attainment.getLearnerId(),
                attainment.getCompetencyId(), attainment.getLearningPathId(), attainment.getClassId(), attainment.getStatus(),
                attainment.getEvidenceRef(), attainment.getAchievedAt(), version(attainment),
                attainment.getProgressPercent() == null ? 0 : attainment.getProgressPercent());
    }

    private CertificateResponse certificateResponse(OutcomeCertificate certificate) {
        return new CertificateResponse(certificate.getId(), certificate.getTenantId(), certificate.getLearnerId(),
                certificate.getLearningPathId(), certificate.getCertificateNumber(), certificate.getVerificationId(),
                effectiveCertificateStatus(certificate), certificate.getEvidenceRef(), certificate.getIssuedAt(), certificate.getExpiresAt(),
                certificate.getRevokedAt(), version(certificate), certificate.getRevokedBy(),
                certificate.getRevocationReason());
    }

    private CompletionResponse completionResponse(LearningPathCompletion completion) {
        return new CompletionResponse(completion.getId(), completion.getTenantId(), completion.getLearnerId(),
                completion.getLearningPathId(), completion.getStatus(), completion.getRequiredNodeCount(),
                completion.getCompletedNodeCount(), completion.getProgressPercent(), completion.getEvidenceRef(),
                completion.getEvaluatedAt(), version(completion));
    }

    private String effectiveCertificateStatus(OutcomeCertificate certificate) {
        if (ISSUED.equals(certificate.getStatus()) && certificate.getExpiresAt() != null
                && !certificate.getExpiresAt().isAfter(now())) return "EXPIRED";
        return certificate.getStatus();
    }

    private List<String> normalizeRefs(List<String> refs) {
        if (refs == null) return List.of();
        LinkedHashMap<String, Boolean> unique = new LinkedHashMap<>();
        refs.stream().map(this::trimToNull).filter(Objects::nonNull).forEach(value -> unique.put(value, Boolean.TRUE));
        return List.copyOf(unique.keySet());
    }

    private long version(Object entity) {
        return switch (entity) {
            case LearningPath value -> value.getRecordVersion() == null ? 0 : value.getRecordVersion();
            case Competency value -> value.getRecordVersion() == null ? 0 : value.getRecordVersion();
            case CompetencyAttainment value -> value.getRecordVersion() == null ? 0 : value.getRecordVersion();
            case LearningPathCompletion value -> value.getRecordVersion() == null ? 0 : value.getRecordVersion();
            case OutcomeCertificate value -> value.getRecordVersion() == null ? 0 : value.getRecordVersion();
            default -> 0;
        };
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String scope(UserAccessContext access) {
        return access.currentClassId() == null ? access.currentEducationUnitId() : access.currentClassId();
    }

    private String key(String value) {
        return required(value, "idempotencyKey", 255);
    }

    private String required(String value, String name, int max) {
        String normalized = trimToNull(value);
        if (normalized == null) throw new BadRequestException(name + " is required");
        if (normalized.length() > max) throw new BadRequestException(name + " is too long");
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizePermission(String permission) {
        return permission == null ? "" : permission.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveEventCorrelation(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String hash(String operation, String... values) {
        StringBuilder payload = new StringBuilder(operation);
        for (String value : values) payload.append('\u001f').append(value == null ? "" : value);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
