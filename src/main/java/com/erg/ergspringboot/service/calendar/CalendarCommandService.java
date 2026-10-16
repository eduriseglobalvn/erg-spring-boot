package com.erg.ergspringboot.service.calendar;

import com.erg.ergspringboot.calendar.CalendarCommandCompatibility;
import com.erg.ergspringboot.calendar.CalendarCommandPort;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.*;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.*;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.calendar.*;
import com.erg.ergspringboot.repository.lms.*;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * @deprecated Use {@link com.erg.ergspringboot.calendar.CalendarOperations};
 *             this bean is a compatibility adapter for old callers/tests.
 */
@Deprecated
@Service
public class CalendarCommandService implements CalendarCommandPort {
    private final CalendarCommandPort operations;

    @Autowired
    public CalendarCommandService(CalendarCommandPort operations) {
        this.operations = operations;
    }

    /** Compatibility constructor retained for legacy tests and callers. */
    @Deprecated
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
        this.operations = CalendarCommandCompatibility.fromRepositories(batchRepository, eventRepository,
                eventTeacherRepository, roomRepository, expansionService, validationService, inputNormalizer,
                conflictService, accessSupport, classroomRepository, subjectRepository, levelRepository,
                userRepository, educationUnitRepository, entityManager, eventPublisher);
    }

    @Override public CalendarPreviewPayload preview(String tenantId, CalendarBulkCreateRequest input, String userId) { return operations.preview(tenantId, input, userId); }
    @Override public String payloadHash(CalendarBulkCreateRequest input) { return operations.payloadHash(input); }
    @Override @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true) public CalendarBulkPayload create(String tenantId, CalendarBulkCreateRequest input, String userId) { return operations.create(tenantId, input, userId); }
    @Override @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true) public CalendarBatchPayload publishBatch(String tenantId, String batchId, String userId) { return operations.publishBatch(tenantId, batchId, userId); }
    @Override @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true) public CalendarBatchPayload cancelBatch(String tenantId, String batchId, String userId) { return operations.cancelBatch(tenantId, batchId, userId); }
    @Override @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true) public CalendarEventPayload updateEvent(String tenantId, CalendarEventUpdateRequest input, String userId) { return operations.updateEvent(tenantId, input, userId); }
    @Override @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true) public CalendarDeletePayload deleteEvent(String tenantId, CalendarEventDeleteRequest input, String userId) { return operations.deleteEvent(tenantId, input, userId); }
    @Override @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true) public CalendarDeletePayload deleteTeacherSchedule(String tenantId, String teacherUserId, List<String> schoolIds, String dateFrom, String dateTo, String cancelledBy) { return operations.deleteTeacherSchedule(tenantId, teacherUserId, schoolIds, dateFrom, dateTo, cancelledBy); }
    @Override @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true) public CalendarRoomOption createRoom(String tenantId, String schoolId, String roomName, String createdBy) { return operations.createRoom(tenantId, schoolId, roomName, createdBy); }
}
