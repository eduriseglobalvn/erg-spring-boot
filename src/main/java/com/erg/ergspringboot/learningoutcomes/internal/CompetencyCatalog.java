package com.erg.ergspringboot.learningoutcomes.internal;

import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeResponseMapper.competencyResponse;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.hash;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.key;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.required;
import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.trimToNull;

import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.CreateCompetencyRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.CompetencyResponse;
import com.erg.ergspringboot.learningoutcomes.persistence.Competency;
import com.erg.ergspringboot.learningoutcomes.persistence.LearningOutcomeCommandReceipt;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.learningoutcomes.persistence.CompetencyRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant competency catalog: creation with unique codes and scoped reads. */
@Service
public class CompetencyCatalog {
    private static final String ACTIVE = "ACTIVE";

    private final CompetencyRepository competencies;
    private final OutcomeVisibilityPolicy visibility;
    private final OutcomeReceiptStore receipts;
    private final OutcomeAuditTrail audit;

    public CompetencyCatalog(
            CompetencyRepository competencies,
            OutcomeVisibilityPolicy visibility,
            OutcomeReceiptStore receipts,
            OutcomeAuditTrail audit) {
        this.competencies = competencies;
        this.visibility = visibility;
        this.receipts = receipts;
        this.audit = audit;
    }

    @Transactional("postgresTransactionManager")
    public CompetencyResponse createCompetency(UserAccessContext access, CreateCompetencyRequest request,
                                               String idempotencyKey, String correlationId) {
        String tenant = visibility.tenant(access);
        CreateCompetencyRequest safe = Objects.requireNonNull(request, "request");
        String key = key(idempotencyKey);
        String hash = hash("create-competency", safe.code(), safe.name(), safe.description(), safe.educationUnitId());
        LearningOutcomeCommandReceipt replay = receipts.begin(tenant, "CREATE_COMPETENCY", key, hash);
        if (replay != null) return competencyResponse(require(tenant, replay.getAggregateId()));
        visibility.ensureScope(access, safe.educationUnitId(), null);
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
        receipts.save(tenant, "CREATE_COMPETENCY", key, hash, "COMPETENCY", saved.getId());
        audit.record(access, "COMPETENCY_CREATED", "COMPETENCY", saved.getId(), key,
                Map.of("code", saved.getCode()));
        return competencyResponse(saved);
    }

    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public CompetencyResponse competency(UserAccessContext access, String competencyId) {
        Competency competency = require(visibility.tenant(access), required(competencyId, "competencyId", 96));
        visibility.ensureScope(access, competency.getEducationUnitId(), null);
        return competencyResponse(competency);
    }

    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public List<CompetencyResponse> competencies(UserAccessContext access, String educationUnitId) {
        String tenant = visibility.tenant(access);
        String requestedEducationUnitId = trimToNull(educationUnitId);
        visibility.ensureScope(access, requestedEducationUnitId, null);
        List<Competency> candidates = requestedEducationUnitId == null
                ? competencies.findTop100ByTenantIdOrderByUpdatedAtDesc(tenant)
                : competencies.findTop100ByTenantIdAndEducationUnitIdOrderByUpdatedAtDesc(
                        tenant, requestedEducationUnitId);
        return candidates.stream()
                .filter(value -> access.canAccessEducationUnit(trimToNull(value.getEducationUnitId())))
                .map(OutcomeResponseMapper::competencyResponse)
                .toList();
    }

    private Competency require(String tenant, String id) {
        return competencies.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("competency not found"));
    }
}
