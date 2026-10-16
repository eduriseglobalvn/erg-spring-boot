package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.lms.*;
import com.erg.ergspringboot.service.quiz.runtime.*;
import com.erg.ergspringboot.lms.internal.attempt.LmsAttemptUseCase;
import com.erg.ergspringboot.lms.internal.classroom.LmsClassroomQueryUseCase;
import com.erg.ergspringboot.lms.internal.readmodel.LmsStudentReadModelUseCase;
import com.erg.ergspringboot.lms.internal.resource.LmsResourceQueryUseCase;
import java.util.List;
import org.springframework.cache.CacheManager;

/** @deprecated Repository/service construction seam for the legacy runtime adapter. */
@Deprecated
public final class LmsRuntimeCompatibility {
    private LmsRuntimeCompatibility() { }

    public static LmsRuntimePort fromLegacyServices(
            LmsClassroomService classroomService,
            LmsAssignmentService assignmentService,
            LmsQuizService quizService,
            LmsAttemptService attemptService,
            LmsStudentService studentService,
            QuizRuntimePackageService runtimePackageService,
            QuizAttemptGradingService attemptGradingService,
            AttemptEventRecorder attemptEventRecorder,
            CacheManager cacheManager) {
        return new com.erg.ergspringboot.lms.internal.runtime.LmsRuntimeUseCase(
                new LmsAttemptUseCase(classroomService, assignmentService, quizService, attemptService,
                        studentService, runtimePackageService, attemptGradingService, attemptEventRecorder, cacheManager),
                new LmsClassroomQueryUseCase(classroomService),
                new LmsResourceQueryUseCase(quizService, runtimePackageService),
                new LmsStudentReadModelUseCase(classroomService, assignmentService, attemptService,
                        studentService, quizService));
    }
}
