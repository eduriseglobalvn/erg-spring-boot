package com.erg.ergspringboot.lms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.erg.ergspringboot.lms.internal.attempt.LmsAttemptUseCase;
import com.erg.ergspringboot.model.lms.Attempt;
import com.erg.ergspringboot.platform.cache.CacheInvalidationService;
import com.erg.ergspringboot.platform.sync.LmsSyncOperationService;
import com.erg.ergspringboot.repository.lms.AssignmentRecipientRepository;
import com.erg.ergspringboot.repository.lms.AssignmentResourceRepository;
import com.erg.ergspringboot.service.lms.LmsAssignmentService;
import com.erg.ergspringboot.service.lms.LmsAttemptService;
import com.erg.ergspringboot.service.lms.LmsClassroomService;
import com.erg.ergspringboot.service.lms.LmsQuizService;
import com.erg.ergspringboot.service.lms.LmsStudentService;
import com.erg.ergspringboot.service.quiz.runtime.AttemptEventRecorder;
import com.erg.ergspringboot.service.quiz.runtime.QuizAttemptGradingService;
import com.erg.ergspringboot.service.quiz.runtime.QuizRuntimePackageService;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.springframework.cache.CacheManager;

/** Mock-backed wiring of the attempt use case with the real access policy, for unit/contract tests. */
public final class LmsAttemptFixture {
    public final LmsClassroomService classrooms = mock(LmsClassroomService.class);
    public final LmsAssignmentService assignments = mock(LmsAssignmentService.class);
    public final LmsQuizService quizzes = mock(LmsQuizService.class);
    public final LmsAttemptService attempts = mock(LmsAttemptService.class);
    public final LmsStudentService students = mock(LmsStudentService.class);
    public final QuizRuntimePackageService packages = mock(QuizRuntimePackageService.class);
    public final QuizAttemptGradingService grading = mock(QuizAttemptGradingService.class);
    public final AttemptEventRecorder events = mock(AttemptEventRecorder.class);
    public final CacheManager caches = mock(CacheManager.class);
    public final AssignmentRecipientRepository recipients = mock(AssignmentRecipientRepository.class);
    public final AssignmentResourceRepository resources = mock(AssignmentResourceRepository.class);
    public final LmsCallerPermissions permissions = mock(LmsCallerPermissions.class);
    public final LmsAssignmentAccessPolicy policy = new LmsAssignmentAccessPolicy(
            assignments, classrooms, students, recipients, resources, permissions);

    public LmsAttemptFixture() {
        lenient().when(attempts.save(any(Attempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(attempts.saveAndFlush(any(Attempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(attempts.findInProgress(any(), any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(attempts.countCompletedForLimit(any(), any(), any(), any())).thenReturn(0L);
        lenient().when(students.findFirstByTenantIdAndAuthUserId(any(), any())).thenReturn(Optional.empty());
        lenient().when(students.findByTenantIdAndId(any(), any())).thenReturn(Optional.empty());
        lenient().when(resources.findByTenantIdAndAssignmentIdAndQuizId(any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(recipients.findFirstByTenantIdAndAssignmentIdAndStudentId(any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(grading.runtimeQuestionIds(any())).thenReturn(Set.of());
    }

    public LmsAttemptUseCase useCase(Clock clock) {
        return useCase(clock, Duration.ofSeconds(90), null);
    }

    public LmsAttemptUseCase useCase(Clock clock, Duration grace, LmsSyncOperationService sync) {
        return new LmsAttemptUseCase(policy, quizzes, attempts, students, packages, grading, events,
                new LmsReadCacheInvalidator(caches, (CacheInvalidationService) null), null, sync, event -> { }, clock, grace);
    }
}
