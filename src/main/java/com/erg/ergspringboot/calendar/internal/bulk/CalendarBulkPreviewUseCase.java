package com.erg.ergspringboot.calendar.internal.bulk;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkCreateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkRowRequest;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarConflictResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarPreviewPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarRowResultResponse;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.service.calendar.CalendarBulkInputNormalizer;
import com.erg.ergspringboot.service.calendar.CalendarConflictService;
import com.erg.ergspringboot.service.calendar.CalendarExpansionService;
import com.erg.ergspringboot.service.calendar.CalendarExpansionService.ExpansionResult;
import com.erg.ergspringboot.service.calendar.CalendarExpansionService.ReferenceData;
import com.erg.ergspringboot.service.calendar.CalendarValidationService;
import com.erg.ergspringboot.service.calendar.CalendarValidationService.RowValidationResult;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the read-only validation and conflict orchestration for calendar previews. */
@Service
public class CalendarBulkPreviewUseCase {

    private final ObjectProvider<CalendarBulkInputNormalizer> normalizers;
    private final ObjectProvider<CalendarBulkReferenceDataUseCase> referenceData;
    private final ObjectProvider<CalendarValidationService> validators;
    private final ObjectProvider<CalendarExpansionService> expansions;
    private final ObjectProvider<CalendarConflictService> conflicts;

    @Autowired
    public CalendarBulkPreviewUseCase(
            ObjectProvider<CalendarBulkInputNormalizer> normalizers,
            ObjectProvider<CalendarBulkReferenceDataUseCase> referenceData,
            ObjectProvider<CalendarValidationService> validators,
            ObjectProvider<CalendarExpansionService> expansions,
            ObjectProvider<CalendarConflictService> conflicts) {
        this.normalizers = normalizers;
        this.referenceData = referenceData;
        this.validators = validators;
        this.expansions = expansions;
        this.conflicts = conflicts;
    }

    /** Compatibility constructor for direct CalendarCommandService tests. */
    public CalendarBulkPreviewUseCase(
            CalendarBulkInputNormalizer normalizer,
            CalendarBulkReferenceDataUseCase referenceData,
            CalendarValidationService validator,
            CalendarExpansionService expansion,
            CalendarConflictService conflict) {
        this.normalizers = provider(normalizer);
        this.referenceData = provider(referenceData);
        this.validators = provider(validator);
        this.expansions = provider(expansion);
        this.conflicts = provider(conflict);
    }

    @Transactional("postgresTransactionManager")
    public CalendarPreviewPayload preview(
            String tenantId, CalendarBulkCreateRequest input, String userId) {
        CalendarBulkInputNormalizer normalizer = require(normalizers, "input normalizer");
        CalendarBulkReferenceDataUseCase references = require(referenceData, "reference data use case");
        CalendarValidationService validator = require(validators, "validation service");
        CalendarExpansionService expansionService = require(expansions, "expansion service");
        CalendarConflictService conflictService = require(conflicts, "conflict service");

        input = normalizer.normalize(input);
        ReferenceData refData = references.load(tenantId, input.schoolId(), input);
        List<RowValidationResult> validationResults = validator.validateRows(input, refData);
        boolean allValid = validationResults.stream().allMatch(RowValidationResult::valid);
        LocalDate applyFrom = LocalDate.parse(input.applyFrom());
        String previewBatchId = "preview_" + IdGenerator.newId();

        int totalPreviewEventCount = 0;
        int totalTeacherScheduleCount = 0;
        int totalPeriodCount = 0;
        List<CalendarRowResultResponse> rowResults = new ArrayList<>();
        List<CalendarEvent> allEvents = new ArrayList<>();
        List<CalendarEventTeacher> allAssignments = new ArrayList<>();
        Map<String, String> clientRowIdByEventId = new HashMap<>();

        for (int i = 0; i < input.rows().size(); i++) {
            CalendarBulkRowRequest row = input.rows().get(i);
            RowValidationResult rowValidation = validationResults.get(i);
            if (!rowValidation.valid()) {
                rowResults.add(new CalendarRowResultResponse(
                        row.clientRowId(), false, 0, 0,
                        rowValidation.errors(), rowValidation.warnings()));
                continue;
            }

            ExpansionResult result = expansionService.expand(
                    row, previewBatchId, tenantId, input.schoolId(), userId,
                    applyFrom, input.repeatWeeks(), refData);
            allEvents.addAll(result.events());
            allAssignments.addAll(result.assignments());
            clientRowIdByEventId.putAll(result.clientRowIdByEventId());
            totalPreviewEventCount += result.events().size();
            totalTeacherScheduleCount += result.assignments().size();
            totalPeriodCount += result.events().size() * row.periodCount();
            rowResults.add(new CalendarRowResultResponse(
                    row.clientRowId(), true, result.events().size(), result.assignments().size(),
                    List.of(), rowValidation.warnings()));
        }

        List<CalendarConflictResponse> detected = Collections.emptyList();
        if (allValid && !allEvents.isEmpty()) {
            detected = conflictService.detectConflicts(
                    tenantId, allEvents, allAssignments, clientRowIdByEventId);
        }
        boolean previewValid = allValid
                && detected.stream().noneMatch(conflict -> "ERROR".equals(conflict.severity()));
        return new CalendarPreviewPayload(
                previewValid, totalPreviewEventCount, totalTeacherScheduleCount,
                totalPeriodCount, rowResults, detected);
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
