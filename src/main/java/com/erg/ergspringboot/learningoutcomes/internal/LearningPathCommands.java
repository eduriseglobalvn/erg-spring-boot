package com.erg.ergspringboot.learningoutcomes.internal;

import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeResponseMapper.nodeResponse;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeResponseMapper.pathResponse;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.MAX_LIST;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.PUBLISHED;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.hash;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.key;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.now;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.required;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.resolveEventCorrelation;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.scope;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.trimToNull;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.version;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.AddPathNodeRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.CreateLearningPathRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.LearningPathResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.PathDetailResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.PathNodeResponse;
import com.erg.ergspringboot.learningoutcomes.LearningPathCreated;
import com.erg.ergspringboot.learningoutcomes.LearningPathPublished;
import com.erg.ergspringboot.model.learningoutcomes.LearningOutcomeCommandReceipt;
import com.erg.ergspringboot.model.learningoutcomes.LearningPath;
import com.erg.ergspringboot.model.learningoutcomes.LearningPathNode;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.learningoutcomes.LearningPathNodeRepository;
import com.erg.ergspringboot.repository.learningoutcomes.LearningPathRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Learning path lifecycle: draft creation, node authoring, publishing and path reads. */
@Service
public class LearningPathCommands {
    private static final String DRAFT = "DRAFT";

    private final LearningPathRepository paths;
    private final LearningPathNodeRepository nodes;
    private final LearningPathLookup lookup;
    private final OutcomeVisibilityPolicy visibility;
    private final OutcomeReceiptStore receipts;
    private final OutcomeAuditTrail audit;
    private final ApplicationEventPublisher events;

    public LearningPathCommands(
            LearningPathRepository paths,
            LearningPathNodeRepository nodes,
            LearningPathLookup lookup,
            OutcomeVisibilityPolicy visibility,
            OutcomeReceiptStore receipts,
            OutcomeAuditTrail audit,
            ApplicationEventPublisher events) {
        this.paths = paths;
        this.nodes = nodes;
        this.lookup = lookup;
        this.visibility = visibility;
        this.receipts = receipts;
        this.audit = audit;
        this.events = events;
    }

    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public List<LearningPathResponse> paths(UserAccessContext access, String status, String educationUnitId,
                                            String classId) {
        String tenant = visibility.tenant(access);
        String requestedEducationUnitId = trimToNull(educationUnitId);
        String requestedClassId = trimToNull(classId);
        visibility.ensureScope(access, requestedEducationUnitId, requestedClassId);
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
                .filter(value -> visibility.canSee(access, value))
                .map(this::pathResponseWithNodeCount)
                .toList();
    }

    @Transactional("postgresTransactionManager")
    public LearningPathResponse createPath(UserAccessContext access, CreateLearningPathRequest request,
                                           String idempotencyKey, String correlationId) {
        String tenant = visibility.tenant(access);
        CreateLearningPathRequest safe = Objects.requireNonNull(request, "request");
        String key = key(idempotencyKey);
        String hash = hash("create-path", safe.title(), safe.description(), safe.educationUnitId(), safe.classId());
        LearningOutcomeCommandReceipt replay = receipts.begin(tenant, "CREATE_PATH", key, hash);
        if (replay != null) return pathResponseWithNodeCount(lookup.require(tenant, replay.getAggregateId()));
        visibility.ensureScope(access, safe.educationUnitId(), safe.classId());
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
        receipts.save(tenant, "CREATE_PATH", key, hash, "LEARNING_PATH", saved.getId());
        String eventId = IdGenerator.newId();
        String eventCorrelationId = resolveEventCorrelation(correlationId, eventId);
        events.publishEvent(new LearningPathCreated(eventId, tenant, saved.getId(), access.userId(),
                version(saved), now(), 1, eventCorrelationId, eventCorrelationId));
        Map<String, Object> pathDetails = new LinkedHashMap<>();
        pathDetails.put("status", saved.getStatus());
        if (scope(access) != null) pathDetails.put("scope", scope(access));
        audit.record(access, "LEARNING_PATH_CREATED", "LEARNING_PATH", saved.getId(), key, pathDetails);
        return pathResponseWithNodeCount(saved);
    }

    @Transactional("postgresTransactionManager")
    public LearningPathResponse publishPath(UserAccessContext access, String pathId, long expectedVersion,
                                            String idempotencyKey, String correlationId) {
        String tenant = visibility.tenant(access);
        String id = required(pathId, "pathId", 96);
        String key = key(idempotencyKey);
        String hash = hash("publish-path", id, Long.toString(expectedVersion));
        LearningOutcomeCommandReceipt replay = receipts.begin(tenant, "PUBLISH_PATH", key, hash);
        if (replay != null) return pathResponseWithNodeCount(lookup.require(tenant, replay.getAggregateId()));
        LearningPath path = lookup.require(tenant, id);
        visibility.ensureScope(access, path.getEducationUnitId(), path.getClassId());
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
        receipts.save(tenant, "PUBLISH_PATH", key, hash, "LEARNING_PATH", saved.getId());
        String eventId = IdGenerator.newId();
        String eventCorrelationId = resolveEventCorrelation(correlationId, eventId);
        events.publishEvent(new LearningPathPublished(eventId, tenant, saved.getId(), access.userId(),
                version(saved), now(), 1, eventCorrelationId, eventCorrelationId));
        audit.record(access, "LEARNING_PATH_PUBLISHED", "LEARNING_PATH", saved.getId(), key,
                Map.of("status", saved.getStatus()));
        return pathResponseWithNodeCount(saved);
    }

    @Transactional("postgresTransactionManager")
    public PathNodeResponse addNode(UserAccessContext access, String pathId, AddPathNodeRequest request,
                                    String idempotencyKey, String correlationId) {
        String tenant = visibility.tenant(access);
        AddPathNodeRequest safe = Objects.requireNonNull(request, "request");
        String id = required(pathId, "pathId", 96);
        String key = key(idempotencyKey);
        String hash = hash("add-node", id, safe.contentRefId(), safe.contentKind(), Integer.toString(safe.sortOrder()),
                Boolean.toString(safe.required()), Long.toString(safe.expectedPathVersion()));
        LearningOutcomeCommandReceipt replay = receipts.begin(tenant, "ADD_PATH_NODE", key, hash);
        if (replay != null) return nodeResponse(nodes.findByTenantIdAndId(tenant, replay.getAggregateId())
                .orElseThrow(() -> new ResourceNotFoundException("path node not found")));
        LearningPath path = lookup.require(tenant, id);
        visibility.ensureScope(access, path.getEducationUnitId(), path.getClassId());
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
        receipts.save(tenant, "ADD_PATH_NODE", key, hash, "LEARNING_PATH_NODE", saved.getId());
        audit.record(access, "LEARNING_PATH_NODE_ADDED", "LEARNING_PATH_NODE", saved.getId(), key,
                Map.of("pathId", id, "contentKind", saved.getContentKind()));
        return nodeResponse(saved);
    }

    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public PathDetailResponse path(UserAccessContext access, String pathId) {
        String tenant = visibility.tenant(access);
        LearningPath path = lookup.require(tenant, required(pathId, "pathId", 96));
        visibility.ensureScope(access, path.getEducationUnitId(), path.getClassId());
        List<PathNodeResponse> nodeResponses = nodes.findTop100ByTenantIdAndPathIdOrderBySortOrderAsc(tenant, path.getId())
                .stream().map(OutcomeResponseMapper::nodeResponse).toList();
        return new PathDetailResponse(pathResponse(path, nodeResponses.size()), nodeResponses);
    }

    private LearningPathResponse pathResponseWithNodeCount(LearningPath path) {
        return pathResponse(path, nodes.findTop100ByTenantIdAndPathIdOrderBySortOrderAsc(path.getTenantId(), path.getId()).size());
    }
}
