package com.erg.ergspringboot.lcms.quiz.authoring;

import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankWorkspaceResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizSummaryPageResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizWorkspaceResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.Quiz;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizRepository;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.MethodAuthorizationEvaluator;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A teacher builds quizzes from the bank for their own classes. Such a quiz belongs to that teacher: other teachers
 * neither see nor change it, while the training office (which may publish system quizzes) sees and may delete it.
 */
@Service
public class TeacherQuizOwnership {
    public static final String PORTAL_TEACHER = "teacher";
    private final MethodAuthorizationEvaluator authorization;
    private final QuizRepository quizzes;

    public TeacherQuizOwnership(MethodAuthorizationEvaluator authorization, QuizRepository quizzes) {
        this.authorization = authorization;
        this.quizzes = quizzes;
    }

    /** True for a signed-in caller who may build quizzes but not publish system ones. */
    public boolean callerIsTeacher() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof ErgPrincipal
                && !authorization.isAllowedAny(authentication, "lms.quiz.publish", "*");
    }

    /** Marks a quiz the calling teacher just created as theirs. */
    @CacheEvict(cacheNames = {"lcmsQuestionBankWorkspace", "lcmsQuizWorkspace"}, allEntries = true)
    @Transactional
    public void claim(String tenantId, String quizId) {
        if (!callerIsTeacher()) return;
        Quiz quiz = find(tenantId, quizId);
        if (callerId().equals(quiz.getOwnerUserId()) && !PORTAL_TEACHER.equals(quiz.getPortal())) {
            quiz.setPortal(PORTAL_TEACHER);
            quizzes.save(quiz);
        }
    }

    /** A teacher may change, publish or delete only a quiz they own. */
    public void requireOwn(String tenantId, String quizId) {
        if (!callerIsTeacher()) return;
        Quiz quiz = find(tenantId, quizId);
        if (!PORTAL_TEACHER.equals(quiz.getPortal()) || !callerId().equals(quiz.getOwnerUserId())) {
            throw new ForbiddenException("Bạn chỉ sửa được bài do chính mình tạo.");
        }
    }

    /** Drops other teachers' quizzes from what a teacher sees; the cached response itself is left untouched. */
    public QuestionBankWorkspaceResponse visibleToCaller(String tenantId, QuestionBankWorkspaceResponse response) {
        if (response == null || !callerIsTeacher()) return response;
        Set<String> hidden = quizzes.findTeacherQuizIdsNotOwnedBy(tenantId, callerId());
        if (hidden.isEmpty()) return response;
        return new QuestionBankWorkspaceResponse(response.tenantId(), response.questions(), response.subjects(),
                response.levels(), response.topics(),
                response.quizzes().stream().filter(quiz -> !hidden.contains(quiz.id())).toList());
    }

    /** The editor list, for the same reason; page totals still count the hidden rows. */
    public QuizWorkspaceResponse visibleToCaller(String tenantId, QuizWorkspaceResponse response) {
        if (response == null || response.quizzes() == null || !callerIsTeacher()) return response;
        Set<String> hidden = quizzes.findTeacherQuizIdsNotOwnedBy(tenantId, callerId());
        if (hidden.isEmpty()) return response;
        QuizSummaryPageResponse page = response.quizzes();
        return new QuizWorkspaceResponse(response.tenantId(),
                new QuizSummaryPageResponse(page.items().stream().filter(quiz -> !hidden.contains(quiz.id())).toList(),
                        page.page(), page.size(), page.totalItems(), page.totalPages(), page.hasNext(), page.hasPrevious()),
                response.subjects(), response.levels(), response.categories());
    }

    /**
     * Takes the quizzes of locked or deleted teacher accounts out of the bank. Work already assigned keeps running:
     * it plays from its published version. Idempotent, so concurrent instances are harmless.
     */
    @Scheduled(fixedDelayString = "${erg.lcms.teacher-quiz-retire-ms:300000}", initialDelayString = "60000")
    @CacheEvict(cacheNames = {"lcmsQuestionBankWorkspace", "lcmsQuizWorkspace"}, allEntries = true)
    @Transactional
    public int retireQuizzesOfLockedOwners() {
        return quizzes.softDeleteTeacherQuizzesOfLockedOwners(java.time.OffsetDateTime.now());
    }

    private Quiz find(String tenantId, String quizId) {
        return quizzes.findByIdAndTenantId(quizId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("quiz not found"));
    }

    private static String callerId() {
        return ((ErgPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).userId();
    }
}
