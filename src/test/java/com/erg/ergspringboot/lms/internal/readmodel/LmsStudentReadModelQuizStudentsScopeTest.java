package com.erg.ergspringboot.lms.internal.readmodel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lcms.persistence.quiz.bank.Quiz;
import com.erg.ergspringboot.lms.LmsAssignmentAccessPolicy;
import com.erg.ergspringboot.lms.internal.core.LmsAssignmentService;
import com.erg.ergspringboot.lms.internal.core.LmsAttemptService;
import com.erg.ergspringboot.lms.internal.core.LmsClassroomService;
import com.erg.ergspringboot.lms.internal.core.LmsQuizService;
import com.erg.ergspringboot.lms.internal.core.LmsStudentService;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/** A timetable-only teacher (class scope, no education unit) sees a named class on /students; /students/page waits on the owner. */
class LmsStudentReadModelQuizStudentsScopeTest {
    private final LmsAttemptService attempts = mock(LmsAttemptService.class);
    private final LmsQuizService quizzes = mock(LmsQuizService.class);
    private LmsStudentReadModelUseCase useCase;
    private final UserAccessContext classOnly = new UserAccessContext("t1", "erg", List.of(), List.of("class-1"), null, null);

    @BeforeEach
    void setUp() {
        useCase = new LmsStudentReadModelUseCase(mock(LmsClassroomService.class), mock(LmsAssignmentService.class), attempts,
                mock(LmsStudentService.class), quizzes, mock(LmsAssignmentAccessPolicy.class));
        when(quizzes.findByTenantIdAndId("erg", "quiz-1")).thenReturn(Optional.of(new Quiz()));
        Page<com.erg.ergspringboot.lms.persistence.projection.AttemptSummaryProjection> empty = new PageImpl<>(List.of());
        when(attempts.findScopedQuizAttemptSummaries(anyString(), anyString(), anyBoolean(), any(), anyBoolean(), any(),
                anyBoolean(), any(Pageable.class))).thenReturn(empty);
    }

    @Test
    void nonPagedListAcceptsAClassNamedInTheScopeWithoutASchool() {
        useCase.quizStudents("quiz-1", classOnly);

        verify(attempts).findScopedQuizAttemptSummaries(eq("erg"), eq("quiz-1"), eq(false), eq(List.of("class-1")), eq(false),
                eq(List.of("__no-scope__")), eq(true), any(Pageable.class));
    }

    @Test
    void pagedListStillRequiresTheSchoolMatchUntilTheOwnerDecides() {
        useCase.quizStudentsPage("quiz-1", classOnly, 0, 20);

        verify(attempts).findScopedQuizAttemptSummaries(eq("erg"), eq("quiz-1"), eq(false), eq(List.of("class-1")), eq(false),
                eq(List.of("__no-scope__")), eq(LmsStudentReadModelUseCase.CLASS_SCOPE_WITHOUT_SCHOOL_ON_PAGE), any(Pageable.class));
    }
}
