package com.erg.ergspringboot.service.calendar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkCreateRequest;
import com.erg.ergspringboot.calendar.internal.batch.CalendarBatchLifecycleUseCase;
import com.erg.ergspringboot.calendar.internal.bulk.CalendarBulkReferenceDataUseCase;
import com.erg.ergspringboot.calendar.internal.bulk.CalendarBulkPreviewUseCase;
import com.erg.ergspringboot.calendar.internal.bulk.CalendarBulkCreateUseCase;
import com.erg.ergspringboot.calendar.internal.mutation.CalendarEventMutationUseCase;
import com.erg.ergspringboot.calendar.internal.room.CalendarRoomCommandUseCase;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkRowRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventDeleteRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventUpdateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CreateRoomRequest;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBatchPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBulkPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarConflictResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarDeletePayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarEventPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarPreviewPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarRoomOption;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarRowResultResponse;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.model.calendar.CalendarBatch;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.model.calendar.CalendarRoom;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Level;
import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.calendar.CalendarBatchRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
import com.erg.ergspringboot.repository.calendar.CalendarRoomRepository;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.LevelRepository;
import com.erg.ergspringboot.repository.lms.SubjectRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import com.erg.ergspringboot.service.calendar.CalendarConflictService.ResolveResult;
import com.erg.ergspringboot.service.calendar.CalendarExpansionService.ExpansionResult;
import com.erg.ergspringboot.service.calendar.CalendarExpansionService.ReferenceData;
import com.erg.ergspringboot.service.calendar.CalendarValidationService.RowValidationResult;
import com.erg.ergspringboot.utils.IdGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class CalendarCommandService {

    private final CalendarBatchLifecycleUseCase batchLifecycle;
    private final CalendarEventMutationUseCase eventMutations;
    private final CalendarRoomCommandUseCase roomCommands;
    private final CalendarBulkPreviewUseCase bulkPreview;
    private final CalendarBulkCreateUseCase bulkCreate;

    /** Compatibility constructor retained for legacy tests and callers. */
    public CalendarCommandService(
            CalendarBatchRepository batchRepository,
            CalendarEventRepository eventRepository,
            CalendarEventTeacherRepository eventTeacherRepository,
            CalendarRoomRepository roomRepository,
            CalendarExpansionService expansionService,
            CalendarValidationService validationService,
            CalendarBulkInputNormalizer inputNormalizer,
            CalendarConflictService conflictService,
            GraphQlAccessSupport accessSupport,
            ClassroomRepository classroomRepository,
            SubjectRepository subjectRepository,
            LevelRepository levelRepository,
            UserRepository userRepository,
            EducationUnitRepository educationUnitRepository,
            EntityManager entityManager,
            ApplicationEventPublisher eventPublisher) {
        this(new CalendarBatchLifecycleUseCase(
                        batchRepository, eventRepository, conflictService, eventPublisher),
                new CalendarEventMutationUseCase(eventRepository, eventTeacherRepository),
                new CalendarRoomCommandUseCase(roomRepository),
                new CalendarBulkPreviewUseCase(
                        inputNormalizer,
                        new CalendarBulkReferenceDataUseCase(
                                classroomRepository, subjectRepository, levelRepository,
                                userRepository, roomRepository, educationUnitRepository),
                        validationService, expansionService, conflictService),
                new CalendarBulkCreateUseCase(
                        batchRepository, eventRepository, eventTeacherRepository,
                        expansionService, validationService, inputNormalizer, conflictService,
                        accessSupport,
                        new CalendarBulkReferenceDataUseCase(
                                classroomRepository, subjectRepository, levelRepository,
                                userRepository, roomRepository, educationUnitRepository),
                        entityManager));
    }

    @Autowired
    public CalendarCommandService(
            CalendarBatchLifecycleUseCase batchLifecycle,
            CalendarEventMutationUseCase eventMutations,
            CalendarRoomCommandUseCase roomCommands,
            CalendarBulkPreviewUseCase bulkPreview,
            CalendarBulkCreateUseCase bulkCreate) {
        this.batchLifecycle = batchLifecycle;
        this.eventMutations = eventMutations;
        this.roomCommands = roomCommands;
        this.bulkPreview = bulkPreview;
        this.bulkCreate = bulkCreate;
    }

    public CalendarPreviewPayload preview(
            String tenantId, CalendarBulkCreateRequest input, String userId) {
        return bulkPreview.preview(tenantId, input, userId);
    }

    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public CalendarBulkPayload create(
            String tenantId, CalendarBulkCreateRequest input, String userId) {
        return bulkCreate.create(tenantId, input, userId);
    }

    String payloadHash(CalendarBulkCreateRequest input) {
        return bulkCreate.payloadHash(input);
    }

    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public CalendarBatchPayload publishBatch(String tenantId, String batchId, String userId) {
        return batchLifecycle.publish(tenantId, batchId, userId);
    }

    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public CalendarBatchPayload cancelBatch(String tenantId, String batchId, String userId) {
        return batchLifecycle.cancel(tenantId, batchId, userId);
    }

    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public CalendarEventPayload updateEvent(
            String tenantId, CalendarEventUpdateRequest input, String userId) {
        return eventMutations.update(tenantId, input, userId);
    }

    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public CalendarDeletePayload deleteEvent(
            String tenantId, CalendarEventDeleteRequest input, String userId) {
        return eventMutations.delete(tenantId, input, userId);
    }

    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public CalendarDeletePayload deleteTeacherSchedule(
            String tenantId, String teacherUserId, List<String> schoolIds,
            String dateFrom, String dateTo, String cancelledBy) {
        return eventMutations.deleteTeacherSchedule(
                tenantId, teacherUserId, schoolIds, dateFrom, dateTo, cancelledBy);
    }

    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public CalendarRoomOption createRoom(
            String tenantId, String schoolId, String roomName, String createdBy) {
        return roomCommands.create(tenantId, schoolId, roomName, createdBy);
    }

}
