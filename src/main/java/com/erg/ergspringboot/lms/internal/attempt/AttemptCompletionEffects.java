package com.erg.ergspringboot.lms.internal.attempt;

import com.erg.ergspringboot.lms.LmsAssignmentAccessPolicy;
import com.erg.ergspringboot.lms.LmsAttemptSubmitted;
import com.erg.ergspringboot.lms.LmsReadCacheInvalidator;
import com.erg.ergspringboot.lms.internal.quiz.runtime.AttemptEventRecorder;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.lms.persistence.Student;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;

/** Side effects of a stored attempt: audit event, progress projection, scoped read-cache eviction, domain event. */
final class AttemptCompletionEffects {

    private final AttemptEventRecorder events;
    private final AttemptProgressProjector progress;
    private final LmsReadCacheInvalidator caches;
    private final LmsAssignmentAccessPolicy policy;
    private final ApplicationEventPublisher publisher;

    AttemptCompletionEffects(AttemptEventRecorder events, AttemptProgressProjector progress,
            LmsReadCacheInvalidator caches, LmsAssignmentAccessPolicy policy, ApplicationEventPublisher publisher) {
        this.events = events;
        this.progress = progress;
        this.caches = caches;
        this.policy = policy;
        this.publisher = publisher;
    }

    void submitted(Attempt saved, Assignment assignment, String tenant, LmsAttemptUseCase.Score score,
            OffsetDateTime submitted, boolean late) {
        events.record(null, saved.getTenantId(), saved.getId(), saved.getQuizId(), saved.getPackageId(), "attempt_submitted",
                null, null, Map.of("score", score.score(), "percent", score.percent(), "passed", score.passed(), "late", late),
                submitted);
        progress.projectAfterCommit(saved, assignment, tenant);
        evictReads(tenant, saved, assignment);
        publisher.publishEvent(new LmsAttemptSubmitted("attempt-submitted:" + saved.getId(), tenant, saved.getId(),
                saved.getAssignmentId(), saved.getStudentId(), saved.getStatus(), score.score(), score.max(), score.passed(),
                submitted));
    }

    void evictReads(String tenant, Attempt attempt, Assignment assignment) {
        caches.evictAttemptAfterCommit(tenant, attempt, assignment,
                policy.learner(tenant, attempt.getStudentId()).map(Student::getAuthUserId).orElse(null));
    }
}
