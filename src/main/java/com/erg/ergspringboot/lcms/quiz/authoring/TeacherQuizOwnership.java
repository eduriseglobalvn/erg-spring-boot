package com.erg.ergspringboot.lcms.quiz.authoring;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.Quiz;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizRepository;
import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.MethodAuthorizationEvaluator;
import com.erg.ergspringboot.platform.security.TenantContext;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    static final String RETIRE_JOB_NAME = "teacher-quiz-retire";
    private static final List<String> QUIZ_CACHES = List.of("lcmsQuestionBankWorkspace", "lcmsQuizWorkspace");
    private final MethodAuthorizationEvaluator authorization;
    private final QuizRepository quizzes;
    private final ClusterJobLock jobLock;
    private final TransactionTemplate transaction;
    private final CacheManager caches;

    @Autowired
    public TeacherQuizOwnership(MethodAuthorizationEvaluator authorization, QuizRepository quizzes,
            ObjectProvider<ClusterJobLock> jobLock, PlatformTransactionManager transactionManager, CacheManager caches) {
        this(authorization, quizzes, jobLock.getIfAvailable(), new TransactionTemplate(transactionManager), caches);
    }

    TeacherQuizOwnership(MethodAuthorizationEvaluator authorization, QuizRepository quizzes) {
        this(authorization, quizzes, (ClusterJobLock) null, (TransactionTemplate) null, null);
    }

    TeacherQuizOwnership(MethodAuthorizationEvaluator authorization, QuizRepository quizzes, ClusterJobLock jobLock,
            TransactionTemplate transaction, CacheManager caches) {
        this.authorization = authorization;
        this.quizzes = quizzes;
        this.jobLock = jobLock;
        this.transaction = transaction;
        this.caches = caches;
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

    /** A teacher reads (exports) a system quiz or their own, never another teacher's; reported as missing, like a copy source. */
    public void requireVisible(String tenantId, String quizId) {
        if (!callerIsTeacher()) return;
        Quiz quiz = find(tenantId, quizId);
        if (PORTAL_TEACHER.equals(quiz.getPortal()) && !callerId().equals(quiz.getOwnerUserId())) {
            throw new ResourceNotFoundException("quiz not found");
        }
    }

    /**
     * The caller's id when quiz lists must hide other teachers' quizzes (a teacher), else null. Lists apply it in SQL and
     * carry it in their cache key, so paging and totals only ever count what the teacher may see.
     */
    public String teacherViewerId() {
        return callerIsTeacher() ? callerId() : null;
    }

    /**
     * Takes the quizzes of locked or deleted teacher accounts out of the bank. Work already assigned keeps running:
     * it plays from its published version. Idempotent, so concurrent instances are harmless.
     */
    @Scheduled(fixedDelayString = "${erg.lcms.teacher-quiz-retire-ms:300000}", initialDelayString = "60000")
    public void retireScheduled() {
        if (jobLock == null) retireQuizzesOfLockedOwners(); else jobLock.runExclusively(RETIRE_JOB_NAME, this::retireQuizzesOfLockedOwners);
    }

    /** Returns the retired quiz count; the caches are cleared only when something actually changed. */
    int retireQuizzesOfLockedOwners() {
        // quizzes are tenant-protected (RLS) and a scheduled job has no tenant.
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
            int retired = transaction == null ? quizzes.softDeleteTeacherQuizzesOfLockedOwners(java.time.OffsetDateTime.now())
                    : transaction.execute(status -> quizzes.softDeleteTeacherQuizzesOfLockedOwners(java.time.OffsetDateTime.now()));
            if (retired > 0 && caches != null) {
                for (String name : QUIZ_CACHES) {
                    Cache cache = caches.getCache(name);
                    if (cache != null) cache.clear();
                }
            }
            return retired;
        }
    }

    private Quiz find(String tenantId, String quizId) {
        return quizzes.findByIdAndTenantId(quizId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("quiz not found"));
    }

    private static String callerId() {
        return ((ErgPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).userId();
    }
}
