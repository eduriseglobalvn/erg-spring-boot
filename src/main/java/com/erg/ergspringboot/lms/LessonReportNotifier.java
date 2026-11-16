package com.erg.ergspringboot.lms;

/** Sends a class-log reminder to one teacher. Implemented by notifications (it already depends on lms, not the reverse). */
public interface LessonReportNotifier {

    /** {@code dedupeKey} makes a repeat of the same reminder a no-op in the outbox. */
    void remind(String tenantId, String actorUserId, String dedupeKey, String teacherUserId, String title, String body);
}
