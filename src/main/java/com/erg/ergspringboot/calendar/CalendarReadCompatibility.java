package com.erg.ergspringboot.calendar;

import com.erg.ergspringboot.calendar.internal.readmodel.CalendarReadUseCase;
import com.erg.ergspringboot.platform.concurrency.ErgReadTaskExecutor;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.calendar.CalendarBatchRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
import com.erg.ergspringboot.repository.calendar.CalendarRoomRepository;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.LevelRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.lms.SubjectRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;

/** @deprecated Repository construction seam for the legacy CalendarReadService adapter. */
@Deprecated
public final class CalendarReadCompatibility {
    private CalendarReadCompatibility() { }

    public static CalendarReadUseCase fromRepositories(
            CalendarEventRepository eventRepository,
            CalendarEventTeacherRepository eventTeacherRepository,
            ClassroomRepository classroomRepository,
            SubjectRepository subjectRepository,
            LevelRepository levelRepository,
            EducationUnitRepository educationUnitRepository,
            CalendarRoomRepository roomRepository,
            UserRepository userRepository,
            StudentRepository studentRepository,
            CalendarBatchRepository batchRepository,
            ErgReadTaskExecutor readTaskExecutor) {
        return new CalendarReadUseCase(
                eventRepository, eventTeacherRepository, classroomRepository, subjectRepository,
                levelRepository, educationUnitRepository, roomRepository, userRepository,
                studentRepository, batchRepository, readTaskExecutor);
    }
}
