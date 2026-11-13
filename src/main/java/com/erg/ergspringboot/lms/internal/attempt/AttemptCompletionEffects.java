package com.erg.ergspringboot.lms.internal.attempt;

import com.erg.ergspringboot.lms.LmsAttemptSubmitted;
import com.erg.ergspringboot.lms.LmsReadCacheInvalidator;
import com.erg.ergspringboot.lms.internal.quiz.runtime.AttemptEventRecorder;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;

/** Side effects of a stored attempt: audit event, progress projection, scoped read-cache eviction, domain event. */
final class AttemptCompletionEffects {

    private final AttemptEventRecorder events;
    private final AttemptProgressProjector progress;
    private final LmsReadCacheInvalidator caches;
    private final ApplicationEventPublisher publisher;

    AttemptCompletionEffects(AttemptEventRecorder events, AttemptProgressProjector progress,
            LmsReadCacheInvalidator caches, ApplicationEventPublisher publisher) {
        this.events = events;
        this.progress = progress;
        this.caches = caches;
        this.publisher = publisher;
    }

    void submitted(Attempt saved, Assignment assignment, String tenant, LmsAttemptUseCase.Score score,
            OffsetDateTime submitted, boolean late) {
        events.record(null, saved.getTenantId(), saved.getId(), saved.getQuizId(), saved.getPackageId(), "attempt_submitted",
                null, null, Map.of("score", score.score(), "percent", score.percent(), "passed", score.passed(), "late", late),
                submitted);
        evictReads(tenant, saved, assignment, progress.projectAfterCommit(saved, assignment, tenant));
        publisher.publishEvent(new LmsAttemptSubmitted("attempt-submitted:" + saved.getId(), tenant, saved.getId(),
                saved.getAssignmentId(), saved.getStudentId(), saved.getStatus(), score.score(), score.max(), score.passed(),
                submitted));
    }

    void evictReads(String tenant, Attempt attempt, Assignment assignment) {
        evictReads(tenant, attempt, assignment, progress.studentUserId(attempt, tenant));
    }

    /**
     * Scopes the student dashboard eviction to the learner's login user id. Looking the learner up by login id with
     * the attempt's Student.id found nobody, so every start and submit cleared all dashboards of the tenant.
     */
    void evictReads(String tenant, Attempt attempt, Assignment assignment, String studentUserId) {
        caches.evictAttemptAfterCommit(tenant, attempt, assignment, studentUserId);
    }
}
