package com.erg.ergspringboot.calendar.internal.bulk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkCreateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkRowRequest;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBulkPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarConflictResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarRowResultResponse;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.model.calendar.CalendarBatch;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.repository.calendar.CalendarBatchRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
import com.erg.ergspringboot.service.calendar.CalendarBulkInputNormalizer;
import com.erg.ergspringboot.service.calendar.CalendarConflictService;
import com.erg.ergspringboot.service.calendar.CalendarConflictService.ResolveResult;
import com.erg.ergspringboot.service.calendar.CalendarExpansionService;
import com.erg.ergspringboot.service.calendar.CalendarExpansionService.ExpansionResult;
import com.erg.ergspringboot.service.calendar.CalendarExpansionService.ReferenceData;
import com.erg.ergspringboot.service.calendar.CalendarValidationService;
import com.erg.ergspringboot.service.calendar.CalendarValidationService.RowValidationResult;
import com.erg.ergspringboot.utils.IdGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the atomic Calendar bulk-create workflow and its idempotency boundary. */
@Service
public class CalendarBulkCreateUseCase {

    private static final Logger log = LoggerFactory.getLogger(CalendarBulkCreateUseCase.class);
    private static final String DRAFT = "DRAFT";
    private static final String CANCELLED = "CANCELLED";
    private static final ObjectMapper CANONICAL_OBJECT_MAPPER = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final ObjectProvider<CalendarBatchRepository> batches;
    private final ObjectProvider<CalendarEventRepository> events;
    private final ObjectProvider<CalendarEventTeacherRepository> eventTeachers;
    private final ObjectProvider<CalendarExpansionService> expansions;
    private final ObjectProvider<CalendarValidationService> validations;
    private final ObjectProvider<CalendarBulkInputNormalizer> normalizers;
    private final ObjectProvider<CalendarConflictService> conflictServices;
    private final ObjectProvider<GraphQlAccessSupport> accessSupports;
    private final ObjectProvider<CalendarBulkReferenceDataUseCase> references;
    private final ObjectProvider<EntityManager> entityManagers;

    @Autowired
    public CalendarBulkCreateUseCase(
            ObjectProvider<CalendarBatchRepository> batches,
            ObjectProvider<CalendarEventRepository> events,
            ObjectProvider<CalendarEventTeacherRepository> eventTeachers,
            ObjectProvider<CalendarExpansionService> expansions,
            ObjectProvider<CalendarValidationService> validations,
            ObjectProvider<CalendarBulkInputNormalizer> normalizers,
            ObjectProvider<CalendarConflictService> conflictServices,
            ObjectProvider<GraphQlAccessSupport> accessSupports,
            ObjectProvider<CalendarBulkReferenceDataUseCase> references,
            ObjectProvider<EntityManager> entityManagers) {
        this.batches = batches;
        this.events = events;
        this.eventTeachers = eventTeachers;
        this.expansions = expansions;
        this.validations = validations;
        this.normalizers = normalizers;
        this.conflictServices = conflictServices;
        this.accessSupports = accessSupports;
        this.references = references;
        this.entityManagers = entityManagers;
    }

    /** Compatibility constructor for direct facade tests. */
    public CalendarBulkCreateUseCase(
            CalendarBatchRepository batches,
            CalendarEventRepository events,
            CalendarEventTeacherRepository eventTeachers,
            CalendarExpansionService expansions,
            CalendarValidationService validations,
            CalendarBulkInputNormalizer normalizers,
            CalendarConflictService conflicts,
            GraphQlAccessSupport accessSupport,
            CalendarBulkReferenceDataUseCase references,
            EntityManager entityManager) {
        this.batches = provider(batches);
        this.events = provider(events);
        this.eventTeachers = provider(eventTeachers);
        this.expansions = provider(expansions);
        this.validations = provider(validations);
        this.normalizers = provider(normalizers);
        this.conflictServices = provider(conflicts);
        this.accessSupports = provider(accessSupport);
        this.references = provider(references);
        this.entityManagers = provider(entityManager);
    }

    @Transactional("postgresTransactionManager")
    public CalendarBulkPayload create(
            String tenantId, CalendarBulkCreateRequest input, String userId) {
        CalendarBatchRepository batchRepository = require(batches, "batch repository");
        CalendarEventRepository eventRepository = require(events, "event repository");
        CalendarEventTeacherRepository teacherRepository = require(eventTeachers, "event-teacher repository");
        CalendarExpansionService expansionService = require(expansions, "expansion service");
        CalendarValidationService validationService = require(validations, "validation service");
        CalendarConflictService conflictService = require(conflictServices, "conflict service");
        CalendarBulkReferenceDataUseCase referenceData = require(references, "reference data use case");

        input = require(normalizers, "input normalizer").normalize(input);
        String payloadHash = payloadHash(input);
        if (input.idempotencyKey() != null && !input.idempotencyKey().isBlank()) {
            Optional<CalendarBatch> existing =
                    batchRepository.findByTenantIdAndIdempotencyKey(tenantId, input.idempotencyKey());
            if (existing.isPresent()) {
                CalendarBatch batch = existing.get();
                if (batch.getPayloadHash() != null && !payloadHash.equals(batch.getPayloadHash())) {
                    throw new ConflictException("Idempotency key was already used with a different payload.");
                }
                return buildBulkPayload(
                        teacherRepository, batch.getId(),
                        eventRepository.findByTenantIdAndBatchId(tenantId, batch.getId()),
                        List.of(), List.of());
            }
        }

        if (!attemptAdvisoryLock(tenantId, input.schoolId())) {
            throw new ConflictException("Another bulk schedule operation is in progress for this school.");
        }

        ReferenceData refData = referenceData.load(tenantId, input.schoolId(), input);
        List<RowValidationResult> validationResults = validationService.validateRows(input, refData);
        if (!validationResults.stream().allMatch(RowValidationResult::valid)) {
            return new CalendarBulkPayload(
                    null, 0, 0, 0,
                    buildRowResults(validationResults, input, refData, expansionService), List.of());
        }

        LocalDate applyFrom = LocalDate.parse(input.applyFrom());
        String batchId = IdGenerator.newId();
        List<CalendarEvent> allEvents = new ArrayList<>();
        List<CalendarEventTeacher> allAssignments = new ArrayList<>();
        Map<String, String> clientRowIdByEventId = new java.util.HashMap<>();
        List<CalendarRowResultResponse> rowResults = new ArrayList<>();
        for (CalendarBulkRowRequest row : input.rows()) {
            ExpansionResult expansion = expansionService.expand(
                    row, batchId, tenantId, input.schoolId(), userId,
                    applyFrom, input.repeatWeeks(), refData);
            allEvents.addAll(expansion.events());
            allAssignments.addAll(expansion.assignments());
            clientRowIdByEventId.putAll(expansion.clientRowIdByEventId());
            rowResults.add(new CalendarRowResultResponse(
                    row.clientRowId(), true, expansion.events().size(), expansion.assignments().size(),
                    List.of(), List.of()));
        }

        List<CalendarConflictResponse> conflicts = conflictService.detectConflicts(
                tenantId, allEvents, allAssignments, clientRowIdByEventId);
        List<CalendarConflictResponse> errorConflicts = conflicts.stream()
                .filter(conflict -> "ERROR".equals(conflict.severity())).toList();
        if (!errorConflicts.isEmpty()) {
            if ("DELETE_CONFLICTING".equals(input.resolveMode())) {
                ResolveResult result = conflictService.resolveConflictsByDeletion(
                        tenantId, errorConflicts, resolveManagedSchoolIds());
                if (!result.eventIdsToDelete().isEmpty()) {
                    eventRepository.updateStatusBatch(
                            tenantId, new ArrayList<>(result.eventIdsToDelete()),
                            CANCELLED, userId, OffsetDateTime.now());
                }
                Set<String> resolvedIds = result.resolvedConflicts().stream()
                        .map(CalendarConflictResponse::existingEventId).collect(Collectors.toSet());
                List<CalendarConflictResponse> remaining = errorConflicts.stream()
                        .filter(conflict -> !resolvedIds.contains(conflict.existingEventId())).toList();
                if (!remaining.isEmpty()) {
                    return new CalendarBulkPayload(null, 0, 0, 0, rowResults, remaining);
                }
            } else {
                return new CalendarBulkPayload(null, 0, 0, 0, rowResults, conflicts);
            }
        }

        boolean hasWarnings = conflicts.stream().anyMatch(conflict -> "WARNING".equals(conflict.severity()));
        if (hasWarnings && !input.overrideConflicts()) {
            return new CalendarBulkPayload(null, 0, 0, 0, rowResults, conflicts);
        }

        CalendarBatch batch = new CalendarBatch();
        batch.setId(batchId);
        batch.setTenantId(tenantId);
        batch.setSchoolId(input.schoolId());
        batch.setAcademicYear(input.academicYear());
        batch.setApplyFrom(applyFrom);
        batch.setRepeatWeeks(input.repeatWeeks());
        batch.setStatus(DRAFT);
        batch.setSource("LCMS_BULK_GRID");
        batch.setIdempotencyKey(input.idempotencyKey());
        batch.setPayloadHash(payloadHash);
        batch.setNote(input.note());
        batch.setCreatedBy(userId);
        batchRepository.save(batch);
        if (!conflicts.isEmpty()) conflictService.persistConflicts(tenantId, batchId, conflicts);
        List<CalendarEvent> savedEvents = eventRepository.saveAll(allEvents);
        teacherRepository.saveAll(allAssignments);
        int totalPeriodCount = allEvents.stream()
                .mapToInt(event -> event.getPeriodCount() != null ? event.getPeriodCount() : 0).sum();
        return new CalendarBulkPayload(
                batchId, savedEvents.size(), allAssignments.size(), totalPeriodCount, rowResults, conflicts);
    }

    public String payloadHash(CalendarBulkCreateRequest input) {
        try {
            String canonicalJson = CANONICAL_OBJECT_MAPPER.writeValueAsString(input);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Cannot canonicalize calendar payload.", error);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable.", error);
        }
    }

    private List<CalendarRowResultResponse> buildRowResults(
            List<RowValidationResult> results,
            CalendarBulkCreateRequest input,
            ReferenceData refData,
            CalendarExpansionService expansionService) {
        List<CalendarRowResultResponse> rows = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            RowValidationResult validation = results.get(i);
            if (validation.valid()) {
                CalendarBulkRowRequest row = input.rows().get(i);
                ExpansionResult expansion = expansionService.expand(
                        row, "tmp", input.tenantId(), input.schoolId(), "system",
                        LocalDate.parse(input.applyFrom()), input.repeatWeeks(), refData);
                rows.add(new CalendarRowResultResponse(
                        validation.clientRowId(), true, expansion.events().size(),
                        expansion.assignments().size(), List.of(), validation.warnings()));
            } else {
                rows.add(new CalendarRowResultResponse(
                        validation.clientRowId(), false, 0, 0,
                        validation.errors(), validation.warnings()));
            }
        }
        return rows;
    }

    private CalendarBulkPayload buildBulkPayload(
            CalendarEventTeacherRepository teachers,
            String batchId,
            List<CalendarEvent> existingEvents,
            List<CalendarRowResultResponse> rowResults,
            List<CalendarConflictResponse> conflicts) {
        int periods = existingEvents.stream()
                .mapToInt(event -> event.getPeriodCount() != null ? event.getPeriodCount() : 0).sum();
        List<String> eventIds = existingEvents.stream().map(CalendarEvent::getId).toList();
        List<CalendarEventTeacher> assignments = eventIds.isEmpty() ? List.of()
                : teachers.findByTenantIdAndEventIdIn(existingEvents.get(0).getTenantId(), eventIds);
        return new CalendarBulkPayload(
                batchId, existingEvents.size(), assignments.size(), periods, rowResults, conflicts);
    }

    private boolean attemptAdvisoryLock(String tenantId, String schoolId) {
        try {
            long lockId = (long) (tenantId.hashCode() * 31L + schoolId.hashCode());
            Query query = require(entityManagers, "entity manager")
                    .createNativeQuery("SELECT pg_try_advisory_xact_lock(:lockId)");
            query.setParameter("lockId", lockId);
            return Boolean.TRUE.equals(query.getSingleResult());
        } catch (Exception error) {
            log.warn("Failed to acquire advisory lock for tenant={} school={}: {}",
                    tenantId, schoolId, error.getMessage());
            return false;
        }
    }

    private List<String> resolveManagedSchoolIds() {
        GraphQlAccessSupport support = require(accessSupports, "access support");
        var principal = support.currentPrincipal();
        if (principal == null) return List.of();
        return support.csv(support.attribute(principal, "managedEducationUnitIds"));
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override public T getObject(Object... args) { return value; }
            @Override public T getIfAvailable() { return value; }
            @Override public T getIfUnique() { return value; }
            @Override public T getObject() { return value; }
        };
    }

    private static <T> T require(ObjectProvider<T> provider, String label) {
        T value = provider == null ? null : provider.getIfAvailable();
        if (value == null) throw new IllegalStateException("Calendar " + label + " is not available");
        return value;
    }
}
