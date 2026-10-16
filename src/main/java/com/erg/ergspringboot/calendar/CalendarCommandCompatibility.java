package com.erg.ergspringboot.calendar;

import com.erg.ergspringboot.calendar.internal.batch.CalendarBatchLifecycleUseCase;
import com.erg.ergspringboot.calendar.internal.bulk.*;
import com.erg.ergspringboot.calendar.internal.command.CalendarCommandUseCase;
import com.erg.ergspringboot.calendar.internal.mutation.CalendarEventMutationUseCase;
import com.erg.ergspringboot.calendar.internal.room.CalendarRoomCommandUseCase;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.calendar.*;
import com.erg.ergspringboot.repository.lms.*;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import com.erg.ergspringboot.service.calendar.*;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;

/** @deprecated Repository construction seam for the legacy calendar adapter. */
@Deprecated
public final class CalendarCommandCompatibility {
    private CalendarCommandCompatibility() { }

    public static CalendarCommandPort fromRepositories(
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
        CalendarBulkReferenceDataUseCase refs = new CalendarBulkReferenceDataUseCase(
                classroomRepository, subjectRepository, levelRepository, userRepository,
                roomRepository, educationUnitRepository);
        return new CalendarCommandUseCase(
                new CalendarBatchLifecycleUseCase(batchRepository, eventRepository, conflictService, eventPublisher),
                new CalendarEventMutationUseCase(eventRepository, eventTeacherRepository),
                new CalendarRoomCommandUseCase(roomRepository),
                new CalendarBulkPreviewUseCase(inputNormalizer, refs, validationService, expansionService, conflictService),
                new CalendarBulkCreateUseCase(batchRepository, eventRepository, eventTeacherRepository,
                        expansionService, validationService, inputNormalizer, conflictService, accessSupport,
                        refs, entityManager));
    }
}
